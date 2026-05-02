package ua.selectedproject.police;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ua.selectedproject.police.data.PlayerPvpStatus;
import ua.selectedproject.police.data.PrisonZone;
import ua.selectedproject.police.placeholder.PapiExpansion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PoliceCommands {

    private static final long PVP_COOLDOWN_SECONDS = 7L * 24 * 3600; // 1 week
    static final int MAX_ZONES_PER_OFFICER = 10;
    static final int MAX_ZONE_VOLUME = 50_000;

    public static void register() {
        CommandRegistrationCallback.EVENT.register(PoliceCommands::registerAll);
    }

    private static void registerAll(CommandDispatcher<ServerCommandSource> dispatcher,
                                    CommandRegistryAccess registryAccess,
                                    CommandManager.RegistrationEnvironment environment) {
        registerPvpCommand(dispatcher);
        registerPoliceCommand(dispatcher);
        registerAPoliceCommand(dispatcher);
    }

    // ==================== /pvp ====================

    private static void registerPvpCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("pvp")
                .then(literal("on").executes(ctx -> handlePvpToggle(ctx.getSource(), true)))
                .then(literal("off").executes(ctx -> handlePvpToggle(ctx.getSource(), false)))
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    PoliceDatabase db = PoliceDatabase.getInstance();
                    if (db == null) return 0;
                    boolean pvp = db.isPvp(player.getUuid());
                    player.sendMessage(Text.literal("§7Ваш режим: " + (pvp ? "§cPVP ⚔" : "§aPVE 🛡")));
                    return 1;
                })
        );
    }

    private static int handlePvpToggle(ServerCommandSource source, boolean wantPvp) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }

        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) {
            source.sendError(Text.literal("§cСистема недоступна."));
            return 0;
        }

        PlayerPvpStatus status = db.getPvpStatus(player.getUuid());
        if (status == null) {
            source.sendError(Text.literal("§cПомилка бази даних."));
            return 0;
        }

        if (status.isPvp() == wantPvp) {
            String mode = wantPvp ? "PVP" : "PVE";
            player.sendMessage(Text.literal("§7Ви вже в режимі " + mode + "."));
            return 0;
        }

        // Check cooldown
        long secondsSinceToggle = Instant.now().getEpochSecond() - status.lastToggle().getEpochSecond();
        if (secondsSinceToggle < PVP_COOLDOWN_SECONDS) {
            long remaining = PVP_COOLDOWN_SECONDS - secondsSinceToggle;
            long days = remaining / 86400, hours = (remaining % 86400) / 3600, mins = (remaining % 3600) / 60;
            player.sendMessage(Text.literal(String.format(
                    "§cВи зможете змінити режим через %d д. %d год. %d хв.", days, hours, mins)));
            return 0;
        }

        db.setPvp(player.getUuid(), wantPvp);
        PvpEventHandler.applyPvpTeam(player, wantPvp);
        PapiExpansion.notifyChange(player.getUuid());

        if (wantPvp) {
            player.sendMessage(Text.literal("§c⚔ Ви увійшли в режим PVP. Ви можете бути атаковані!"));
        } else {
            player.sendMessage(Text.literal("§a🛡 Ви увійшли в режим PVE. Ви захищені від атак."));
        }
        return 1;
    }

    // ==================== /police ====================

    private static void registerPoliceCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("police")
                .requires(source -> {
                    try {
                        return source.getPlayerOrThrow() != null;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .then(literal("on").executes(ctx -> handlePoliceOn(ctx.getSource())))
                .then(literal("off").executes(ctx -> handlePoliceOff(ctx.getSource())))
                .then(literal("prison")
                        .then(literal("home")
                                .then(argument("zoneId", IntegerArgumentType.integer(1))
                                        .executes(ctx -> handlePrisonHome(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "zoneId")))))
                        .then(literal("jail")
                                .then(argument("nick", StringArgumentType.word())
                                        .executes(ctx -> handlePrisonJail(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "nick"), 15))
                                        .then(argument("minutes", IntegerArgumentType.integer(1, 60))
                                                .executes(ctx -> handlePrisonJail(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "nick"),
                                                        IntegerArgumentType.getInteger(ctx, "minutes"))))))
                        .then(literal("list")
                                .executes(ctx -> handlePrisonList(ctx.getSource())))
                        .then(literal("set")
                                .executes(ctx -> handlePrisonSetMode(ctx.getSource())))
                        .then(literal("clean")
                                .then(literal("last")
                                        .executes(ctx -> handlePoliceClean(ctx.getSource())))))
                .then(literal("spawn")
                        .then(argument("nick", StringArgumentType.word())
                                .executes(ctx -> handlePoliceSpawn(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "nick")))))
                .then(literal("release")
                        .then(argument("nick", StringArgumentType.word())
                                .executes(ctx -> handlePoliceRelease(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "nick")))))
                .then(literal("clean")
                        .then(literal("last")
                                .executes(ctx -> handlePoliceClean(ctx.getSource()))))
        );
    }

    private static int handlePoliceOn(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }

        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) {
            source.sendError(Text.literal("§cСистема недоступна."));
            return 0;
        }

        if (!db.isPvp(player.getUuid())) {
            player.sendMessage(Text.literal("§cПоліцейські можуть бути тільки гравці з увімкненим PVP."));
            return 0;
        }
        if (db.isPolice(player.getUuid())) {
            player.sendMessage(Text.literal("§7Ви вже є поліцейським."));
            return 0;
        }

        db.setPolice(player.getUuid(), true);
        player.sendMessage(Text.literal("§b👮 Ви тепер поліцейський. Використовуйте /police off щоб вийти."));
        return 1;
    }

    private static int handlePoliceOff(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }

        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) {
            source.sendError(Text.literal("§cСистема недоступна."));
            return 0;
        }

        if (!db.isPolice(player.getUuid())) {
            player.sendMessage(Text.literal("§7Ви не є поліцейським."));
            return 0;
        }

        db.setPolice(player.getUuid(), false);
        player.sendMessage(Text.literal("§7Ви більше не поліцейський."));
        return 1;
    }

    private static int handlePrisonSetMode(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }

        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) {
            source.sendError(Text.literal("§cСистема недоступна."));
            return 0;
        }

        if (!db.isPolice(player.getUuid())) {
            player.sendMessage(Text.literal("§cТільки поліцейські можуть створювати зони в'язниці."));
            return 0;
        }

        int currentCount = db.countZonesByOwner(player.getUuid());
        if (currentCount >= MAX_ZONES_PER_OFFICER) {
            player.sendMessage(Text.literal("§cВи досягли ліміту (" + MAX_ZONES_PER_OFFICER + ") зон в'язниці."));
            return 0;
        }

        PrisonSelectionHandler.enterSelectionMode(player);
        return 1;
    }

    private static int handlePoliceSpawn(ServerCommandSource source, String nick) {
        ServerPlayerEntity officer;
        try {
            officer = source.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }

        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) {
            source.sendError(Text.literal("§cСистема недоступна."));
            return 0;
        }

        if (!db.isPolice(officer.getUuid())) {
            officer.sendMessage(Text.literal("§cТільки поліцейські можуть встановлювати місця появи."));
            return 0;
        }

        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(nick);
        if (target == null) {
            source.sendError(Text.literal("§cГравець " + nick + " не в мережі."));
            return 0;
        }

        UUID targetUuid = target.getUuid();
        if (!db.isCaught(targetUuid) && !db.isBound(targetUuid)) {
            source.sendError(Text.literal("§c" + nick + " не перебуває під вартою."));
            return 0;
        }

        // Use officer's current position as the spawn point
        String world = officer.getServerWorld().getRegistryKey().getValue().toString();
        double x = officer.getX(), y = officer.getY(), z = officer.getZ();
        db.setPrisonSpawn(targetUuid, world, x, y, z);

        officer.sendMessage(Text.literal(String.format(
                "§aМісце появи для §e%s §aвстановлено: §f%.0f, %.0f, %.0f", nick, x, y, z)));
        return 1;
    }

    private static int handlePoliceRelease(ServerCommandSource source, String nick) {
        ServerPlayerEntity officer;
        try {
            officer = source.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }

        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) {
            source.sendError(Text.literal("§cСистема недоступна."));
            return 0;
        }

        if (!db.isPolice(officer.getUuid())) {
            officer.sendMessage(Text.literal("§cТільки поліцейські можуть звільняти гравців."));
            return 0;
        }

        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(nick);
        if (target == null) {
            source.sendError(Text.literal("§cГравець " + nick + " не в мережі."));
            return 0;
        }

        db.releaseFromCustody(target.getUuid());
        db.setCriminal(target.getUuid(), false);
        PvpEventHandler.applyCriminalTag(target, false);

        target.sendMessage(Text.literal("§aВас звільнено поліцейським."));
        officer.sendMessage(Text.literal("§aГравця §e" + nick + "§a звільнено."));
        return 1;
    }

    private static int handlePoliceClean(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }

        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) {
            source.sendError(Text.literal("§cСистема недоступна."));
            return 0;
        }

        List<PrisonZone> zones = db.getPrisonZonesByOwner(player.getUuid());
        if (zones.isEmpty()) {
            player.sendMessage(Text.literal("§7У вас немає зон в'язниці."));
            return 0;
        }

        if (db.removeLastZoneByOwner(player.getUuid())) {
            PrisonZone last = zones.get(zones.size() - 1);
            player.sendMessage(Text.literal("§aОстанню зону в'язниці #" + last.id() + " видалено."));
        } else {
            source.sendError(Text.literal("§cПомилка видалення зони."));
            return 0;
        }
        return 1;
    }

    private static int handlePrisonHome(ServerCommandSource source, int zoneId) {
        ServerPlayerEntity officer;
        try { officer = source.getPlayerOrThrow(); } catch (Exception e) { return 0; }
        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) { source.sendError(Text.literal("§cСистема недоступна.")); return 0; }

        PrisonZone zone = db.getZoneById(zoneId);
        if (zone == null) {
            officer.sendMessage(Text.literal("§cЗона #" + zoneId + " не знайдена."));
            return 0;
        }
        if (!zone.ownerUuid().equals(officer.getUuid())) {
            officer.sendMessage(Text.literal("§cЦе не ваша зона."));
            return 0;
        }
        String world = officer.getServerWorld().getRegistryKey().getValue().toString();
        if (!world.equals(zone.world())) {
            officer.sendMessage(Text.literal("§cВи в іншому світі.")); return 0;
        }
        if (!zone.contains(world, officer.getX(), officer.getY(), officer.getZ())) {
            officer.sendMessage(Text.literal("§cСтаньте всередині зони #" + zoneId + ".")); return 0;
        }
        if (db.setZoneHome(zoneId, officer.getX(), officer.getY(), officer.getZ())) {
            officer.sendMessage(Text.literal(String.format(
                    "§aТочку появи зони #%d встановлено: §f%.2f, %.2f, %.2f",
                    zoneId, officer.getX(), officer.getY(), officer.getZ())));
            return 1;
        }
        return 0;
    }

    private static int handlePrisonJail(ServerCommandSource source, String nick, int minutes) {
        ServerPlayerEntity officer;
        try { officer = source.getPlayerOrThrow(); } catch (Exception e) { return 0; }
        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) { source.sendError(Text.literal("§cСистема недоступна.")); return 0; }
        if (!db.isPolice(officer.getUuid())) {
            officer.sendMessage(Text.literal("§cТільки поліцейські.")); return 0;
        }
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(nick);
        if (target == null) { source.sendError(Text.literal("§c" + nick + " не в мережі.")); return 0; }
        UUID tu = target.getUuid();
        if (!db.isCaught(tu) && !db.isLeashed(tu)) {
            source.sendError(Text.literal("§c" + nick + " не під вартою. Спершу прив'яжіть.")); return 0;
        }

        List<PrisonZone> zones = db.getPrisonZonesByOwner(officer.getUuid());
        PrisonZone chosen = null;
        for (PrisonZone z : zones) if (z.homeX() != null) { chosen = z; break; }
        if (chosen == null && !zones.isEmpty()) chosen = zones.get(0);
        if (chosen == null) {
            officer.sendMessage(Text.literal("§cУ вас немає зон. /police prison set")); return 0;
        }

        net.minecraft.util.Identifier wid = net.minecraft.util.Identifier.tryParse(chosen.world());
        if (wid == null) { source.sendError(Text.literal("§cНевалідний світ зони.")); return 0; }
        net.minecraft.server.world.ServerWorld world = source.getServer().getWorld(
                net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, wid));
        if (world == null) { source.sendError(Text.literal("§cСвіт не знайдено.")); return 0; }

        target.teleport(world, chosen.targetX(), chosen.targetY(), chosen.targetZ(),
                java.util.EnumSet.noneOf(net.minecraft.network.packet.s2c.play.PositionFlag.class),
                target.getYaw(), target.getPitch());

        Instant boundUntil = Instant.now().plusSeconds(minutes * 60L);
        db.setBound(tu, true, boundUntil, officer.getUuid());
        db.setLeashed(tu, false, null);
        db.setCaught(tu, true, officer.getUuid());
        db.setPrisonSpawn(tu, chosen.world(), chosen.targetX(), chosen.targetY(), chosen.targetZ());
        PoliceEventHandler.storeBoundPosition(tu,
                new net.minecraft.util.math.Vec3d(chosen.targetX(), chosen.targetY(), chosen.targetZ()));

        officer.sendMessage(Text.literal(String.format(
                "§a⚖ %s засуджено на %d хв (зона #%d)", nick, minutes, chosen.id())));
        target.sendMessage(Text.literal(String.format("§c⛓ Засуджено на %d хв.", minutes)));
        return 1;
    }

    private static int handlePrisonList(ServerCommandSource source) {
        ServerPlayerEntity p;
        try { p = source.getPlayerOrThrow(); } catch (Exception e) { return 0; }
        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) return 0;
        List<PrisonZone> zones = db.getPrisonZonesByOwner(p.getUuid());
        if (zones.isEmpty()) { p.sendMessage(Text.literal("§7У вас немає зон.")); return 0; }
        p.sendMessage(Text.literal("§a=== Ваші зони ==="));
        for (PrisonZone z : zones) {
            String h = z.homeX() != null
                    ? String.format(" §a(home %.0f,%.0f,%.0f)", z.homeX(), z.homeY(), z.homeZ())
                    : " §c(home не задано)";
            p.sendMessage(Text.literal(String.format("§f#%d §7v=%d%s", z.id(), z.volume(), h)));
        }
        return 1;
    }

    // ==================== /apolice ====================

    private static void registerAPoliceCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("apolice")
                .requires(src -> src.hasPermissionLevel(2))
                .then(literal("release")
                        .then(argument("nick", StringArgumentType.word())
                                .executes(ctx -> handleAdminRelease(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "nick")))))
        );
    }

    private static int handleAdminRelease(ServerCommandSource source, String nick) {
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(nick);
        UUID targetUuid = null;

        if (target != null) {
            targetUuid = target.getUuid();
        } else {
            source.sendError(Text.literal("§cГравець " + nick + " не в мережі."));
            return 0;
        }

        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) {
            source.sendError(Text.literal("§cСистема недоступна."));
            return 0;
        }

        db.releaseFromCustody(targetUuid);
        db.setCriminal(targetUuid, false);
        PvpEventHandler.applyCriminalTag(target, false);

        target.sendMessage(Text.literal("§aВас звільнено адміністратором."));
        source.sendFeedback(() -> Text.literal("§aГравця §e" + nick + "§a звільнено з-під варти."), true);
        return 1;
    }
}
