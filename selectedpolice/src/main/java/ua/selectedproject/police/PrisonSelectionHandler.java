package ua.selectedproject.police;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import ua.selectedproject.police.data.PrisonZone;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two-click prison zone selection mode.
 * /police prison set enters selection mode; right-clicking two blocks defines the zone corners.
 */
public class PrisonSelectionHandler {

    /** Players currently in selection mode. */
    private static final Set<UUID> selectionMode = ConcurrentHashMap.newKeySet();

    /** First corner already chosen, waiting for second click: uuid → first BlockPos. */
    private static final Map<UUID, BlockPos> pendingFirst = new HashMap<>();

    /** Put a player into selection mode and reset any prior pending corner. */
    public static void enterSelectionMode(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        selectionMode.add(uuid);
        pendingFirst.remove(uuid);
        player.sendMessage(Text.literal("§aРежим виділення зони в'язниці активовано. Клацніть ПКМ перший кут."));
    }

    /** Cancel selection mode without creating a zone. */
    public static void cancelSelectionMode(UUID uuid) {
        selectionMode.remove(uuid);
        pendingFirst.remove(uuid);
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

            UUID uuid = sp.getUuid();
            if (!selectionMode.contains(uuid)) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();

            if (!pendingFirst.containsKey(uuid)) {
                // First corner
                pendingFirst.put(uuid, pos);
                sp.sendMessage(Text.literal(String.format(
                        "§aПерший кут: §f%d, %d, %d §a— клацніть ПКМ другий кут.",
                        pos.getX(), pos.getY(), pos.getZ())));
            } else {
                // Second corner — commit and exit selection mode
                BlockPos first = pendingFirst.remove(uuid);
                selectionMode.remove(uuid);
                commitZone(sp, first, pos);
            }

            // Consume the interaction so the block doesn't activate
            return ActionResult.SUCCESS;
        });
    }

    private static void commitZone(ServerPlayerEntity player, BlockPos a, BlockPos b) {
        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) {
            player.sendMessage(Text.literal("§cСистема недоступна."));
            return;
        }

        int currentCount = db.countZonesByOwner(player.getUuid());
        if (currentCount >= PoliceCommands.MAX_ZONES_PER_OFFICER) {
            player.sendMessage(Text.literal(
                    "§cВи досягли ліміту зон в'язниці (" + PoliceCommands.MAX_ZONES_PER_OFFICER + ")."));
            return;
        }

        // Validate volume before saving
        PrisonZone temp = new PrisonZone(0, player.getUuid(), "",
                a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
        if (temp.volume() > PoliceCommands.MAX_ZONE_VOLUME) {
            player.sendMessage(Text.literal(
                    "§cЗона занадто велика. Максимум §f" + PoliceCommands.MAX_ZONE_VOLUME + " §cблоків (зараз " + temp.volume() + ")."));
            return;
        }

        String world = player.getServerWorld().getRegistryKey().getValue().toString();
        PrisonZone zone = db.addPrisonZone(player.getUuid(), world,
                a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());

        if (zone != null) {
            player.sendMessage(Text.literal(String.format(
                    "§aЗону в'язниці §f#%d §aстворено: §f(%d,%d,%d) → (%d,%d,%d)§a, об'єм: §f%d",
                    zone.id(),
                    a.getX(), a.getY(), a.getZ(),
                    b.getX(), b.getY(), b.getZ(),
                    zone.volume())));
        } else {
            player.sendMessage(Text.literal("§cПомилка збереження зони."));
        }
    }
}
