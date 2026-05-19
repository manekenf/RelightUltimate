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
 *   <li>A player becomes leashed → {@code state=LEASHED}, {@code holder=officerUuid}</li>
 *   <li>A player becomes bound (jailed) → {@code state=BOUND}, {@code holder=officerUuid}</li>
 *   <li>A player is released → {@code state=NONE}, {@code holder=ZERO}</li>
 *   <li>A player joins → server replays current state of every bound/leashed player</li>
 * </ul>
 * <p>
 * Holder is always present in the payload (UUIDs are fixed-width) but is meaningless
 * when state == NONE — by convention we send {@link #NO_HOLDER} (all-zero UUID) there.
 * The client treats NO_HOLDER as "no holder mapping".
 */
public record BindingSyncPayload(UUID player, State state, UUID holder) implements CustomPayload {

    /** Sentinel UUID used when no holder applies (state == NONE). */
    public static final UUID NO_HOLDER = new UUID(0L, 0L);

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
            PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), BindingSyncPayload::holder,
            BindingSyncPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}