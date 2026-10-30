package com.boomerzap.CSCase;

import com.boomerzap.CSCase.network.CaseNetwork;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus event handlers. */
public final class CaseEvents {
    private CaseEvents() {}

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CaseLoader(event.getServerResources().getRegistryLookup()));
    }

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(CaseNetwork::syncTo);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        CaseOpening.tick(event.getServer());
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CaseOpening.flush(player);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            CaseOpening.flush(player);
        }
    }
}
