package ua.selectedproject.clans.network;

import ua.selectedproject.core.data.DatabaseManager;
import ua.selectedproject.core.data.Clan;
import ua.selectedproject.core.data.ClanMember;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NetworkHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedClans/Network");

    // ==================== PAYLOAD IDS ====================
    public static final Identifier OPEN_CLAN_CREATE_SCREEN = Identifier.of("selectedclans", "open_create_screen");
    public static final Identifier OPEN_CLAN_MANAGE_SCREEN = Identifier.of("selectedclans", "open_manage_screen");
    public static final Identifier CLAN_CREATE_REQUEST = Identifier.of("selectedclans", "create_request");
    public static final Identifier CLAN_INVITE_REQUEST = Identifier.of("selectedclans", "invite_request");
    public static final Identifier CLAN_KICK_REQUEST = Identifier.of("selectedclans", "kick_request");
    public static final Identifier BOARD_INTERACTION = Identifier.of("selectedclans", "board_interact");
    public static final Identifier CLAN_DATA_SYNC = Identifier.of("selectedclans", "clan_data_sync");
    public static final Identifier CLAN_MEMBERS_SYNC = Identifier.of("selectedclans", "members_sync");

    // ==================== C2S PAYLOADS (Client -> Server) ====================

    /**
     * Client requests to create a clan with given name and tag.
     */
    public record ClanCreateRequestPayload(String name, String tag) implements CustomPayload {
        public static final Id<ClanCreateRequestPayload> ID = new Id<>(CLAN_CREATE_REQUEST);
        public static final PacketCodec<RegistryByteBuf, ClanCreateRequestPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, ClanCreateRequestPayload::name,
                        PacketCodecs.STRING, ClanCreateRequestPayload::tag,
                        ClanCreateRequestPayload::new
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * Client requests to invite a player by name.
     */
    public record ClanInviteRequestPayload(String targetName) implements CustomPayload {
        public static final Id<ClanInviteRequestPayload> ID = new Id<>(CLAN_INVITE_REQUEST);
        public static final PacketCodec<RegistryByteBuf, ClanInviteRequestPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, ClanInviteRequestPayload::targetName,
                        ClanInviteRequestPayload::new
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * Client requests to kick a member by UUID string.
     */
    public record ClanKickRequestPayload(String targetUuid) implements CustomPayload {
        public static final Id<ClanKickRequestPayload> ID = new Id<>(CLAN_KICK_REQUEST);
        public static final PacketCodec<RegistryByteBuf, ClanKickRequestPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, ClanKickRequestPayload::targetUuid,
                        ClanKickRequestPayload::new
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * Client interacts with the clan board block.
     */
    public record BoardInteractionPayload(long blockPos) implements CustomPayload {
        public static final Id<BoardInteractionPayload> ID = new Id<>(BOARD_INTERACTION);
        public static final PacketCodec<RegistryByteBuf, BoardInteractionPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_LONG, BoardInteractionPayload::blockPos,
                        BoardInteractionPayload::new
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ==================== S2C PAYLOADS (Server -> Client) ====================

    /**
     * Server tells client to open clan creation screen. No payload data needed.
     */
    public record OpenClanCreateScreenPayload() implements CustomPayload {
        public static final OpenClanCreateScreenPayload INSTANCE = new OpenClanCreateScreenPayload();
        public static final Id<OpenClanCreateScreenPayload> ID = new Id<>(OPEN_CLAN_CREATE_SCREEN);
        public static final PacketCodec<RegistryByteBuf, OpenClanCreateScreenPayload> CODEC =
                PacketCodec.unit(INSTANCE);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * Server sends clan data for the management screen.
     */
    public record ClanDataSyncPayload(
            int clanId, String name, String tag, String leaderUuid,
            long createdAt, int memberCount, int rank
    ) implements CustomPayload {
        public static final Id<ClanDataSyncPayload> ID = new Id<>(CLAN_DATA_SYNC);
        public static final PacketCodec<RegistryByteBuf, ClanDataSyncPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> {
                            buf.writeInt(value.clanId);
                            buf.writeString(value.name);
                            buf.writeString(value.tag);
                            buf.writeString(value.leaderUuid);
                            buf.writeLong(value.createdAt);
                            buf.writeInt(value.memberCount);
                            buf.writeInt(value.rank);
                        },
                        buf -> new ClanDataSyncPayload(
                                buf.readInt(),
                                buf.readString(),
                                buf.readString(),
                                buf.readString(),
                                buf.readLong(),
                                buf.readInt(),
                                buf.readInt()
                        )
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * One row in a member sync. Serialized as (uuid_string, name).
     */
    public record MemberEntry(String uuid, String name) {
        public static final PacketCodec<RegistryByteBuf, MemberEntry> CODEC =
                PacketCodec.of(
                        (value, buf) -> {
                            buf.writeString(value.uuid);
                            buf.writeString(value.name);
                        },
                        buf -> new MemberEntry(buf.readString(), buf.readString())
                );
    }

    /**
     * Server sends member list as a structured list — replaces the old pipe-delimited
     * string, which would truncate (32767-char STRING cap) and was fragile against
     * names containing '|' or ';'.
     */
    public record ClanMembersSyncPayload(java.util.List<MemberEntry> members) implements CustomPayload {
        public static final Id<ClanMembersSyncPayload> ID = new Id<>(CLAN_MEMBERS_SYNC);
        public static final PacketCodec<RegistryByteBuf, ClanMembersSyncPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> {
                            buf.writeVarInt(value.members.size());
                            for (MemberEntry m : value.members) MemberEntry.CODEC.encode(buf, m);
                        },
                        buf -> {
                            int n = buf.readVarInt();
                            java.util.List<MemberEntry> list = new java.util.ArrayList<>(n);
                            for (int i = 0; i < n; i++) list.add(MemberEntry.CODEC.decode(buf));
                            return new ClanMembersSyncPayload(list);
                        }
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ==================== REGISTRATION ====================

    public static void registerServerPayloads() {
        // Register C2S payloads
        PayloadTypeRegistry.playC2S().register(ClanCreateRequestPayload.ID, ClanCreateRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClanInviteRequestPayload.ID, ClanInviteRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClanKickRequestPayload.ID, ClanKickRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BoardInteractionPayload.ID, BoardInteractionPayload.CODEC);

        // Register S2C payloads
        PayloadTypeRegistry.playS2C().register(OpenClanCreateScreenPayload.ID, OpenClanCreateScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ClanDataSyncPayload.ID, ClanDataSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ClanMembersSyncPayload.ID, ClanMembersSyncPayload.CODEC);

        LOGGER.info("Network payloads registered");
    }

    public static void registerServerReceivers() {
        // Handle clan creation request from client
        ServerPlayNetworking.registerGlobalReceiver(ClanCreateRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                ClanActionHandler.handleCreateClan(player, payload.name(), payload.tag());
            });
        });

        // Handle invite request
        ServerPlayNetworking.registerGlobalReceiver(ClanInviteRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                ClanActionHandler.handleInvitePlayer(player, payload.targetName());
            });
        });

        // Handle kick request
        ServerPlayNetworking.registerGlobalReceiver(ClanKickRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                ClanActionHandler.handleKickPlayer(player, payload.targetUuid());
            });
        });

        LOGGER.info("Network receivers registered");
    }

    // ==================== HELPERS ====================

    /**
     * Send clan data + member list to a player for the management screen.
     */
    public static void sendClanDataToPlayer(ServerPlayerEntity player, Clan clan) {
        DatabaseManager db = DatabaseManager.getInstance();
        List<ClanMember> members = db.getClanMembers(clan.getId());
        int memberCount = members.size();

        // Exact rank from DB — no top-100 truncation issue.
        int rank = db.getClanSizeRank(clan.getId());

        // Send clan data
        ServerPlayNetworking.send(player, new ClanDataSyncPayload(
                clan.getId(), clan.getName(), clan.getTag(),
                clan.getLeaderUuid().toString(),
                clan.getCreatedAt().getEpochSecond(),
                memberCount, rank
        ));

        java.util.List<MemberEntry> entries = new java.util.ArrayList<>(members.size());
        for (ClanMember m : members) {
            entries.add(new MemberEntry(m.getPlayerUuid().toString(), m.getPlayerName()));
        }
        ServerPlayNetworking.send(player, new ClanMembersSyncPayload(entries));
    }
}
