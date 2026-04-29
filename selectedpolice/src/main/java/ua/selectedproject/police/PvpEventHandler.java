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

import java.time.Instant;
import java.util.*;

/**
 * Handles PVP/PVE damage protection, criminal tracking, and police kill detection.
 */
public class PvpEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedPolice/PvpEvents");

    /** Tracks recent attacks on PVE players: attacker UUID → list of epoch seconds. */
    private static final Map<UUID, List<Long>> attackTimestamps = new HashMap<>();

    /** Players pending binding on next respawn: criminal UUID → officer UUID. */
    static final Map<UUID, UUID> pendingBind = new HashMap<>();

    public static void register() {
        registerJoinHandler();
        // Cancel PVE player damage from other players; track criminal logic
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity victim)) return true;

            ServerPlayerEntity attacker = null;
            if (source.getAttacker() instanceof ServerPlayerEntity p) attacker = p;

            if (attacker == null || attacker == victim) return true;

            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db == null) return true;

            // Police officer hitting a bound player → leash/unleash logic (no damage)
            if (db.isPolice(attacker.getUuid()) && db.isBound(victim.getUuid())) {
                handleOfficerHitsBound(attacker, victim, db);
                return false;
            }

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
            UUID officerUuid = pendingBind.remove(uuid);
            if (officerUuid == null) return;

            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db == null) return;

            Instant boundUntil = Instant.now().plusSeconds(15 * 60);
            db.setBound(uuid, true, boundUntil, officerUuid);
            PoliceEventHandler.storeBoundPosition(uuid, newPlayer.getPos());

            // Notify officer of spawn coordinates
            MinecraftServer server = newPlayer.getServer();
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

    private static void handleOfficerHitsBound(ServerPlayerEntity officer, ServerPlayerEntity criminal, PoliceDatabase db) {
        UUID crimId = criminal.getUuid();
        if (db.isLeashed(crimId)) {
            // Second hit: unleash but keep caught
            db.setLeashed(crimId, false, null);
            db.setCaught(crimId, true, officer.getUuid());
            officer.sendMessage(Text.literal("§e" + criminal.getName().getString() + " §aрозв'язаний і утримується під вартою."));
            criminal.sendMessage(Text.literal("§eВас узяли під варту."));
        } else {
            // First hit: leash them
            db.setLeashed(crimId, true, officer.getUuid());
            officer.sendMessage(Text.literal("§e" + criminal.getName().getString() + " §aприкований до вас (6 блоків)."));
            criminal.sendMessage(Text.literal("§cВас прикували до офіцера."));
        }
    }

    /** Apply or remove the criminal scoreboard team tag (shows red prefix above head). */
    public static void applyCriminalTag(ServerPlayerEntity player, boolean isCriminal) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        Scoreboard scoreboard = server.getScoreboard();
        Team team = scoreboard.getTeam("sp_criminal");
        if (team == null) {
            team = scoreboard.addTeam("sp_criminal");
            team.setPrefix(Text.literal("§c[злочинець] "));
            team.setColor(Formatting.RED);
        }

        String playerName = player.getNameForScoreboard();
        if (isCriminal) {
            scoreboard.addScoreHolderToTeam(playerName, team);
        } else {
            scoreboard.removeScoreHolderFromTeam(playerName, team);
            // Re-assign to pve/pvp team now that criminal tag is gone
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

        Team pveTeam = scoreboard.getTeam("sp_pve");
        if (pveTeam == null) {
            pveTeam = scoreboard.addTeam("sp_pve");
            pveTeam.setPrefix(Text.literal("§a[PVE] "));
            pveTeam.setColor(Formatting.GREEN);
        }

        Team pvpTeam = scoreboard.getTeam("sp_pvp");
        if (pvpTeam == null) {
            pvpTeam = scoreboard.addTeam("sp_pvp");
            pvpTeam.setPrefix(Text.literal("§c[PVP] "));
            pvpTeam.setColor(Formatting.RED);
        }

        // Don't override the criminal tag
        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db != null && db.isCriminal(player.getUuid())) return;

        String playerName = player.getNameForScoreboard();
        scoreboard.addScoreHolderToTeam(playerName, isPvp ? pvpTeam : pveTeam);
    }

    /** Restore criminal tags and assign pve/pvp teams on server ready. */
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
            ServerPlayerEntity player = handler.player;
            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db == null) return;
            if (db.isCriminal(player.getUuid())) {
                applyCriminalTag(player, true);
            } else {
                applyPvpTeam(player, db.isPvp(player.getUuid()));
            }
        });
    }
}
