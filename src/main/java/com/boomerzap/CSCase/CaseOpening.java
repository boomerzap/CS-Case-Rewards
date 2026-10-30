package com.boomerzap.CSCase;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.boomerzap.CSCase.network.CaseResultPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side opening logic. The result is rolled immediately (so it cannot be influenced by the
 * client), the animation is played on the client, and the item is handed over when the animation
 * is due to finish - or straight away if the player disconnects or the server stops first.
 */
public final class CaseOpening {
    private static final class Pending {
        final ResourceLocation caseId;
        final ItemStack stack;
        final CaseRarity rarity;
        int ticksLeft;

        Pending(ResourceLocation caseId, ItemStack stack, CaseRarity rarity, int ticksLeft) {
            this.caseId = caseId;
            this.stack = stack;
            this.rarity = rarity;
            this.ticksLeft = ticksLeft;
        }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private CaseOpening() {}

    public static boolean isOpening(ServerPlayer player) {
        return PENDING.containsKey(player.getUUID());
    }

    /** Rolls a result, plays the animation on the player's client and schedules the reward. */
    public static boolean startAnimated(ServerPlayer player, ResourceLocation caseId) {
        CaseDefinition def = CaseManager.get(caseId);
        if (def == null || isOpening(player)) return false;

        CaseEntry won = def.roll(player.getRandom());
        int ticks = Config.OPEN_TICKS.get();
        PENDING.put(player.getUUID(), new Pending(caseId, won.stack().copy(), won.rarity(), ticks));
        PacketDistributor.sendToPlayer(player, new CaseResultPayload(caseId, won.stack().copy(), won.rarity(), ticks));
        return true;
    }

    /** Rolls a result and hands it over immediately, with no GUI. */
    public static boolean grantInstantly(ServerPlayer player, ResourceLocation caseId) {
        CaseDefinition def = CaseManager.get(caseId);
        if (def == null) return false;
        CaseEntry won = def.roll(player.getRandom());
        give(player, new Pending(caseId, won.stack().copy(), won.rarity(), 0));
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                // Offline without the logout hook having run; nothing sensible to do but drop it.
                it.remove();
                continue;
            }
            if (!player.isAlive()) continue;
            Pending p = entry.getValue();
            if (--p.ticksLeft <= 0) {
                it.remove();
                give(player, p);
            }
        }
    }

    /** Hands over a pending reward right now (logout / server stop). */
    public static void flush(ServerPlayer player) {
        Pending p = PENDING.remove(player.getUUID());
        if (p != null) give(player, p);
    }

    private static void give(ServerPlayer player, Pending p) {
        ItemStack stack = p.stack.copy();
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }

        if (Config.ANNOUNCE.get() && p.rarity.compareTo(Config.ANNOUNCE_MIN_RARITY.get()) >= 0) {
            Component caseName = caseName(p.caseId);
            Component msg = Component.translatable("cscase.announce",
                    player.getDisplayName(), describe(p.stack, p.rarity), caseName)
                    .withStyle(ChatFormatting.GRAY);
            player.server.getPlayerList().broadcastSystemMessage(msg, false);
        }
    }

    private static Component caseName(ResourceLocation id) {
        CaseDefinition def = CaseManager.get(id);
        return def != null ? def.displayName(id) : Component.literal(id.toString());
    }

    /** "3x Diamond" in the rarity colour. */
    public static Component describe(ItemStack stack, CaseRarity rarity) {
        Component name = stack.getCount() > 1
                ? Component.literal(stack.getCount() + "x ").append(stack.getHoverName())
                : stack.getHoverName();
        return name.copy().withStyle(s -> s.withColor(rarity.rgb()));
    }
}
