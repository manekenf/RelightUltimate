package ua.selectedproject.core;

import ua.selectedproject.core.config.CoreConfig;
import ua.selectedproject.core.config.CoreLocalization;
import ua.selectedproject.core.data.DatabaseManager;
import ua.selectedproject.core.economy.CoinItems;
import ua.selectedproject.core.network.VelocityHelper;
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Core commands: /hub, /sethubspawn, /coins, /discord, /resourceworld, /selectedcore
 */
public class CoreCommands {
    /** /discord link rate limit: at most this many attempts per hour per UUID. */
    private static final int LINK_RATE_LIMIT = 5;
    private static final long LINK_RATE_WINDOW_MS = 60L * 60L * 1000L;
    private static final Map<UUID, Deque<Long>> linkAttempts = new HashMap<>();

    public static void register() {
        CommandRegistrationCallback.EVENT.register(CoreCommands::registerAll);
    }

    private static void registerAll(CommandDispatcher<ServerCommandSource> dispatcher,
                                     CommandRegistryAccess registryAccess,
                                     CommandManager.RegistrationEnvironment environment) {

        // /hub
        dispatcher.register(literal("hub")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    CoreConfig config = CoreConfig.getInstance();
                    if (config.enableHubDimension) {
                        ua.selectedproject.core.dimension.HubDimension.teleportToHub(player);
                        player.sendMessage(Text.literal("§aТелепортовано у хаб."));
                    } else {
                        VelocityHelper.sendToHub(player);
                        player.sendMessage(Text.literal("§aПереміщення на хаб сервер..."));
                    }
                    return 1;
                })
        );

        // /sethubspawn
        dispatcher.register(literal("sethubspawn")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    net.minecraft.util.math.BlockPos pos = player.getBlockPos();
                    ua.selectedproject.core.dimension.HubDimension.setHubSpawnPos(pos);
                    ctx.getSource().sendFeedback(
                            () -> Text.literal(String.format("§aHub spawn set to %d, %d, %d", pos.getX(), pos.getY(), pos.getZ())),
                            true);
                    return 1;
                })
        );

        // /joinresource
        dispatcher.register(literal("joinresource")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    var scheduler = ua.selectedproject.core.resourceworld.ResourceWorldScheduler.getInstance();
                    if (!scheduler.isOpen()) {
                        player.sendMessage(Text.literal("§cСвіт ресурсів зачинений!"));
                        return 0;
                    }
                    VelocityHelper.sendToResourceWorld(player);
                    player.sendMessage(Text.literal("§aПереміщення у світ ресурсів..."));
                    return 1;
                })
        );

        // /coins
        dispatcher.register(literal("coins")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("give")
                        .then(argument("player", StringArgumentType.word())
                                .then(argument("tier", IntegerArgumentType.integer(1, 3))
                                        .then(argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    String playerName = StringArgumentType.getString(ctx, "player");
                                                    int tier = IntegerArgumentType.getInteger(ctx, "tier");
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    return handleGiveCoins(ctx.getSource(), playerName, tier, amount);
                                                })))))
                .then(literal("check")
                        .then(argument("player", StringArgumentType.word())
                                .executes(ctx -> handleCheckCoins(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
        );

        // /discord link <code>
        dispatcher.register(literal("discord")
                .then(literal("link")
                        .then(argument("code", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    String code = StringArgumentType.getString(ctx, "code");
                                    return handleDiscordLink(player, code);
                                })))
        );

        // /resourceworld
        dispatcher.register(literal("resourceworld")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("open")
                        .then(argument("minutes", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                                    return handleResourceOpen(ctx.getSource(), minutes);
                                })))
                .then(literal("close")
                        .executes(ctx -> handleResourceClose(ctx.getSource())))
                .then(literal("status")
                        .executes(ctx -> handleResourceStatus(ctx.getSource())))
                .then(literal("nether")
                        .then(argument("state", StringArgumentType.word())
                                .executes(ctx -> setResourceDimension(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "state"), true))))
                .then(literal("end")
                        .then(argument("state", StringArgumentType.word())
                                .executes(ctx -> setResourceDimension(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "state"), false))))
        );

        // /selectedcore setleaderboard/setportal
        dispatcher.register(literal("selectedcore")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("setportal")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            net.minecraft.util.math.Vec3d pos = player.getPos();
                            ua.selectedproject.core.resourceworld.ResourceWorldScheduler.getInstance().setPortalHologramPos(pos);
                            ctx.getSource().sendFeedback(() -> Text.literal(String.format("§aPortal hologram set at %.1f, %.1f, %.1f", pos.x, pos.y, pos.z)), true);
                            return 1;
                        }))
        );

        SelectedCore.LOGGER.info("Core commands registered");
    }

    // ==================== HANDLERS ====================

    private static int handleGiveCoins(ServerCommandSource source, String playerName, int tier, int amount) {
        CoreConfig config = CoreConfig.getInstance();
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(playerName);
        if (target == null) { source.sendError(Text.literal("§cPlayer not found.")); return 0; }

        ItemStack coinStack = switch (tier) {
            case 1 -> new ItemStack(CoinItems.COIN_TIER_1, amount);
            case 2 -> new ItemStack(CoinItems.COIN_TIER_2, amount);
            case 3 -> new ItemStack(CoinItems.COIN_TIER_3, amount);
            default -> ItemStack.EMPTY;
        };
        if (coinStack.isEmpty()) return 0;
        if (!target.getInventory().insertStack(coinStack) && !coinStack.isEmpty()) {
            // Inventory was (partially) full — drop whatever didn't fit at the player's feet.
            target.dropItem(coinStack, false);
        }

        String coinName = switch (tier) {
            case 1 -> config.coinTier1DisplayName;
            case 2 -> config.coinTier2DisplayName;
            case 3 -> config.coinTier3DisplayName;
            default -> "Unknown";
        };
        CoreLocalization lang = CoreLocalization.getInstance();
        source.sendFeedback(() -> Text.literal(lang.get("coins.give", amount, coinName, playerName)), true);
        return 1;
    }

    private static int handleCheckCoins(ServerCommandSource source, String playerName) {
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(playerName);
        if (target == null) { source.sendError(Text.literal("§cPlayer not found.")); return 0; }

        int[] counts = CoinItems.countPlayerCoins(target);
        long totalValue = CoinItems.calculateValue(counts[0], counts[1], counts[2]);
        CoreConfig config = CoreConfig.getInstance();
        source.sendFeedback(() -> Text.literal(String.format(
                "§e%s§r coins: %s×%d, %s×%d, %s×%d (total: §6%d§r)",
                playerName, config.coinTier1DisplayName, counts[0],
                config.coinTier2DisplayName, counts[1],
                config.coinTier3DisplayName, counts[2], totalValue)), false);
        return 1;
    }

    private static int handleDiscordLink(ServerPlayerEntity player, String code) {
        DatabaseManager db = DatabaseManager.getInstance();
        CoreLocalization lang = CoreLocalization.getInstance();

        if (!checkLinkRateLimit(player.getUuid())) {
            player.sendMessage(Text.literal(lang.get("discord.link.rate_limited")));
            return 0;
        }

        Long discordId = db.getDiscordIdByCode(code);
        if (discordId == null) {
            player.sendMessage(Text.literal(lang.get("discord.link.invalid_code")));
            return 0;
        }

        // Atomic claim: delete the code first; if 0 rows affected, another player already used it.
        if (!db.consumeLinkCode(code)) {
            player.sendMessage(Text.literal(lang.get("discord.link.invalid_code")));
            return 0;
        }

        var link = db.createLink(discordId, player.getUuid(), player.getName().getString());
        if (link == null) {
            // UNIQUE collision (already linked) — code was consumed but link not created.
            player.sendMessage(Text.literal(lang.get("discord.link.already_linked")));
            return 0;
        }
        player.sendMessage(Text.literal(lang.get("discord.link.success")));
        return 1;
    }

    private static synchronized boolean checkLinkRateLimit(UUID uuid) {
        long now = System.currentTimeMillis();
        Deque<Long> attempts = linkAttempts.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        while (!attempts.isEmpty() && now - attempts.peekFirst() > LINK_RATE_WINDOW_MS) {
            attempts.pollFirst();
        }
        if (attempts.size() >= LINK_RATE_LIMIT) return false;
        attempts.addLast(now);
        return true;
    }

    private static int setResourceDimension(ServerCommandSource source, String state, boolean nether) {
        boolean enable;
        if (state.equalsIgnoreCase("enable") || state.equalsIgnoreCase("on") || state.equalsIgnoreCase("true")) {
            enable = true;
        } else if (state.equalsIgnoreCase("disable") || state.equalsIgnoreCase("off") || state.equalsIgnoreCase("false")) {
            enable = false;
        } else {
            source.sendError(Text.literal("§cExpected 'enable' or 'disable', got '" + state + "'"));
            return 0;
        }
        var scheduler = ua.selectedproject.core.resourceworld.ResourceWorldScheduler.getInstance();
        if (nether) scheduler.setNetherEnabled(enable);
        else scheduler.setEndEnabled(enable);
        source.sendFeedback(() -> Text.literal("§e" + (nether ? "Nether" : "End") +
                " " + (enable ? "enabled" : "disabled")), true);
        return 1;
    }

    private static int handleResourceOpen(ServerCommandSource source, int minutes) {
        var scheduler = ua.selectedproject.core.resourceworld.ResourceWorldScheduler.getInstance();
        scheduler.open(minutes);
        CoreLocalization lang = CoreLocalization.getInstance();
        String prefix = lang.get("prefix");
        Text announcement = Text.literal(lang.get("resource.opened", prefix));
        for (ServerPlayerEntity p : source.getServer().getPlayerManager().getPlayerList()) {
            p.sendMessage(announcement);
        }
        source.sendFeedback(() -> Text.literal("§aResource world opened for " + minutes + " minutes."), true);
        var bot = ua.selectedproject.core.discord.DiscordBot.getInstance();
        if (bot != null && bot.isRunning()) bot.notifyResourceWorldOpened(minutes + " хвилин");
        return 1;
    }

    private static int handleResourceClose(ServerCommandSource source) {
        ua.selectedproject.core.resourceworld.ResourceWorldScheduler.getInstance().forceClose(source.getServer());
        var bot = ua.selectedproject.core.discord.DiscordBot.getInstance();
        if (bot != null && bot.isRunning()) bot.notifyResourceWorldClosed();
        source.sendFeedback(() -> Text.literal("§cResource world force-closed."), true);
        return 1;
    }

    private static int handleResourceStatus(ServerCommandSource source) {
        String status = ua.selectedproject.core.resourceworld.ResourceWorldScheduler.getInstance().getStatus();
        source.sendFeedback(() -> Text.literal(status), false);
        return 1;
    }
}
