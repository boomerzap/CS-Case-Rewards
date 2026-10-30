package com.boomerzap.CSCase.network;

import java.util.HashMap;
import java.util.Map;

import com.boomerzap.CSCase.CSCase;
import com.boomerzap.CSCase.CaseDefinition;
import com.boomerzap.CSCase.CaseManager;
import com.boomerzap.CSCase.CaseOpening;
import com.boomerzap.CSCase.client.ClientHooks;
import com.boomerzap.CSCase.item.CaseItem;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CaseNetwork {
    private static final String PROTOCOL = "1";
    private static final int CASES_PER_PACKET = 16;

    private CaseNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(CSCase.MODID).versioned(PROTOCOL);
        registrar.playToClient(SyncCasesPayload.TYPE, SyncCasesPayload.STREAM_CODEC,
                (payload, ctx) -> ClientHooks.handleSync(payload));
        registrar.playToClient(CaseResultPayload.TYPE, CaseResultPayload.STREAM_CODEC,
                (payload, ctx) -> ClientHooks.handleResult(payload));
        registrar.playToServer(OpenCasePayload.TYPE, OpenCasePayload.STREAM_CODEC,
                (payload, ctx) -> handleOpenRequest(payload, (ServerPlayer) ctx.player()));
    }

    /** Sends every known case to a player, in packet-sized chunks. */
    public static void syncTo(ServerPlayer player) {
        Map<ResourceLocation, CaseDefinition> chunk = new HashMap<>();
        boolean first = true;
        for (Map.Entry<ResourceLocation, CaseDefinition> e : CaseManager.all().entrySet()) {
            chunk.put(e.getKey(), e.getValue());
            if (chunk.size() >= CASES_PER_PACKET) {
                PacketDistributor.sendToPlayer(player, new SyncCasesPayload(first, new HashMap<>(chunk)));
                chunk.clear();
                first = false;
            }
        }
        // Always send a final (possibly empty) packet so a sync of zero cases still clears the client.
        if (!chunk.isEmpty() || first) {
            PacketDistributor.sendToPlayer(player, new SyncCasesPayload(first, chunk));
        }
    }

    private static void handleOpenRequest(OpenCasePayload payload, ServerPlayer player) {
        ItemStack stack = player.getItemInHand(payload.hand());
        ResourceLocation id = stack.getItem() instanceof CaseItem ? CaseItem.getCaseId(stack) : null;
        if (id == null) return;

        if (CaseManager.get(id) == null) {
            player.displayClientMessage(Component.translatable("cscase.message.unknown_case", id.toString()), true);
            return;
        }
        if (CaseOpening.isOpening(player)) return;

        if (CaseOpening.startAnimated(player, id)) {
            stack.consume(1, player);
        }
    }
}
