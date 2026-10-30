package com.boomerzap.CSCase.client;

import com.boomerzap.CSCase.CaseDefinition;
import com.boomerzap.CSCase.CaseManager;
import com.boomerzap.CSCase.item.CaseItem;
import com.boomerzap.CSCase.network.CaseResultPayload;
import com.boomerzap.CSCase.network.SyncCasesPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Entry points called from common code. Only ever invoked on the physical client. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openPreview(ItemStack stack, InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation id = CaseItem.getCaseId(stack);
        CaseDefinition def = CaseManager.get(id);
        if (id == null || def == null) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("cscase.message.unknown_case", String.valueOf(id)), true);
            }
            return;
        }
        mc.setScreen(new CaseScreen(id, def, hand));
    }

    public static void handleSync(SyncCasesPayload payload) {
        if (payload.replace()) {
            CaseManager.replaceAll(payload.cases());
        } else {
            CaseManager.putAll(payload.cases());
        }
    }

    public static void handleResult(CaseResultPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CaseScreen screen && screen.caseId().equals(payload.caseId())) {
            screen.startSpin(payload.won(), payload.rarity(), payload.ticks());
        } else {
            // Opened by command (no preview screen yet): go straight to the animation.
            CaseScreen screen = new CaseScreen(payload.caseId(), CaseManager.get(payload.caseId()), null);
            mc.setScreen(screen);
            screen.startSpin(payload.won(), payload.rarity(), payload.ticks());
        }
    }
}
