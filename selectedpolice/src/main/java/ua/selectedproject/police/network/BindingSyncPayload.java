package ua.selectedproject.police.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Sync packet — server tells the client about an active binding.
 * <p>
 * Sent when:
 * <ul>
 *   <li>A criminal becomes leashed — broadcast to all players in render distance</li>
 *   <li>A criminal is unleashed — broadcast with {@code active=false}</li>
 *   <li>A player joins — server replays all active bindings to them</li>
 * </ul>
 */
public record BindingSyncPayload(UUID captive, UUID officer, boolean active) implements CustomPayload {
    public static final Identifier ID = Identifier.of("selectedpolice", "binding_sync");
    public static final CustomPayload.Id<BindingSyncPayload> PACKET_ID = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, BindingSyncPayload> CODEC = PacketCodec.tuple(
            net.minecraft.network.codec.PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString),
            BindingSyncPayload::captive,
            net.minecraft.network.codec.PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString),
            BindingSyncPayload::officer,
            net.minecraft.network.codec.PacketCodecs.BOOL,
            BindingSyncPayload::active,
            BindingSyncPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}