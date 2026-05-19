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
import ua.selectedproject.police.network.BindingNetworking;
import ua.selectedproject.police.network.BindingSyncPayload;

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

    /** Drop the in-memory frozen position for a player (e.g. on disconnect). */
    public static void clearBoundPosition(UUID uuid) {
        boundPositions.remove(uuid);
    }

    public static void register() {
        registerTickEvents();
        registerInteractionBlocking();
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
                        BindingNetworking.broadcast(server, uuid, BindingSyncPayload.State.NONE, null);
                        boundPositions.remove(uuid);
                        player.sendMessage(Text.literal("§aВас звільнено. Час утримання минув."));
                        continue;
                    }

                    Vec3d frozen = boundPositions.get(uuid);
                    if (frozen == null) {
                        // Restore from prison spawn if available, otherwise use current position.
                        // Without this fall-back, after a server restart the player is anchored
                        // wherever they happened to log in.
                        if (status.spawnWorld() != null) {
                            frozen = new Vec3d(status.spawnX(), status.spawnY(), status.spawnZ());
                        } else {
                            frozen = player.getPos();
                        }
                        boundPositions.put(uuid, frozen);
                    } else if (doDistanceCheck && player.squaredDistanceTo(frozen) > 0.25) {
                        player.requestTeleport(frozen.x, frozen.y, frozen.z);
                    }

                    // Show remaining time on actionbar
                    if (boundUntil != null) {
                        long remaining = Math.max(0, boundUntil.getEpochSecond() - Instant.now().getEpochSecond());
                        long mins = remaining / 60, secs = remaining % 60;
                        player.sendMessage(Text.literal(
                                String.format("§c⛓ Прив'язаний: §f%d хв. %02d с.", mins, secs)), true);
                    }
                } else if (status.isLeashed()) {
                    // Leashed (but not bound): show "being led" actionbar.
                    UUID lt = status.leashedTo();
                    if (lt != null) {
                        ServerPlayerEntity officer = server.getPlayerManager().getPlayer(lt);
                        if (officer != null) {
                            player.sendMessage(Text.literal(
                                    "§c⛓ Вас веде §e" + officer.getName().getString()), true);
                        }
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

        // Single block-break listener that handles both restraint check AND prison-zone protection
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!(world instanceof ServerWorld)) return true;
            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db == null || !(player instanceof ServerPlayerEntity sp)) return true;

            UUID uuid = sp.getUuid();
            if (isRestrained(db, uuid)) {
                sp.sendMessage(Text.literal("§cВи не можете руйнувати, поки прив'язані."), true);
                return false;
            }

            // Caught players (post-jail, no longer bound but still serving) cannot
            // break blocks inside ANY prison zone.
            if (db.isCaught(uuid) || db.isBound(uuid)) {
                String worldName = world.getRegistryKey().getValue().toString();
                for (PrisonZone zone : db.getAllPrisonZones()) {
                    if (zone.contains(worldName, pos.getX(), pos.getY(), pos.getZ())) {
                        sp.sendMessage(Text.literal("§cВи не можете руйнувати блоки у зоні в'язниці."), true);
                        return false;
                    }
                }
            }

            return true;
        });

        // Block placement protection inside prison zones (caught/bound players)
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            PoliceDatabase db = PoliceDatabase.getInstance();
            if (db == null) return ActionResult.PASS;
            if (!db.isCaught(sp.getUuid()) && !db.isBound(sp.getUuid())) return ActionResult.PASS;

            // Only intervene if the player's hand stack is a placeable block.
            net.minecraft.item.ItemStack stack = sp.getStackInHand(hand);
            if (!(stack.getItem() instanceof net.minecraft.item.BlockItem)) return ActionResult.PASS;

            // Compute the position the new block would be placed at (offset by hit side).
            net.minecraft.util.math.BlockPos placeAt = hitResult.getBlockPos().offset(hitResult.getSide());
            String worldName = world.getRegistryKey().getValue().toString();
            for (PrisonZone zone : db.getAllPrisonZones()) {
                if (zone.contains(worldName, placeAt.getX(), placeAt.getY(), placeAt.getZ())) {
                    sp.sendMessage(Text.literal("§cВи не можете розміщувати блоки у зоні в'язниці."), true);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });
    }

    private static boolean isRestrained(PoliceDatabase db, UUID uuid) {
        return db.isBound(uuid) || db.isLeashed(uuid);
    }
}
