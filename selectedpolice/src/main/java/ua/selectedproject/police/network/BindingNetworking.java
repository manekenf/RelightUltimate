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

    /**
     * Broadcast a binding state change to every online player.
     * <p>
     * For {@link BindingSyncPayload.State#LEASHED} and {@link BindingSyncPayload.State#BOUND}
     * supply the officer UUID. For {@link BindingSyncPayload.State#NONE} pass {@code null}
     * (the helper will substitute {@link BindingSyncPayload#NO_HOLDER}).
     */
    public static void broadcast(MinecraftServer server, UUID captive,
                                 BindingSyncPayload.State state, UUID holder) {
        if (server == null) return;
        UUID h = (holder != null) ? holder : BindingSyncPayload.NO_HOLDER;
        BindingSyncPayload payload = new BindingSyncPayload(captive, state, h);
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
            UUID holder;
            if (s.isBound()) {
                state = BindingSyncPayload.State.BOUND;
                holder = s.boundBy() != null ? s.boundBy() : BindingSyncPayload.NO_HOLDER;
            } else if (s.isLeashed()) {
                state = BindingSyncPayload.State.LEASHED;
                holder = s.leashedTo() != null ? s.leashedTo() : BindingSyncPayload.NO_HOLDER;
            } else {
                continue;
            }
            ServerPlayNetworking.send(recipient, new BindingSyncPayload(p.getUuid(), state, holder));
        }
    }
}