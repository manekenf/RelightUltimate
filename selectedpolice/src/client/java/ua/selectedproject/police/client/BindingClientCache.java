package ua.selectedproject.police.client;

import ua.selectedproject.police.network.BindingSyncPayload;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of binding states keyed by captive's UUID. Populated by
 * {@link SelectedPoliceClient}'s payload receiver, read by the player model mixin
 * (for the hands-behind-back animation) and the leash renderer.
 * <p>
 * Two parallel maps:
 * <ul>
 *   <li>{@link #states} — captive → BOUND/LEASHED/NONE</li>
 *   <li>{@link #holders} — captive → officer UUID (only populated for LEASHED and BOUND states)</li>
 * </ul>
 */
public final class BindingClientCache {
    private static final Map<UUID, BindingSyncPayload.State> states = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> holders = new ConcurrentHashMap<>();

    private BindingClientCache() {}

    /**
     * Set a captive's binding state. If {@code holder} is null, any existing holder
     * mapping for that captive is cleared.
     */
    public static void set(UUID captive, BindingSyncPayload.State state, UUID holder) {
        states.put(captive, state);
        if (holder != null) {
            holders.put(captive, holder);
        } else {
            holders.remove(captive);
        }
    }

    public static void remove(UUID captive) {
        states.remove(captive);
        holders.remove(captive);
    }

    public static BindingSyncPayload.State get(UUID captive) {
        return states.getOrDefault(captive, BindingSyncPayload.State.NONE);
    }

    /** Officer UUID that this captive is currently leashed or bound to, or null. */
    public static UUID getHolder(UUID captive) {
        return holders.get(captive);
    }

    public static boolean isRestrained(UUID captive) {
        BindingSyncPayload.State s = states.get(captive);
        return s != null && s != BindingSyncPayload.State.NONE;
    }

    /** All currently-cached binding states. Used by the leash renderer to iterate. */
    public static Set<Map.Entry<UUID, BindingSyncPayload.State>> entries() {
        return states.entrySet();
    }

    public static void clear() {
        states.clear();
        holders.clear();
    }
}