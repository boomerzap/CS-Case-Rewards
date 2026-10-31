package com.boomerzap.CSCase.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.boomerzap.CSCase.CaseDefinition;
import com.boomerzap.CSCase.CaseEntry;
import com.boomerzap.CSCase.CaseRarity;
import com.boomerzap.CSCase.item.CaseItem;
import com.boomerzap.CSCase.network.OpenCasePayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Counter-Strike style case screen. Three phases:
 * PREVIEW (contents grid + Open button), SPINNING (roulette strip decelerating onto the reward),
 * DONE (reward reveal in the rarity colour).
 */
public class CaseScreen extends Screen {
    private enum State { PREVIEW, SPINNING, DONE }

    // Palette
    private static final int PANEL_BG = 0xF0141720;
    private static final int PANEL_BORDER = 0xFF2C3240;
    private static final int CARD_BG = 0xFF1E222C;
    private static final int MARKER = 0xFFFFD34D;
    private static final int FADE_RGB = 0x141720;

    // Preview grid
    private static final int GRID_W = 56, GRID_H = 64, GRID_GAP = 6;
    // Spin strip
    private static final int STRIP_W = 64, STRIP_H = 72, STRIP_PITCH = 68;
    private static final int STRIP_CARDS = 64;
    // Visual-only tuning: these never affect what the server actually rolls.
    private static final float COVERT_STRIP_BOOST = 3.0f;      // filler odds multiplier
    private static final float GOLD_STRIP_BOOST = 6.0f;
    private static final float NEAR_MISS_CHANCE = 0.4f;        // chance a rare card sits right next to the winner
    private static final int HEADER_H = 30, FOOTER_H = 34;

    private final ResourceLocation caseId;
    @Nullable private final CaseDefinition def;
    @Nullable private final InteractionHand hand;
    private final List<Integer> gridOrder = new ArrayList<>();

    private State state = State.PREVIEW;

    // Layout
    private int panelX, panelY, panelW, panelH;
    private int bodyTop, bodyBottom;
    private Button actionButton;   // Open / Open again
    private Button closeButton;    // Close / Skip

    // Preview
    private int scrollRows;
    private boolean waiting;
    private int waitTicks;

    // Spin
    private final List<ItemStack> stripStacks = new ArrayList<>();
    private final List<CaseRarity> stripRarities = new ArrayList<>();
    private int winnerIndex;
    private float targetScroll;
    private int spinDuration = 1;
    private int spinTicks;
    private int doneTicks;
    private int lastTickCard = -1;
    private ItemStack wonStack = ItemStack.EMPTY;
    private CaseRarity wonRarity = CaseRarity.CONSUMER;

    public CaseScreen(ResourceLocation caseId, @Nullable CaseDefinition def, @Nullable InteractionHand hand) {
        super(def != null ? def.displayName(caseId) : Component.literal(caseId.toString()));
        this.caseId = caseId;
        this.def = def;
        this.hand = hand;
        if (def != null) {
            for (int n = 0; n < def.entries().size(); n++) gridOrder.add(n);
            // Rarest first, then by chance.
            gridOrder.sort(Comparator
                    .<Integer>comparingInt(n -> -def.entries().get(n).rarity().ordinal())
                    .thenComparingDouble(def::chanceOf));
        }
    }

