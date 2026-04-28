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
    public static final Identifier OPEN_CLAN_CREATE_SCREEN = Identifier.of("clansmod", "open_create_screen");
    public static final Identifier OPEN_CLAN_MANAGE_SCREEN = Identifier.of("clansmod", "open_manage_screen");
    public static final Identifier CLAN_CREATE_REQUEST = Identifier.of("clansmod", "create_request");
    public static final Identifier CLAN_INVITE_REQUEST = Identifier.of("clansmod", "invite_request");
    public static final Identifier CLAN_KICK_REQUEST = Identifier.of("clansmod", "kick_request");
    public static final Identifier BOARD_INTERACTION = Identifier.of("clansmod", "board_interact");
    public static final Identifier CLAN_DATA_SYNC = Identifier.of("clansmod", "clan_data_sync");
    public static final Identifier CLAN_MEMBERS_SYNC = Identifier.of("clansmod", "members_sync");

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
     * Server tells client to open clan creation screen.
     */
    public record OpenClanCreateScreenPayload(boolean dummy) implements CustomPayload {
        public static final Id<OpenClanCreateScreenPayload> ID = new Id<>(OPEN_CLAN_CREATE_SCREEN);
        public static final PacketCodec<RegistryByteBuf, OpenClanCreateScreenPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.BOOL, OpenClanCreateScreenPayload::dummy,
                        OpenClanCreateScreenPayload::new
                );
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
     * Server sends serialized member list (pipe-delimited entries: uuid|name).
     */
    public record ClanMembersSyncPayload(String membersData) implements CustomPayload {
        public static final Id<ClanMembersSyncPayload> ID = new Id<>(CLAN_MEMBERS_SYNC);
        public static final PacketCodec<RegistryByteBuf, ClanMembersSyncPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, ClanMembersSyncPayload::membersData,
                        ClanMembersSyncPayload::new
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

        // Calculate rank (by member count)
        var topClans = db.getTopClansBySize(100);
        int rank = 1;
        for (var entry : topClans) {
            if (entry.getKey().getId() == clan.getId()) break;
            rank++;
        }

        // Send clan data
        ServerPlayNetworking.send(player, new ClanDataSyncPayload(
                clan.getId(), clan.getName(), clan.getTag(),
                clan.getLeaderUuid().toString(),
                clan.getCreatedAt().getEpochSecond(),
                memberCount, rank
        ));

        // Send member list as pipe-delimited string: "uuid|name;uuid|name;..."
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < members.size(); i++) {
            ClanMember m = members.get(i);
            if (i > 0) sb.append(";");
            sb.append(m.getPlayerUuid().toString()).append("|").append(m.getPlayerName());
        }
        ServerPlayNetworking.send(player, new ClanMembersSyncPayload(sb.toString()));
    }
}
