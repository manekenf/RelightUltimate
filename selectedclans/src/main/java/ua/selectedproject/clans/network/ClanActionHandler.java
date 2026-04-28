package ua.selectedproject.clans.network;

import ua.selectedproject.core.config.CoreLocalization;
import ua.selectedproject.core.config.CoreConfig;
import ua.selectedproject.core.data.DatabaseManager;
import ua.selectedproject.core.data.Clan;
import ua.selectedproject.core.data.ClanInvite;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ClanActionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedClans/Actions");

    public static void handleCreateClan(ServerPlayerEntity player, String name, String tag) {
        DatabaseManager db = DatabaseManager.getInstance();
        CoreLocalization lang = CoreLocalization.getInstance();
        CoreConfig config = CoreConfig.getInstance();

        // Validation
        if (db.isPlayerInAnyClan(player.getUuid())) {
            player.sendMessage(Text.literal(lang.get("clan.create.already_in_clan")));
            return;
        }

        name = name.trim();
        tag = tag.trim().toUpperCase();

        if (name.length() < config.clanNameMinLength || name.length() > config.clanNameMaxLength) {
            player.sendMessage(Text.literal(lang.get("clan.create.name_invalid",
                    config.clanNameMinLength, config.clanNameMaxLength)));
            return;
        }

        if (tag.length() < config.clanTagMinLength || tag.length() > config.clanTagMaxLength) {
            player.sendMessage(Text.literal(lang.get("clan.create.tag_invalid",
                    config.clanTagMinLength, config.clanTagMaxLength)));
            return;
        }

        if (db.isNameTaken(name)) {
            player.sendMessage(Text.literal(lang.get("clan.create.name_taken")));
            return;
        }

        if (db.isTagTaken(tag)) {
            player.sendMessage(Text.literal(lang.get("clan.create.tag_taken")));
            return;
        }

        // Create the clan
        Clan clan = db.createClan(name, tag, player.getUuid());
        if (clan != null) {
            // Add leader as member with correct name
            db.addMember(clan.getId(), player.getUuid(), player.getName().getString());

            // Broadcast to all online players
            String prefix = lang.get("prefix");
            Text announcement = Text.literal(lang.get("clan.create.success", prefix, name, tag));
            broadcastToServer(player.getServer(), announcement);

            // Send clan data back so client can open management screen
            NetworkHandler.sendClanDataToPlayer(player, clan);

            // Notify Discord
            ua.selectedproject.core.discord.DiscordBot bot = ua.selectedproject.core.discord.DiscordBot.getInstance();
            if (bot != null && bot.isRunning()) {
                bot.notifyClanCreated(name, tag, player.getName().getString());
            }

            LOGGER.info("Clan '{}' [{}] created by {}", name, tag, player.getName().getString());
        }
    }

    public static void handleInvitePlayer(ServerPlayerEntity inviter, String targetName) {
        DatabaseManager db = DatabaseManager.getInstance();
        CoreLocalization lang = CoreLocalization.getInstance();
        MinecraftServer server = inviter.getServer();

        // Get inviter's clan
        Clan clan = db.getClanByPlayer(inviter.getUuid());
        if (clan == null || !clan.getLeaderUuid().equals(inviter.getUuid())) {
            return; // Not a leader, ignore
        }

        // Find target player (must be online)
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetName);
        if (target == null) {
            inviter.sendMessage(Text.literal(lang.get("clan.invite.player_offline")));
            return;
        }

        // Check if target is already in a clan
        if (db.isPlayerInAnyClan(target.getUuid())) {
            inviter.sendMessage(Text.literal(lang.get("clan.invite.player_in_clan")));
            return;
        }

        // Create invite
        ClanInvite invite = db.createInvite(clan.getId(), inviter.getUuid(), target.getUuid());
        if (invite != null) {
            inviter.sendMessage(Text.literal(lang.get("clan.invite.sent", targetName)));

            // Send clickable invite to target
            String inviterName = inviter.getName().getString();
            MutableText acceptButton = Text.literal("§a[✔]")
                    .styled(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/clan accept " + clan.getId()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("§aПрийняти / Accept"))));

            MutableText declineButton = Text.literal(" §c[✘]")
                    .styled(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/clan decline " + clan.getId()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("§cВідхилити / Decline"))));

            MutableText inviteMsg = Text.literal(
                    String.format("§6%s§r запрошує вас до клану §e%s§r! ", inviterName, clan.getName())
            );
            inviteMsg.append(acceptButton).append(declineButton);
            target.sendMessage(inviteMsg);

            LOGGER.info("{} invited {} to clan '{}'", inviterName, targetName, clan.getName());
        }
    }

    public static void handleKickPlayer(ServerPlayerEntity kicker, String targetUuidStr) {
        DatabaseManager db = DatabaseManager.getInstance();
        CoreLocalization lang = CoreLocalization.getInstance();

        Clan clan = db.getClanByPlayer(kicker.getUuid());
        if (clan == null || !clan.getLeaderUuid().equals(kicker.getUuid())) {
            return; // Not a leader
        }

        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(targetUuidStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        // Cannot kick yourself (the leader)
        if (targetUuid.equals(kicker.getUuid())) {
            return;
        }

        // Verify target is in this clan
        Clan targetClan = db.getClanByPlayer(targetUuid);
        if (targetClan == null || targetClan.getId() != clan.getId()) {
            return;
        }

        // Get target name before removal
        var members = db.getClanMembers(clan.getId());
        String targetName = members.stream()
                .filter(m -> m.getPlayerUuid().equals(targetUuid))
                .map(m -> m.getPlayerName())
                .findFirst().orElse("Unknown");

        // Remove from clan
        db.removeMember(targetUuid);

        // Notify kicker
        kicker.sendMessage(Text.literal(lang.get("clan.kick.success", targetName)));

        // Notify kicked player if online
        ServerPlayerEntity target = kicker.getServer().getPlayerManager().getPlayer(targetUuid);
        if (target != null) {
            target.sendMessage(Text.literal(lang.get("clan.kick.notify", clan.getName())));
        }

        // Discord DM to kicked player
        ua.selectedproject.core.discord.DiscordBot bot = ua.selectedproject.core.discord.DiscordBot.getInstance();
        if (bot != null && bot.isRunning()) {
            bot.notifyPlayerKicked(targetUuid, clan.getName());
        }

        // Check if clan now needs deletion countdown
        checkClanMemberThreshold(clan);

        // Refresh the management screen data for the kicker
        Clan updatedClan = db.getClanById(clan.getId());
        if (updatedClan != null) {
            NetworkHandler.sendClanDataToPlayer(kicker, updatedClan);
        }

        LOGGER.info("{} kicked {} from clan '{}'", kicker.getName().getString(), targetName, clan.getName());
    }

    public static void handleAcceptInvite(ServerPlayerEntity player, int clanId) {
        DatabaseManager db = DatabaseManager.getInstance();
        CoreLocalization lang = CoreLocalization.getInstance();

        // Check for pending invite
        ClanInvite invite = db.getPendingInvite(player.getUuid(), clanId);
        if (invite == null) {
            player.sendMessage(Text.literal(lang.get("clan.invite.expired")));
            return;
        }

        // Check player isn't already in a clan
        if (db.isPlayerInAnyClan(player.getUuid())) {
            player.sendMessage(Text.literal(lang.get("clan.create.already_in_clan")));
            return;
        }

        // Accept
        db.updateInviteStatus(invite.getId(), ClanInvite.Status.ACCEPTED);
        db.addMember(clanId, player.getUuid(), player.getName().getString());

        Clan clan = db.getClanById(clanId);
        if (clan != null) {
            // Announce to clan members
            String playerName = player.getName().getString();
            Text announcement = Text.literal(lang.get("clan.invite.accepted", playerName));

            for (var member : db.getClanMembers(clanId)) {
                ServerPlayerEntity memberPlayer = player.getServer().getPlayerManager().getPlayer(member.getPlayerUuid());
                if (memberPlayer != null) {
                    memberPlayer.sendMessage(announcement);
                }
            }

            // Cancel deletion countdown if applicable
            if (clan.getDeletionScheduledAt() != null) {
                checkClanMemberThreshold(clan);
            }
        }

        LOGGER.info("{} accepted invite to clan {}", player.getName().getString(), clanId);
    }

    public static void handleDeclineInvite(ServerPlayerEntity player, int clanId) {
        DatabaseManager db = DatabaseManager.getInstance();
        CoreLocalization lang = CoreLocalization.getInstance();

        ClanInvite invite = db.getPendingInvite(player.getUuid(), clanId);
        if (invite == null) return;

        db.updateInviteStatus(invite.getId(), ClanInvite.Status.DECLINED);

        // Notify inviter if online
        ServerPlayerEntity inviter = player.getServer().getPlayerManager().getPlayer(invite.getInviterUuid());
        if (inviter != null) {
            inviter.sendMessage(Text.literal(lang.get("clan.invite.declined", player.getName().getString())));
        }
    }

    // ==================== CLAN LIFECYCLE ====================

    public static void checkClanMemberThreshold(Clan clan) {
        DatabaseManager db = DatabaseManager.getInstance();
        CoreLocalization lang = CoreLocalization.getInstance();
        CoreConfig config = CoreConfig.getInstance();

        int memberCount = db.getMemberCount(clan.getId());

        if (memberCount < config.clanMinMembersToSurvive) {
            if (clan.getDeletionScheduledAt() == null) {
                // Start deletion countdown
                java.time.Instant deletionTime = java.time.Instant.now()
                        .plus(java.time.Duration.ofDays(config.clanDeletionDelayDays));
                db.scheduleDeletion(clan.getId(), deletionTime);
                LOGGER.info("Clan '{}' scheduled for deletion at {}", clan.getName(), deletionTime);
            }
        } else {
            if (clan.getDeletionScheduledAt() != null) {
                // Cancel countdown
                db.cancelDeletion(clan.getId());
                LOGGER.info("Clan '{}' deletion cancelled — enough members", clan.getName());
            }
        }
    }

    // ==================== HELPERS ====================

    public static void broadcastToServer(MinecraftServer server, Text message) {
        if (server == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(message);
        }
    }
}