    public ResourceLocation caseId() {
        return caseId;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        panelW = Math.min(width - 20, 440);
        panelH = Mth.clamp(height - 20, 200, 270);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        bodyTop = panelY + HEADER_H;
        bodyBottom = panelY + panelH - FOOTER_H;

        int by = panelY + panelH - 26;
        actionButton = addRenderableWidget(Button.builder(Component.translatable("cscase.gui.open"), b -> requestOpen())
                .bounds(panelX + panelW / 2 - 104, by, 100, 20).build());
        closeButton = addRenderableWidget(Button.builder(Component.translatable("cscase.gui.close"), b -> onCloseButton())
                .bounds(panelX + panelW / 2 + 4, by, 100, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        if (actionButton == null) return;
        switch (state) {
            case PREVIEW -> {
                actionButton.visible = hand != null;
                actionButton.active = !waiting;
                actionButton.setMessage(Component.translatable("cscase.gui.open"));
                closeButton.setMessage(Component.translatable("cscase.gui.close"));
            }
            case SPINNING -> {
                actionButton.visible = false;
                closeButton.setMessage(Component.translatable("cscase.gui.skip"));
            }
            case DONE -> {
                actionButton.visible = canOpenAgain();
                actionButton.active = !waiting;
                actionButton.setMessage(Component.translatable("cscase.gui.open_again"));
                closeButton.setMessage(Component.translatable("cscase.gui.close"));
            }
        }
        // Centre the lone close button when there is no action button.
        int by = panelY + panelH - 26;
        closeButton.setX(actionButton.visible ? panelX + panelW / 2 + 4 : panelX + panelW / 2 - 50);
        closeButton.setY(by);
    }

    private boolean canOpenAgain() {
        if (hand == null || minecraft == null || minecraft.player == null) return false;
        ItemStack held = minecraft.player.getItemInHand(hand);
        return held.getItem() instanceof CaseItem && caseId.equals(CaseItem.getCaseId(held));
    }

    private void requestOpen() {
        if (hand == null || waiting) return;
        waiting = true;
        waitTicks = 0;
        PacketDistributor.sendToServer(new OpenCasePayload(hand));
        updateButtons();
    }

    private void onCloseButton() {
        if (state == State.SPINNING) {
            finishSpin();
        } else {
            onClose();
        }
    }

    // ------------------------------------------------------------------ animation

    /** Called when the server has rolled a result. Builds the strip and starts spinning. */
    public void startSpin(ItemStack won, CaseRarity rarity, int ticks) {
        RandomSource random = RandomSource.create();
        stripStacks.clear();
        stripRarities.clear();
        winnerIndex = 50 + random.nextInt(8);
        for (int n = 0; n < STRIP_CARDS; n++) {
            if (n == winnerIndex || def == null) {
                stripStacks.add(won.copy());
                stripRarities.add(rarity);
            } else {
                CaseEntry e = def.entries().get(rollStripIndex(random, false));
                stripStacks.add(e.stack().copy());
                stripRarities.add(e.rarity());
            }
        }

        // Land somewhere inside the winning card, not always dead centre.
        float offset = (random.nextFloat() - 0.5f) * STRIP_W * 0.7f;

        // Near miss: if the winner is not rare, sometimes park a covert/gold card right beside it
        // and stop close to that edge, so it looks like the roulette almost landed on it.
        if (def != null && rarity.compareTo(CaseRarity.COVERT) < 0 && random.nextFloat() < NEAR_MISS_CHANCE
                && hasRareEntries()) {
            int side = random.nextBoolean() ? 1 : -1;
            CaseEntry tease = def.entries().get(rollStripIndex(random, true));
            stripStacks.set(winnerIndex + side, tease.stack().copy());
            stripRarities.set(winnerIndex + side, tease.rarity());
            offset = side * STRIP_W * (0.36f + random.nextFloat() * 0.10f); // stays inside the winning card
        }
        targetScroll = winnerIndex * STRIP_PITCH + offset;
        wonStack = won.copy();
        wonRarity = rarity;
        spinDuration = Math.max(20, ticks - 10); // finish a touch before the item is handed over
        spinTicks = 0;
        doneTicks = 0;
        lastTickCard = 0;
        waiting = false;
        state = State.SPINNING;
        updateButtons();
    }

    private boolean hasRareEntries() {
        if (def == null) return false;
        for (CaseEntry e : def.entries()) {
            if (e.rarity().compareTo(CaseRarity.COVERT) >= 0) return true;
        }
        return false;
    }

    /**
     * Picks an entry index for strip filler using the real weights, with covert and gold boosted
     * so rare cards flash past more often than they actually drop. {@code rareOnly} restricts the pick
     * to covert and above (used for near-miss teasers).
     */
    private int rollStripIndex(RandomSource random, boolean rareOnly) {
        List<CaseEntry> entries = def.entries();
        double[] w = new double[entries.size()];
        double total = 0;
        for (int n = 0; n < w.length; n++) {
            CaseRarity r = entries.get(n).rarity();
            double weight = def.weightOf(n);
            if (r == CaseRarity.EXCEEDINGLY_RARE) weight *= GOLD_STRIP_BOOST;
            else if (r == CaseRarity.COVERT) weight *= COVERT_STRIP_BOOST;
            if (rareOnly && r.compareTo(CaseRarity.COVERT) < 0) weight = 0;
            w[n] = weight;
            total += weight;
        }
        if (total <= 0) return random.nextInt(entries.size());
        double roll = random.nextDouble() * total;
        for (int n = 0; n < w.length; n++) {
            roll -= w[n];
            if (roll < 0 && w[n] > 0) return n;
        }
        return w.length - 1;
    }

    private void finishSpin() {
        spinTicks = spinDuration;
        state = State.DONE;
        doneTicks = 0;
        waiting = false;
        playReveal();
        updateButtons();
    }

    private float scrollAt(float time) {
        float p = Mth.clamp(time / spinDuration, 0f, 1f);
        float eased = 1f - (float) Math.pow(1f - p, 3);
        return targetScroll * eased;
    }

    @Override
    public void tick() {
        super.tick();
        switch (state) {
            case PREVIEW -> {
                if (waiting && ++waitTicks > 60) { // server never answered (e.g. already opening)
                    waiting = false;
                    updateButtons();
                }
            }
            case SPINNING -> {
                if (++spinTicks >= spinDuration) finishSpin();
            }
            case DONE -> {
                doneTicks++;
                if (doneTicks % 10 == 0) updateButtons(); // the held case may have changed
            }
        }
    }

    // ------------------------------------------------------------------ sound

    private void playTick(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    private void playReveal() {
        if (minecraft == null) return;
        if (wonRarity.compareTo(CaseRarity.CLASSIFIED) >= 0) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f));
        } else {
            float pitch = 0.8f + wonRarity.ordinal() * 0.1f;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch));
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        drawPanel(g);

