package ua.selectedproject.police.placeholder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Entry point for PlaceholderAPI integration.
 *
 * PAPI is a Bukkit plugin; on Arclight it is available via the combined class loader.
 * We guard the registration with a Class.forName check so the mod loads cleanly on
 * servers that don't have PAPI installed.
 */
public class PapiExpansion {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedPolice/PAPI");

    /**
     * Attempt to register the PlaceholderAPI expansion.
     * Safe to call even if PAPI is absent — errors are caught and logged.
     * Must be called after the server is ready (onServerReady), not during mod init.
     */
    public static void register() {
        try {
            // Probe for PAPI on the runtime classpath before touching PoliceExpansion
            Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
            new PoliceExpansion().register();
            LOGGER.info("PlaceholderAPI expansion registered: %selectedpolice_icon%, %selectedpolice_status%");
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            LOGGER.info("PlaceholderAPI not present — skipping expansion registration");
        } catch (Exception e) {
            LOGGER.warn("Failed to register PlaceholderAPI expansion", e);
        }
    }

    /**
     * Called after a PVP/PVE toggle.
     * PoliceExpansion reads live from PoliceDatabase, so no cache invalidation needed.
     */
    public static void notifyChange(UUID playerUuid) {
        // no-op — values are read live from PoliceDatabase on every placeholder request
    }
}
