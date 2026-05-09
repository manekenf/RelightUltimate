package ua.selectedproject.police;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.selectedproject.police.network.BindingNetworking;
import ua.selectedproject.police.network.BindingSyncPayload;

import java.time.Instant;
import java.util.*;

/**
 * Handles PVP/PVE damage protection, criminal tracking, and police kill detection.
 */
public class PvpEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedPolice/PvpEvents");

    /**
     * Tracks recent attacks on PVE players: attacker UUID → list of epoch seconds.
     */
    private static final Map<UUID, List<Long>> attackTimestamps = new HashMap<>();

    /**
     * Players pending binding on next respawn: criminal UUID → officer UUID.
     */
    static final Map<UUID, UUID> pendingBind = new HashMap<>();

    private static final java.util.Set<UUID> deferredTagApply =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    public static void register() {
        registerJoinHandler();
        registerDeferredTagApply();
        // Cancel PVE player damage from other players; track criminal logic
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity victim)) return true;

            ServerPlayerEntity attacker = null;
            if (source.getAttacker() instanceof ServerPlayerEntity p) attacker = p;

            if (attacker == null || attacker == victim) return true;

            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db == null) return true;

            // PVE protection
            if (!db.isPvp(victim.getUuid())) {
                // Victim is PVE — only track if attacker is PVP
                if (db.isPvp(attacker.getUuid())) {
                    trackCriminalAttack(attacker, victim, db);
                }
                return false; // Cancel damage, effects from other sources still apply
            }

            return true;
        });

        // Detect when police kills a criminal
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killedEntity) -> {
            if (!(killer instanceof ServerPlayerEntity officer)) return;
            if (!(killedEntity instanceof ServerPlayerEntity criminal)) return;

            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db == null) return;
            if (!db.isPolice(officer.getUuid())) return;
            if (!db.isCriminal(criminal.getUuid())) return;

            // Mark criminal for binding on next respawn
            pendingBind.put(criminal.getUuid(), officer.getUuid());
            officer.sendMessage(Text.literal(
                    "§a⚖ Злочинець §e" + criminal.getName().getString() +
                            "§a вбитий. Очікуйте сповіщення про місце появи."));
            LOGGER.info("Officer {} killed criminal {}", officer.getName().getString(), criminal.getName().getString());
        });

        // On respawn, apply bound status if flagged
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            UUID uuid = newPlayer.getUuid();
            deferredTagApply.add(uuid);

            UUID officerUuid = pendingBind.remove(uuid);
            if (officerUuid == null) return;

            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db == null) return;

            Instant boundUntil = Instant.now().plusSeconds(15 * 60);
            db.setBound(uuid, true, boundUntil, officerUuid);
            PoliceEventHandler.storeBoundPosition(uuid, newPlayer.getPos());

            MinecraftServer server = newPlayer.getServer();
            BindingNetworking.broadcast(server, uuid, BindingSyncPayload.State.BOUND);

            if (server != null) {
                ServerPlayerEntity officer = server.getPlayerManager().getPlayer(officerUuid);
                if (officer != null) {
                    double x = newPlayer.getX(), y = newPlayer.getY(), z = newPlayer.getZ();
                    officer.sendMessage(Text.literal(String.format(
                            "§e%s §aз'явився на §f%.0f, %.0f, %.0f",
                            newPlayer.getName().getString(), x, y, z)));
                }
            }

            newPlayer.sendMessage(Text.literal("§c⛓ Ви прив'язані на 15 хвилин. Ви не можете рухатись або взаємодіяти."));
            LOGGER.info("Criminal {} is now bound until {}", newPlayer.getName().getString(), boundUntil);
        });
    }

    private static void trackCriminalAttack(ServerPlayerEntity attacker, ServerPlayerEntity victim, PoliceDatabase db) {
        UUID id = attacker.getUuid();
        if (db.isCriminal(id)) return; // Already criminal, nothing to escalate

        long now = Instant.now().getEpochSecond();

        List<Long> times = attackTimestamps.computeIfAbsent(id, k -> new ArrayList<>());
        times.add(now);
        times.removeIf(t -> now - t > 30); // Keep only last 30 seconds

        int count = times.size();
        if (count == 2) {
            attacker.sendMessage(Text.literal("§e⚠ Попередження: ще один удар по мирному гравцю — і ви станете злочинцем!"));
        } else if (count >= 3) {
            times.clear();
            db.setCriminal(id, true); // sets criminal_until = now + 30 min
            applyCriminalTag(attacker, true);
            attacker.sendMessage(Text.literal("§c⚠ Ви стали злочинцем на 30 хвилин через напад на мирного гравця!"));
            victim.sendMessage(Text.literal("§e" + attacker.getName().getString() + " §cтепер злочинець."));
            LOGGER.info("{} became a criminal for 30 minutes", attacker.getName().getString());
        }
    }

    /**
     * Apply or remove the criminal scoreboard team tag (shows red prefix above head).
     */
    public static void applyCriminalTag(ServerPlayerEntity player, boolean isCriminal) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        Scoreboard scoreboard = server.getScoreboard();
        Team team = scoreboard.getTeam("sp_criminal");
        if (team == null) team = scoreboard.addTeam("sp_criminal");
        team.setPrefix(Text.literal("§c[злочинець] "));   // tmp text
        team.setColor(Formatting.RED);

        String playerName = player.getNameForScoreboard();
        if (isCriminal) {
            Team existing = scoreboard.getScoreHolderTeam(playerName);
            if (existing != null && existing != team) {
                scoreboard.removeScoreHolderFromTeam(playerName, existing);
            }
            scoreboard.addScoreHolderToTeam(playerName, team);
        } else {
            Team existing = scoreboard.getScoreHolderTeam(playerName);
            if (existing == team) {
                scoreboard.removeScoreHolderFromTeam(playerName, team);
            }
            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db != null) {
                applyPvpTeam(player, db.isPvp(player.getUuid()));
            }
        }
    }

    /**
     * Assign the player to the sp_pvp or sp_pve scoreboard team.
     * Skipped if the player is currently criminal (sp_criminal takes priority).
     */
    public static void applyPvpTeam(ServerPlayerEntity player, boolean isPvp) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        Scoreboard scoreboard = server.getScoreboard();

        // Always update prefix in case it changed (e.g. after icon swap during dev).
        Team pveTeam = scoreboard.getTeam("sp_pve");
        if (pveTeam == null) pveTeam = scoreboard.addTeam("sp_pve");
        pveTeam.setPrefix(Text.literal("\uE103 "));   // pve icon
        pveTeam.setColor(Formatting.GREEN);

        Team pvpTeam = scoreboard.getTeam("sp_pvp");
        if (pvpTeam == null) pvpTeam = scoreboard.addTeam("sp_pvp");
        pvpTeam.setPrefix(Text.literal("\uE102 "));   // pvp icon
        pvpTeam.setColor(Formatting.RED);

        Team policeTeam = scoreboard.getTeam("sp_police");
        if (policeTeam == null) policeTeam = scoreboard.addTeam("sp_police");
        policeTeam.setPrefix(Text.literal("§b[поліція] "));   // tmp text
        policeTeam.setColor(Formatting.AQUA);

        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) return;
        if (db.isCriminal(player.getUuid())) return;

        Team target;
        if (db.isPolice(player.getUuid())) target = policeTeam;
        else if (isPvp) target = pvpTeam;
        else target = pveTeam;

        String playerName = player.getNameForScoreboard();
        Team existing = scoreboard.getScoreHolderTeam(playerName);
        if (existing != null && existing != target) {
            scoreboard.removeScoreHolderFromTeam(playerName, existing);
        }
        scoreboard.addScoreHolderToTeam(playerName, target);
    }

    /**
     * Restore criminal tags and assign pve/pvp teams on server ready.
     */
    public static void restoreCriminalTags(MinecraftServer server) {
        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (db.isCriminal(player.getUuid())) {
                applyCriminalTag(player, true);
            } else {
                applyPvpTeam(player, db.isPvp(player.getUuid()));
            }
        }
    }

    private static void registerJoinHandler() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            deferredTagApply.add(handler.player.getUuid());
            ServerPlayerEntity p = handler.player;
            server.execute(() -> BindingNetworking.syncAllToPlayer(p));
        });
    }

    private static void registerDeferredTagApply() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (deferredTagApply.isEmpty()) return;
            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db == null) {
                deferredTagApply.clear();
                return;
            }
            java.util.Set<UUID> snap;
            synchronized (deferredTagApply) {
                snap = new java.util.HashSet<>(deferredTagApply);
                deferredTagApply.clear();
            }
            for (UUID uuid : snap) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                if (p == null) continue;
                if (db.isCriminal(uuid)) applyCriminalTag(p, true);
                else applyPvpTeam(p, db.isPvp(uuid));
            }
        });
    }
}
