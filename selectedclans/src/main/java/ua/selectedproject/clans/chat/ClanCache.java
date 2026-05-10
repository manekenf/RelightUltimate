package ua.selectedproject.clans.chat;

import ua.selectedproject.core.data.Clan;
import ua.selectedproject.core.data.DatabaseManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiny TTL cache around {@link DatabaseManager#getClanByPlayer(UUID)} for use in
 * hot paths (per-message chat decoration, leader chat fanout). Staleness is bounded
 * by {@link #TTL_MS}; that is acceptable because the visible effect is at most a
 * handful of seconds of an out-of-date clan tag in chat after a join/leave.
 */
public final class ClanCache {
    private static final long TTL_MS = 15_000L;
    private static final ConcurrentHashMap<UUID, Entry> CACHE = new ConcurrentHashMap<>();

    private ClanCache() {}

    public static Clan getClan(UUID uuid) {
        Entry e = CACHE.get(uuid);
        long now = System.currentTimeMillis();
        if (e != null && now - e.fetchedAt < TTL_MS) {
            return e.clan;
        }
        DatabaseManager db = DatabaseManager.getInstance();
        Clan fresh = db != null ? db.getClanByPlayer(uuid) : null;
        CACHE.put(uuid, new Entry(fresh, now));
        return fresh;
    }

    public static void invalidate(UUID uuid) {
        CACHE.remove(uuid);
    }

    public static void invalidateAll() {
        CACHE.clear();
    }

    private record Entry(Clan clan, long fetchedAt) {}
}
