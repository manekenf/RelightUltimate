package ua.selectedproject.clans.chat;

import ua.selectedproject.core.SelectedCore;
import ua.selectedproject.core.config.CoreLocalization;
import ua.selectedproject.core.config.CoreConfig;
import ua.selectedproject.core.data.DatabaseManager;
import ua.selectedproject.core.data.Clan;
import ua.selectedproject.core.data.ClanMember;
import ua.selectedproject.core.economy.CoinItems;
import ua.selectedproject.clans.network.ClanActionHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ClanCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register(ClanCommands::registerAll);
    }

    private static void registerAll(CommandDispatcher<ServerCommandSource> dispatcher,
                                     CommandRegistryAccess registryAccess,
                                     CommandManager.RegistrationEnvironment environment) {

        // /clan accept/decline/leave/info
        dispatcher.register(literal("clan")
                .then(literal("accept")
                        .then(argument("clanId", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    int clanId = IntegerArgumentType.getInteger(ctx, "clanId");
                                    ClanActionHandler.handleAcceptInvite(player, clanId);
                                    return 1;
                                })))
                .then(literal("decline")
                        .then(argument("clanId", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    int clanId = IntegerArgumentType.getInteger(ctx, "clanId");
                                    ClanActionHandler.handleDeclineInvite(player, clanId);
                                    return 1;
                                })))
                .then(literal("leave")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            return handleLeave(player);
                        }))
                .then(literal("info")
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    String name = StringArgumentType.getString(ctx, "name");
                                    return handleInfo(player, name);
                                })))
        );

        // /cc <message>
        dispatcher.register(literal("cc")
                .then(argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            String message = StringArgumentType.getString(ctx, "message");
                            return handleClanChat(player, message);
                        }))
        );

        // /lidc <message>
        dispatcher.register(literal("lidc")
                .then(argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            String message = StringArgumentType.getString(ctx, "message");
                            return handleLeaderChat(player, message);
                        }))
        );

        // /ccn <message>
        dispatcher.register(literal("ccn")
                .requires(source -> source.hasPermissionLevel(2))
                .then(argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String message = StringArgumentType.getString(ctx, "message");
                            return handleAdminBroadcast(ctx.getSource(), message);
                        }))
        );

        // /clansmod (clan-specific admin commands)
        dispatcher.register(literal("clansmod")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("setboard")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(
                                    () -> Text.literal("§aPlace the Clan Board block at the desired location."),
                                    false);
                            return 1;
                        }))
                .then(literal("setleaderboard")
                        .then(literal("size")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    net.minecraft.util.math.Vec3d pos = player.getPos();
                                    ua.selectedproject.clans.leaderboard.LeaderboardUpdater.setSizeLeaderboardPos(pos);
                                    ua.selectedproject.clans.leaderboard.LeaderboardUpdater.update(player.getServer());
                                    ctx.getSource().sendFeedback(
                                            () -> Text.literal(String.format("§aSize leaderboard set at %.1f, %.1f, %.1f", pos.x, pos.y, pos.z)),
                                            true);
                                    return 1;
                                }))
                        .then(literal("wealth")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    net.minecraft.util.math.Vec3d pos = player.getPos();
                                    ua.selectedproject.clans.leaderboard.LeaderboardUpdater.setWealthLeaderboardPos(pos);
                                    ua.selectedproject.clans.leaderboard.LeaderboardUpdater.update(player.getServer());
                                    ctx.getSource().sendFeedback(
                                            () -> Text.literal(String.format("§aWealth leaderboard set at %.1f, %.1f, %.1f", pos.x, pos.y, pos.z)),
                                            true);
                                    return 1;
                                })))
        );

        // /clanadmin
        dispatcher.register(literal("clanadmin")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("create")
                        .then(argument("name", StringArgumentType.string())
                                .then(argument("tag", StringArgumentType.word())
                                        .then(argument("leader", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    String tag = StringArgumentType.getString(ctx, "tag");
                                                    String leaderName = StringArgumentType.getString(ctx, "leader");
                                                    return adminCreateClan(ctx.getSource(), name, tag, leaderName);
                                                })))))
                .then(literal("delete")
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> adminDeleteClan(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("addplayer")
                        .then(argument("clan", StringArgumentType.string())
                                .then(argument("player", StringArgumentType.word())
                                        .executes(ctx -> adminAddPlayer(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "clan"),
                                                StringArgumentType.getString(ctx, "player"))))))
                .then(literal("removeplayer")
                        .then(argument("player", StringArgumentType.word())
                                .executes(ctx -> adminRemovePlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                .then(literal("transferleader")
                        .then(argument("clan", StringArgumentType.string())
                                .then(argument("player", StringArgumentType.word())
                                        .executes(ctx -> adminTransferLeader(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "clan"),
                                                StringArgumentType.getString(ctx, "player"))))))
                .then(literal("setshop")
                        .then(argument("clan", StringArgumentType.string())
                                .then(argument("number", IntegerArgumentType.integer(0))
                                        .executes(ctx -> adminSetShop(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "clan"),
                                                IntegerArgumentType.getInteger(ctx, "number"))))))
                .then(literal("list")
                        .executes(ctx -> adminListClans(ctx.getSource())))
                .then(literal("info")
                        .then(argument("clan", StringArgumentType.greedyString())
                                .executes(ctx -> adminClanInfo(ctx.getSource(), StringArgumentType.getString(ctx, "clan")))))
        );

        SelectedCore.LOGGER.info("Commands registered");
    }

    // ==================== PLAYER COMMAND HANDLERS ====================

    private static int handleLeave(ServerPlayerEntity player) {
        DatabaseManager db = DatabaseManager.getInstance();
        CoreLocalization lang = CoreLocalization.getInstance();

        Clan clan = db.getClanByPlayer(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("§cYou are not in a clan."));
            return 0;
        }
        if (clan.getLeaderUuid().equals(player.getUuid())) {
            player.sendMessage(Text.literal(lang.get("clan.leave.leader_cannot")));
            return 0;
        }
        db.removeMember(player.getUuid());
        player.sendMessage(Text.literal(lang.get("clan.leave.success", clan.getName())));
        ClanActionHandler.checkClanMemberThreshold(clan);
        return 1;
    }

    private static int handleInfo(ServerPlayerEntity player, String nameOrTag) {
        DatabaseManager db = DatabaseManager.getInstance();
        CoreLocalization lang = CoreLocalization.getInstance();

        Clan foundClan = db.getClanByName(nameOrTag);
        if (foundClan == null) foundClan = db.getClanByTag(nameOrTag);
        if (foundClan == null) {
            player.sendMessage(Text.literal("§cClan not found."));
            return 0;
        }
        final Clan clan = foundClan;

        int memberCount = db.getMemberCount(clan.getId());
        player.sendMessage(Text.literal(lang.get("clan.info.header", clan.getName(), clan.getTag())));
        player.sendMessage(Text.literal(lang.get("clan.info.created", clan.getCreatedAt().toString())));
        player.sendMessage(Text.literal(lang.get("clan.info.members", memberCount)));
        if (clan.getShopNumber() != null) {
            player.sendMessage(Text.literal(lang.get("clan.info.shop", clan.getShopNumber())));
        } else {
            player.sendMessage(Text.literal(lang.get("clan.info.no_shop")));
        }
        return 1;
    }

    private static int handleClanChat(ServerPlayerEntity player, String message) {
        DatabaseManager db = DatabaseManager.getInstance();
        CoreLocalization lang = CoreLocalization.getInstance();

        Clan clan = db.getClanByPlayer(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("§cYou are not in a clan."));
            return 0;
        }
        String formatted = lang.get("chat.clan.format", player.getName().getString(), message);
        Text chatMsg = Text.literal(formatted);
        List<ClanMember> members = db.getClanMembers(clan.getId());
        for (ClanMember member : members) {
            ServerPlayerEntity online = player.getServer().getPlayerManager().getPlayer(member.getPlayerUuid());
            if (online != null) online.sendMessage(chatMsg);
        }
        return 1;
    }

    private static int handleLeaderChat(ServerPlayerEntity player, String message) {
        DatabaseManager db = DatabaseManager.getInstance();
        CoreLocalization lang = CoreLocalization.getInstance();

        Clan playerClan = db.getClanByPlayer(player.getUuid());
        if (playerClan == null || !playerClan.getLeaderUuid().equals(player.getUuid())) {
            player.sendMessage(Text.literal("§cOnly clan leaders can use this chat."));
            return 0;
        }
        String formatted = lang.get("chat.leader.format", player.getName().getString(), message);
        Text chatMsg = Text.literal(formatted);
        List<Clan> allClans = db.getAllClans();
        for (Clan clan : allClans) {
            ServerPlayerEntity leader = player.getServer().getPlayerManager().getPlayer(clan.getLeaderUuid());
            if (leader != null) leader.sendMessage(chatMsg);
        }
        return 1;
    }

    private static int handleAdminBroadcast(ServerCommandSource source, String message) {
        CoreLocalization lang = CoreLocalization.getInstance();
        String formatted = lang.get("chat.admin.broadcast", message);
        Text chatMsg = Text.literal(formatted);
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(chatMsg);
        }
        source.sendFeedback(() -> Text.literal("§aBroadcast sent."), true);
        return 1;
    }

    // ==================== ADMIN COMMAND HANDLERS ====================

    private static int adminCreateClan(ServerCommandSource source, String name, String tag, String leaderName) {
        DatabaseManager db = DatabaseManager.getInstance();
        tag = tag.toUpperCase();

        ServerPlayerEntity leader = source.getServer().getPlayerManager().getPlayer(leaderName);
        if (leader == null) { source.sendError(Text.literal("§cPlayer " + leaderName + " is not online.")); return 0; }
        if (db.isPlayerInAnyClan(leader.getUuid())) { source.sendError(Text.literal("§c" + leaderName + " is already in a clan.")); return 0; }
        if (db.isNameTaken(name)) { source.sendError(Text.literal("§cClan name '" + name + "' is already taken.")); return 0; }
        if (db.isTagTaken(tag)) { source.sendError(Text.literal("§cClan tag '" + tag + "' is already taken.")); return 0; }

        Clan clan = db.createClan(name, tag, leader.getUuid());
        if (clan != null) {
            db.addMember(clan.getId(), leader.getUuid(), leader.getName().getString());
            final String ft = tag;
            source.sendFeedback(() -> Text.literal("§aClan §6" + name + " §8[§e" + ft + "§8]§a created with leader §e" + leaderName), true);
            return 1;
        }
        source.sendError(Text.literal("§cFailed to create clan."));
        return 0;
    }

    private static int adminDeleteClan(ServerCommandSource source, String nameOrTag) {
        DatabaseManager db = DatabaseManager.getInstance();
        Clan foundClan = db.getClanByName(nameOrTag);
        if (foundClan == null) foundClan = db.getClanByTag(nameOrTag);
        if (foundClan == null) { source.sendError(Text.literal("§cClan not found: " + nameOrTag)); return 0; }
        final Clan clan = foundClan;

        String clanName = clan.getName();
        String clanTag = clan.getTag();
        db.deleteClan(clan.getId());
        source.sendFeedback(() -> Text.literal("§cClan §6" + clanName + " §8[§e" + clanTag + "§8]§c deleted."), true);
        var bot = ua.selectedproject.core.discord.DiscordBot.getInstance();
        if (bot != null && bot.isRunning()) bot.notifyClanDeleted(clanName, clanTag);
        return 1;
    }

    private static int adminAddPlayer(ServerCommandSource source, String clanName, String playerName) {
        DatabaseManager db = DatabaseManager.getInstance();
        Clan foundClan = db.getClanByName(clanName);
        if (foundClan == null) foundClan = db.getClanByTag(clanName);
        if (foundClan == null) { source.sendError(Text.literal("§cClan not found: " + clanName)); return 0; }
        final Clan clan = foundClan;

        ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(playerName);
        if (player == null) { source.sendError(Text.literal("§cPlayer " + playerName + " is not online.")); return 0; }
        if (db.isPlayerInAnyClan(player.getUuid())) { source.sendError(Text.literal("§c" + playerName + " is already in a clan.")); return 0; }

        db.addMember(clan.getId(), player.getUuid(), player.getName().getString());
        source.sendFeedback(() -> Text.literal("§aAdded §e" + playerName + "§a to clan §6" + clan.getName()), true);
        ClanActionHandler.checkClanMemberThreshold(clan);
        return 1;
    }

    private static int adminRemovePlayer(ServerCommandSource source, String playerName) {
        DatabaseManager db = DatabaseManager.getInstance();
        ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(playerName);
        if (player == null) { source.sendError(Text.literal("§cPlayer " + playerName + " is not online.")); return 0; }

        Clan clan = db.getClanByPlayer(player.getUuid());
        if (clan == null) { source.sendError(Text.literal("§c" + playerName + " is not in any clan.")); return 0; }
        if (clan.getLeaderUuid().equals(player.getUuid())) {
            source.sendError(Text.literal("§cCannot remove the leader. Use /clanadmin transferleader first."));
            return 0;
        }

        String cn = clan.getName();
        db.removeMember(player.getUuid());
        source.sendFeedback(() -> Text.literal("§cRemoved §e" + playerName + "§c from clan §6" + cn), true);
        ClanActionHandler.checkClanMemberThreshold(clan);
        return 1;
    }

    private static int adminTransferLeader(ServerCommandSource source, String clanName, String playerName) {
        DatabaseManager db = DatabaseManager.getInstance();
        Clan foundClan = db.getClanByName(clanName);
        if (foundClan == null) foundClan = db.getClanByTag(clanName);
        if (foundClan == null) { source.sendError(Text.literal("§cClan not found: " + clanName)); return 0; }
        final Clan clan = foundClan;

        ServerPlayerEntity newLeader = source.getServer().getPlayerManager().getPlayer(playerName);
        if (newLeader == null) { source.sendError(Text.literal("§cPlayer " + playerName + " is not online.")); return 0; }

        Clan playerClan = db.getClanByPlayer(newLeader.getUuid());
        if (playerClan == null || playerClan.getId() != clan.getId()) {
            source.sendError(Text.literal("§c" + playerName + " is not in clan " + clan.getName()));
            return 0;
        }

        db.transferLeadership(clan.getId(), newLeader.getUuid());
        source.sendFeedback(() -> Text.literal("§aLeadership of §6" + clan.getName() + "§a transferred to §e" + playerName), true);
        return 1;
    }

    private static int adminSetShop(ServerCommandSource source, String clanName, int shopNumber) {
        DatabaseManager db = DatabaseManager.getInstance();
        Clan foundClan = db.getClanByName(clanName);
        if (foundClan == null) foundClan = db.getClanByTag(clanName);
        if (foundClan == null) { source.sendError(Text.literal("§cClan not found: " + clanName)); return 0; }
        final Clan clan = foundClan;

        db.setShopNumber(clan.getId(), shopNumber);
        source.sendFeedback(() -> Text.literal("§aClan §6" + clan.getName() + "§a shop set to §e#" + shopNumber), true);
        return 1;
    }

    private static int adminListClans(ServerCommandSource source) {
        DatabaseManager db = DatabaseManager.getInstance();
        var clans = db.getAllClans();
        if (clans.isEmpty()) { source.sendFeedback(() -> Text.literal("§7No clans exist."), false); return 1; }

        source.sendFeedback(() -> Text.literal("§6§l--- Clans (" + clans.size() + ") ---"), false);
        for (Clan clan : clans) {
            int members = db.getMemberCount(clan.getId());
            source.sendFeedback(() -> Text.literal(String.format(
                    "  §6%s §8[§e%s§8] §7- %d members, leader: %s%s",
                    clan.getName(), clan.getTag(), members, clan.getLeaderUuid().toString().substring(0, 8),
                    clan.getShopNumber() != null ? ", shop #" + clan.getShopNumber() : "")), false);
        }
        return 1;
    }

    private static int adminClanInfo(ServerCommandSource source, String nameOrTag) {
        DatabaseManager db = DatabaseManager.getInstance();
        Clan foundClan = db.getClanByName(nameOrTag);
        if (foundClan == null) foundClan = db.getClanByTag(nameOrTag);
        if (foundClan == null) { source.sendError(Text.literal("§cClan not found: " + nameOrTag)); return 0; }
        final Clan clan = foundClan;

        var members = db.getClanMembers(clan.getId());
        String leaderName = members.stream()
                .filter(m -> m.getPlayerUuid().equals(clan.getLeaderUuid()))
                .map(ClanMember::getPlayerName)
                .findFirst().orElse("Unknown");

        source.sendFeedback(() -> Text.literal("§6§l--- " + clan.getName() + " [" + clan.getTag() + "] ---"), false);
        source.sendFeedback(() -> Text.literal("§7Leader: §e" + leaderName), false);
        source.sendFeedback(() -> Text.literal("§7Created: §f" + clan.getCreatedAt().toString().substring(0, 10)), false);
        source.sendFeedback(() -> Text.literal("§7Members: §f" + members.size()), false);
        source.sendFeedback(() -> Text.literal("§7Shop: §f" + (clan.getShopNumber() != null ? "#" + clan.getShopNumber() : "None")), false);
        if (clan.getDeletionScheduledAt() != null) {
            source.sendFeedback(() -> Text.literal("§c⚠ Deletion scheduled: " + clan.getDeletionScheduledAt().toString().substring(0, 10)), false);
        }
        source.sendFeedback(() -> Text.literal("§7Members list:"), false);
        for (var member : members) {
            boolean isLeader = member.getPlayerUuid().equals(clan.getLeaderUuid());
            source.sendFeedback(() -> Text.literal("  " + (isLeader ? "§6♛ " : "§7- ") + "§f" + member.getPlayerName() + " §8(" + member.getPlayerUuid().toString().substring(0, 8) + "...)"), false);
        }
        return 1;
    }
}
