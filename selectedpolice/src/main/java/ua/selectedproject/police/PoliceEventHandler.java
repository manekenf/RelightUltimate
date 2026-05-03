package ua.selectedproject.police;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import ua.selectedproject.police.data.PlayerPvpStatus;
import ua.selectedproject.police.data.PrisonZone;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles bound player movement/interaction restrictions, leashing, and prison zone block protection.
 */
public class PoliceEventHandler {
    /** Stored positions for bound players — they cannot move from here. */
    private static final Map<UUID, Vec3d> boundPositions = new HashMap<>();

    private static int tickCounter = 0;

    public static void storeBoundPosition(UUID uuid, Vec3d pos) {
        boundPositions.put(uuid, pos);
    }

    public static void register() {
        registerTickEvents();
        registerInteractionBlocking();
        registerPrisonZoneProtection();
    }

    private static void registerTickEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            boolean doDistanceCheck = (tickCounter % 10 == 0);

            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db == null) return;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID uuid = player.getUuid();
                PlayerPvpStatus status = db.getPvpStatus(uuid);
                if (status == null) continue;

                // Handle bound player: freeze position (every tick for expiry/actionbar)
                if (status.isBound()) {
                    Instant boundUntil = status.boundUntil();
                    if (boundUntil != null && Instant.now().isAfter(boundUntil)) {
                        // Binding expired
                        db.setBound(uuid, false, null, null);
                        boundPositions.remove(uuid);
                        player.sendMessage(Text.literal("§aВас звільнено. Час утримання минув."));
                        continue;
                    }

                    Vec3d frozen = boundPositions.get(uuid);
                    if (frozen == null) {
                        boundPositions.put(uuid, player.getPos());
                    } else if (!status.isBound() && status.isLeashed()) {
                        UUID lt = status.leashedTo();
                        if (lt != null) {
                            ServerPlayerEntity officer = server.getPlayerManager().getPlayer(lt);
                            if (officer != null) {
                                player.sendMessage(Text.literal(
                                        "§c⛓ Вас веде §e" + officer.getName().getString()), true);
                            }
                        }
                    }     else if (doDistanceCheck && player.squaredDistanceTo(frozen) > 0.25) {
                        player.requestTeleport(frozen.x, frozen.y, frozen.z);
                    }

                    // Show remaining time on actionbar
                    if (boundUntil != null) {
                        long remaining = Math.max(0, boundUntil.getEpochSecond() - Instant.now().getEpochSecond());
                        long mins = remaining / 60, secs = remaining % 60;
                        player.sendMessage(Text.literal(
                                String.format("§c⛓ Прив'язаний: §f%d хв. %02d с.", mins, secs)), true);
                    }
                }

                // Handle leashed player: teleport to officer if > 6 blocks away (every 10 ticks)
                if (doDistanceCheck && status.isLeashed()) {
                    UUID leashedTo = status.leashedTo();
                    if (leashedTo == null) {
                        db.setLeashed(uuid, false, null);
                        continue;
                    }
                    ServerPlayerEntity officer = server.getPlayerManager().getPlayer(leashedTo);
                    if (officer == null) {
                        // Officer offline — unleash
                        db.setLeashed(uuid, false, null);
                        player.sendMessage(Text.literal("§eОфіцер вийшов зі серверу. Прив'язку знято."));
                        continue;
                    }
                    if (player.squaredDistanceTo(officer.getPos()) > 36.0) { // 6² = 36
                        player.requestTeleport(officer.getX(), officer.getY(), officer.getZ());
                    }
                }
            }
        });
    }

    private static void registerInteractionBlocking() {
        // Block item use for bound players
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return TypedActionResult.pass(player.getStackInHand(hand));
            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db != null && isRestrained(db, player.getUuid())) {
                player.sendMessage(Text.literal("§cВи не можете використовувати предмети, поки зв'язані."), true);
                return TypedActionResult.fail(player.getStackInHand(hand));
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });

        // Block block interaction for bound players (skip if player is in prison selection mode)
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (player instanceof ServerPlayerEntity sp && PrisonSelectionHandler.isInSelectionMode(sp.getUuid())) {
                return ActionResult.PASS;
            }
            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db != null && isRestrained(db, player.getUuid())) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // Block entity interaction for bound players
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db != null && isRestrained(db, player.getUuid())) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (world.isClient()) return ActionResult.PASS;
            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db != null && isRestrained(db, player.getUuid())) return ActionResult.FAIL;
            return ActionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient()) return ActionResult.PASS;
            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db != null && isRestrained(db, player.getUuid())) return ActionResult.FAIL;
            return ActionResult.PASS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!(world instanceof ServerWorld)) return true;
            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db == null || !(player instanceof ServerPlayerEntity sp)) return true;
            if (isRestrained(db, sp.getUuid())) {
                sp.sendMessage(Text.literal("§cВи не можете руйнувати, поки прив'язані."), true);
                return false;
            }
            return true;
        });
    }

    private static void registerPrisonZoneProtection() {
        // Caught players cannot break blocks inside prison zones
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerWorld)) return true;
            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db == null || !(player instanceof ServerPlayerEntity sp)) return true;
            if (!db.isCaught(sp.getUuid()) && !db.isBound(sp.getUuid())) return true;

            String worldName = world.getRegistryKey().getValue().toString();
            List<PrisonZone> zones = db.getAllPrisonZones();
            for (PrisonZone zone : zones) {
                if (zone.contains(worldName, pos.getX(), pos.getY(), pos.getZ())) {
                    sp.sendMessage(Text.literal("§cВи не можете руйнувати блоки у зоні в'язниці."), true);
                    return false;
                }
            }
            return true;
        });
    }

    private static boolean isRestrained(PoliceDatabase db, UUID uuid) {
        return db.isBound(uuid) || db.isLeashed(uuid);
    }
}
