package ua.selectedproject.police.client;

import ua.selectedproject.police.network.BindingSyncPayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of binding states keyed by player UUID. Populated by
 * {@link SelectedPoliceClient}'s payload receiver, read by the player model mixin
 * to decide which animation to apply.
 */
public final class BindingClientCache {
    private static final Map<UUID, BindingSyncPayload.State> states = new ConcurrentHashMap<>();

    private BindingClientCache() {}

    public static void set(UUID uuid, BindingSyncPayload.State state) {
        states.put(uuid, state);
    }

    public static void remove(UUID uuid) {
        states.remove(uuid);
    }

    public static BindingSyncPayload.State get(UUID uuid) {
        return states.getOrDefault(uuid, BindingSyncPayload.State.NONE);
    }

    public static boolean isRestrained(UUID uuid) {
        BindingSyncPayload.State s = states.get(uuid);
        return s != null && s != BindingSyncPayload.State.NONE;
    }

    public static void clear() {
        states.clear();
    }
}