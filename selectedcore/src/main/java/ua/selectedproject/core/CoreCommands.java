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

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Core commands: /hub, /sethubspawn, /coins, /discord, /resourceworld, /selectedcore
 */
public class CoreCommands {

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
                                .executes(ctx -> {
                                    boolean enable = StringArgumentType.getString(ctx, "state").equalsIgnoreCase("enable");
                                    ua.selectedproject.core.resourceworld.ResourceWorldScheduler.getInstance().setNetherEnabled(enable);
                                    ctx.getSource().sendFeedback(() -> Text.literal("§eNether " + (enable ? "enabled" : "disabled")), true);
                                    return 1;
                                })))
                .then(literal("end")
                        .then(argument("state", StringArgumentType.word())
                                .executes(ctx -> {
                                    boolean enable = StringArgumentType.getString(ctx, "state").equalsIgnoreCase("enable");
                                    ua.selectedproject.core.resourceworld.ResourceWorldScheduler.getInstance().setEndEnabled(enable);
                                    ctx.getSource().sendFeedback(() -> Text.literal("§eEnd " + (enable ? "enabled" : "disabled")), true);
                                    return 1;
                                })))
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
        target.getInventory().insertStack(coinStack);

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
        Long discordId = db.getDiscordIdByCode(code);
        if (discordId == null) { player.sendMessage(Text.literal(lang.get("discord.link.invalid_code"))); return 0; }
        db.createLink(discordId, player.getUuid(), player.getName().getString());
        db.deleteLinkCode(code);
        player.sendMessage(Text.literal(lang.get("discord.link.success")));
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