        List<Component> tooltip = null;
        switch (state) {
            case PREVIEW -> tooltip = renderPreview(g, mouseX, mouseY);
            case SPINNING, DONE -> renderSpin(g, partialTick);
        }

        actionButton.render(g, mouseX, mouseY, partialTick);
        closeButton.render(g, mouseX, mouseY, partialTick);
        if (tooltip != null) {
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private void drawPanel(GuiGraphics g) {
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, PANEL_BORDER);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);

        // Rarity ribbon along the top edge.
        CaseRarity[] all = CaseRarity.values();
        int seg = panelW / all.length;
        for (int n = 0; n < all.length; n++) {
            int x0 = panelX + n * seg;
            int x1 = n == all.length - 1 ? panelX + panelW : x0 + seg;
            g.fill(x0, panelY, x1, panelY + 2, all[n].argb());
        }

        g.drawCenteredString(font, title, panelX + panelW / 2, panelY + 11, 0xFFFFFFFF);
    }

    // -- preview

    private int gridCols() {
        return Math.max(1, (panelW - 24 + GRID_GAP) / (GRID_W + GRID_GAP));
    }

    private int gridRowsTotal() {
        return (gridOrder.size() + gridCols() - 1) / gridCols();
    }

    private int gridRowsVisible() {
        return Math.max(1, (bodyBottom - bodyTop + GRID_GAP) / (GRID_H + GRID_GAP));
    }

    private int maxScrollRows() {
        return Math.max(0, gridRowsTotal() - gridRowsVisible());
    }

    @Nullable
    private List<Component> renderPreview(GuiGraphics g, int mouseX, int mouseY) {
        if (def == null) {
            g.drawCenteredString(font, Component.translatable("cscase.gui.no_data"), panelX + panelW / 2, bodyTop + 20, 0xFFFF6B6B);
            return null;
        }
        int cols = gridCols();
        int gridWidth = cols * GRID_W + (cols - 1) * GRID_GAP;
        int left = panelX + (panelW - gridWidth) / 2;
        scrollRows = Mth.clamp(scrollRows, 0, maxScrollRows());

        List<Component> tooltip = null;
        g.enableScissor(panelX, bodyTop, panelX + panelW, bodyBottom);
        for (int slot = 0; slot < gridOrder.size(); slot++) {
            int row = slot / cols - scrollRows;
            if (row < 0) continue;
            int x = left + (slot % cols) * (GRID_W + GRID_GAP);
            int y = bodyTop + 2 + row * (GRID_H + GRID_GAP);
            if (y > bodyBottom) break;

            int entryIndex = gridOrder.get(slot);
            CaseEntry entry = def.entries().get(entryIndex);
            boolean hovered = mouseX >= x && mouseX < x + GRID_W && mouseY >= y && mouseY < y + GRID_H
                    && mouseY >= bodyTop && mouseY < bodyBottom;
            drawCard(g, x, y, GRID_W, GRID_H, entry.stack(), entry.rarity(), 2f, hovered ? 0xFFFFFFFF : 0);

            // chance label
            double chance = def.chanceOf(entryIndex) * 100.0;
            String label = chance >= 10 ? String.format("%.1f%%", chance)
                    : chance >= 1 ? String.format("%.2f%%", chance) : String.format("%.3f%%", chance);
            g.drawString(font, label, x + (GRID_W - font.width(label)) / 2, y + GRID_H - 14, 0xFFC8CCD6, true);

            if (hovered) {
                List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, entry.stack()));
                lines.add(Component.empty());
                lines.add(entry.rarity().displayName().copy().withStyle(s -> s.withColor(entry.rarity().rgb())));
                lines.add(Component.translatable("cscase.gui.chance", String.format("%.3f", chance)));
                tooltip = lines;
            }
        }
        g.disableScissor();

        // scroll hint
        if (maxScrollRows() > 0) {
            Component hint = Component.translatable("cscase.gui.scroll");
            g.drawCenteredString(font, hint, panelX + panelW / 2, bodyBottom + 2, 0xFF7F8594);
        }
        return tooltip;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (state == State.PREVIEW && maxScrollRows() > 0) {
            scrollRows = Mth.clamp(scrollRows - (int) Math.signum(scrollY), 0, maxScrollRows());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // -- spin / done

    private void renderSpin(GuiGraphics g, float partialTick) {
        float time = state == State.SPINNING ? Math.min(spinTicks + partialTick, spinDuration) : spinDuration;
        float scroll = scrollAt(time);

        int stripLeft = panelX + 8;
        int stripRight = panelX + panelW - 8;
        int stripY = bodyTop + 8;
        int centerX = (stripLeft + stripRight) / 2;

        // tick sound when a new card passes under the marker
        int cardAtMarker = Mth.clamp((int) Math.floor((scroll + STRIP_PITCH / 2f) / STRIP_PITCH), 0, STRIP_CARDS - 1);
        if (state == State.SPINNING && cardAtMarker != lastTickCard) {
            lastTickCard = cardAtMarker;
            playTick(0.9f + 0.5f * (time / spinDuration));
        }

        g.fill(stripLeft, stripY - 2, stripRight, stripY + STRIP_H + 2, 0xFF0E1016);
        g.enableScissor(stripLeft, stripY - 2, stripRight, stripY + STRIP_H + 2);
        for (int n = 0; n < stripStacks.size(); n++) {
            int cx = Math.round(centerX + n * STRIP_PITCH - scroll);
            int x = cx - STRIP_W / 2;
            if (x + STRIP_W < stripLeft || x > stripRight) continue;
            int outline = (state == State.DONE && n == winnerIndex) ? pulse(0xFFFFFF, doneTicks) : 0;
            drawCard(g, x, stripY, STRIP_W, STRIP_H, stripStacks.get(n), stripRarities.get(n), 2.5f, outline);
        }
        // soft edge fade
        for (int i = 0; i < 36; i++) {
            int a = (int) (0xC0 * (1f - i / 36f));
            int col = (a << 24) | FADE_RGB;
            g.fill(stripLeft + i, stripY - 2, stripLeft + i + 1, stripY + STRIP_H + 2, col);
            g.fill(stripRight - i - 1, stripY - 2, stripRight - i, stripY + STRIP_H + 2, col);
        }
        g.disableScissor();

        // marker
        g.fill(centerX - 1, stripY - 8, centerX + 1, stripY + STRIP_H + 8, MARKER);
        g.fill(centerX - 4, stripY - 8, centerX + 4, stripY - 5, MARKER);
        g.fill(centerX - 4, stripY + STRIP_H + 5, centerX + 4, stripY + STRIP_H + 8, MARKER);

        if (state == State.DONE) {
            renderReveal(g, stripY + STRIP_H + 14);
        } else {
            g.drawCenteredString(font, Component.translatable("cscase.gui.opening"),
                    panelX + panelW / 2, stripY + STRIP_H + 16, 0xFF9AA0B0);
        }
    }

    private void renderReveal(GuiGraphics g, int y) {
        float fade = Mth.clamp(doneTicks / 8f, 0f, 1f);
        int alpha = (int) (fade * 255);
        if (alpha < 8) return;
        int rgb = wonRarity.rgb();

        // coloured glow behind the text
        int h = bodyBottom - y;
        if (h > 0) {
            g.fillGradient(panelX + 1, y, panelX + panelW - 1, y + h,
                    rgb, ((int) (fade * 0x40) << 24) | rgb);
        }

        int cx = panelX + panelW / 2;
        g.drawCenteredString(font, Component.translatable("cscase.gui.you_received"), cx, y + 6, (alpha << 24) | 0xC8CCD6);

        Component name = wonStack.getCount() > 1
                ? Component.literal(wonStack.getCount() + "x ").append(wonStack.getHoverName())
                : wonStack.getHoverName();
        g.pose().pushPose();
        g.pose().translate(cx, y + 20, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawCenteredString(font, name, 0, 0, (alpha << 24) | rgb);
        g.pose().popPose();

        g.drawCenteredString(font, wonRarity.displayName(), cx, y + 38, (alpha << 24) | rgb);
    }

    // -- shared card drawing

    /**
     * A CS-style item card: dark body, rarity-tinted gradient, rarity bar along the bottom.
     * {@code outline} is an ARGB colour, or 0 for none.
     */
    private void drawCard(GuiGraphics g, int x, int y, int w, int h, ItemStack stack, CaseRarity rarity,
                          float itemScale, int outline) {
        int rgb = rarity.rgb();
        g.fill(x, y, x + w, y + h, CARD_BG);
        g.fillGradient(x, y, x + w, y + h, (0x10 << 24) | rgb, (0x70 << 24) | rgb);
        g.fill(x, y + h - 3, x + w, y + h, rarity.argb());

        float size = 16 * itemScale;
        float ix = x + (w - size) / 2f;
        float iy = y + (h - 3 - size) / 2f - (h > 64 ? 0 : 4);
        g.pose().pushPose();
        g.pose().translate(ix, iy, 0);
        g.pose().scale(itemScale, itemScale, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();

        if (stack.getCount() > 1) {
            String count = "x" + stack.getCount();
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font, count, x + w - font.width(count) - 3, y + 3, 0xFFFFFFFF, true);
            g.pose().popPose();
        }

        if (outline != 0) {
            g.fill(x, y, x + w, y + 1, outline);
            g.fill(x, y + h - 1, x + w, y + h, outline);
            g.fill(x, y, x + 1, y + h, outline);
            g.fill(x + w - 1, y, x + w, y + h, outline);
        }
    }

    private static int pulse(int rgb, int ticks) {
        int a = 140 + (int) (115 * (0.5 + 0.5 * Math.sin(ticks * 0.3)));
        return (a << 24) | rgb;
    }

    // ------------------------------------------------------------------ input / misc

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (state == State.SPINNING && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            finishSpin();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return state != State.SPINNING;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
