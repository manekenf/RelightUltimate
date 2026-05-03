package ua.selectedproject.police.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ua.selectedproject.police.PoliceDatabase;

import java.util.UUID;

public final class BindingNetworking {
    private BindingNetworking() {}

    public static void broadcastBind(MinecraftServer server, UUID captive, UUID officer) {
        BindingSyncPayload payload = new BindingSyncPayload(captive, officer, true);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void broadcastUnbind(MinecraftServer server, UUID captive) {
        BindingSyncPayload payload = new BindingSyncPayload(captive, new UUID(0,0), false);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    /** Replay all active bindings to a single player (called on JOIN). */
    public static void syncAllToPlayer(ServerPlayerEntity player) {
        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) return;
        for (UUID captive : db.getAllLeashedPlayers()) {
            UUID officer = db.getLeashedTo(captive);
            if (officer != null) {
                ServerPlayNetworking.send(player,
                        new BindingSyncPayload(captive, officer, true));
            }
        }
    }
}