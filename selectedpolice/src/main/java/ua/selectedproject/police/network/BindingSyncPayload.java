package ua.selectedproject.police.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * S2C payload describing a player's binding state change.
 * <p>
 * Sent when:
 * <ul>
 *   <li>A player becomes leashed → {@code state=LEASHED}</li>
 *   <li>A player becomes bound (jailed) → {@code state=BOUND}</li>
 *   <li>A player is released from either → {@code state=NONE}</li>
 *   <li>A player joins → server replays current state of every bound/leashed player</li>
 * </ul>
 */
public record BindingSyncPayload(UUID player, State state) implements CustomPayload {

    public enum State {
        NONE,
        LEASHED,
        BOUND;

        public static final PacketCodec<PacketByteBuf, State> CODEC =
                PacketCodec.<PacketByteBuf, State>of(
                        (state, buf) -> buf.writeByte(state.ordinal()),
                        buf -> values()[buf.readByte()]
                );
    }

    public static final Identifier ID = Identifier.of("selectedpolice", "binding_sync");
    public static final CustomPayload.Id<BindingSyncPayload> PACKET_ID = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, BindingSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), BindingSyncPayload::player,
            State.CODEC,                                                BindingSyncPayload::state,
            BindingSyncPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}