package ua.selectedproject.clans.placeholder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClanPapiExpansion {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedClans/PAPI");

    private ClanPapiExpansion() {}

    public static void register() {
        try {
            Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
            new ClansExpansion().register();
            LOGGER.info("PAPI expansion registered: %selectedclans_tag%, _tag_raw%, _name%");
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            LOGGER.info("PlaceholderAPI not present — skipping clans expansion");
        } catch (Exception e) {
            LOGGER.warn("Failed to register clans PAPI expansion", e);
        }
    }
}