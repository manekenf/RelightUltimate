package ua.selectedproject.police.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ua.selectedproject.police.PoliceDatabase;
import ua.selectedproject.police.data.PlayerPvpStatus;

import java.util.UUID;

public final class BindingNetworking {
    private BindingNetworking() {}

    private static boolean registered = false;

    /** Register the payload type. Called from main mod init (both sides need this). */
    public static void registerPayloads() {
        if (registered) return;
        registered = true;
        PayloadTypeRegistry.playS2C().register(BindingSyncPayload.PACKET_ID, BindingSyncPayload.CODEC);

    }

    /** Broadcast a state change to all online players (so they all render the captive correctly). */
    public static void broadcast(MinecraftServer server, UUID player, BindingSyncPayload.State state) {
        if (server == null) return;
        BindingSyncPayload payload = new BindingSyncPayload(player, state);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    /** Replay current state of every bound/leashed player to a single (just-joined) player. */
    public static void syncAllToPlayer(ServerPlayerEntity recipient) {
        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) return;
        // Iterate over all players known to the server. We could query the DB more directly,
        // but this is simple and fine — only fires on join.
        for (ServerPlayerEntity p : recipient.getServer().getPlayerManager().getPlayerList()) {
            PlayerPvpStatus s = db.getPvpStatus(p.getUuid());
            if (s == null) continue;
            BindingSyncPayload.State state;
            if (s.isBound())        state = BindingSyncPayload.State.BOUND;
            else if (s.isLeashed()) state = BindingSyncPayload.State.LEASHED;
            else                    continue;
            ServerPlayNetworking.send(recipient, new BindingSyncPayload(p.getUuid(), state));
        }
    }
}