package ua.selectedproject.clans.leaderboard;

import ua.selectedproject.core.config.CoreConfig;
import ua.selectedproject.core.economy.CoinItems;
import ua.selectedproject.core.data.DatabaseManager;
import ua.selectedproject.core.data.Clan;
import ua.selectedproject.core.hologram.HologramManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Periodically updates the leaderboard holograms.
 * Two leaderboards:
 *   - Top clans by member count
 *   - Top clans by wealth (leader ender chest coins)
 */
public class LeaderboardUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedClans/Leaderboard");

    // Hologram positions — set via commands, stored in config
    private static Vec3d sizeLeaderboardPos = null;
    private static Vec3d wealthLeaderboardPos = null;

    // Last-known clan wealth, keyed by clan id. Only populated while a clan's leader
    // is online; survives the leader logging out so the wealth board doesn't churn
    // when leaders go offline.
    private static final java.util.Map<Integer, Long> lastWealth = new java.util.concurrent.ConcurrentHashMap<>();

    public static void setSizeLeaderboardPos(Vec3d pos) {
        sizeLeaderboardPos = pos;
    }

    public static void setWealthLeaderboardPos(Vec3d pos) {
        wealthLeaderboardPos = pos;
    }

    public static Vec3d getSizeLeaderboardPos() { return sizeLeaderboardPos; }
    public static Vec3d getWealthLeaderboardPos() { return wealthLeaderboardPos; }

    /**
     * Called periodically from server tick to refresh leaderboards.
     */
    public static void update(MinecraftServer server) {
        DatabaseManager db = DatabaseManager.getInstance();
        if (db == null) return;

        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return;

        CoreConfig config = CoreConfig.getInstance();
        HologramManager holo = HologramManager.getInstance();
        int topN = config.leaderboardSize;

        // ===== TOP CLANS BY SIZE =====
        if (sizeLeaderboardPos != null) {
            List<Text> lines = new ArrayList<>();
            lines.add(Text.literal("§6§l✦ Найбільші клани ✦"));
            lines.add(Text.literal("§8──────────────"));

            List<Map.Entry<Clan, Integer>> topBySize = db.getTopClansBySize(topN);
            for (int i = 0; i < topBySize.size(); i++) {
                Map.Entry<Clan, Integer> entry = topBySize.get(i);
                Clan clan = entry.getKey();
                int count = entry.getValue();
                String medal = switch (i) {
                    case 0 -> "§6①";
                    case 1 -> "§7②";
                    case 2 -> "§c③";
                    default -> "§8" + (i + 1);
                };
                lines.add(Text.literal(String.format("%s §f%s §8[§e%s§8] §7- §a%d",
                        medal, clan.getName(), clan.getTag(), count)));
            }

            if (topBySize.isEmpty()) {
                lines.add(Text.literal("§7Ще немає кланів"));
            }

            holo.setHologram(overworld, sizeLeaderboardPos, lines, "top_size");
        }

        // ===== TOP CLANS BY WEALTH =====
        if (wealthLeaderboardPos != null) {
            List<Text> lines = new ArrayList<>();
            lines.add(Text.literal("§6§l✦ Найбагатші клани ✦"));
            lines.add(Text.literal("§8──────────────"));

            // Get all clans and use the most recent known wealth value for ranking.
            // While a leader is online we recompute live; otherwise we fall back to
            // the last cached value, so offline leaders don't drop their clan to 0.
            List<Clan> allClans = db.getAllClans();
            List<Map.Entry<Clan, Long>> clanWealth = new ArrayList<>();

            for (Clan clan : allClans) {
                ServerPlayerEntity leader = server.getPlayerManager().getPlayer(clan.getLeaderUuid());
                Long wealth;
                if (leader != null) {
                    wealth = CoinItems.countEnderChestWealth(leader);
                    lastWealth.put(clan.getId(), wealth);
                } else {
                    wealth = lastWealth.get(clan.getId());
                }
                if (wealth != null) {
                    clanWealth.add(Map.entry(clan, wealth));
                }
            }

            // Sort by wealth descending
            clanWealth.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

            int shown = 0;
            for (Map.Entry<Clan, Long> entry : clanWealth) {
                if (shown >= topN) break;
                Clan clan = entry.getKey();
                long wealth = entry.getValue();
                String medal = switch (shown) {
                    case 0 -> "§6①";
                    case 1 -> "§7②";
                    case 2 -> "§c③";
                    default -> "§8" + (shown + 1);
                };
                lines.add(Text.literal(String.format("%s §f%s §8[§e%s§8] §7- §e%d✦",
                        medal, clan.getName(), clan.getTag(), wealth)));
                shown++;
            }

            if (clanWealth.isEmpty()) {
                lines.add(Text.literal("§7Ще немає кланів"));
            }

            holo.setHologram(overworld, wealthLeaderboardPos, lines, "top_wealth");
        }
    }
}
