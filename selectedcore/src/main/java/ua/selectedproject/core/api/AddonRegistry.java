package ua.selectedproject.core.api;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry for addon modules. Addons register during initialization
 * and receive lifecycle callbacks from the core.
 */
public class AddonRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedCore/Addons");
    private static final List<SelectedAddon> addons = new ArrayList<>();

    public static void register(SelectedAddon addon) {
        addons.add(addon);
        LOGGER.info("Addon registered: {}", addon.getAddonId());
    }

    public static List<SelectedAddon> getAddons() {
        return addons;
    }

    public static void notifyServerReady(MinecraftServer server) {
        for (SelectedAddon addon : addons) {
            try {
                addon.onServerReady(server);
            } catch (Exception e) {
                LOGGER.error("Addon {} failed on server ready", addon.getAddonId(), e);
            }
        }
    }

    public static void notifyServerStopping(MinecraftServer server) {
        for (SelectedAddon addon : addons) {
            try {
                addon.onServerStopping(server);
            } catch (Exception e) {
                LOGGER.error("Addon {} failed on server stopping", addon.getAddonId(), e);
            }
        }
    }

    public static void notifyMinuteTick(MinecraftServer server) {
        for (SelectedAddon addon : addons) {
            try {
                addon.onMinuteTick(server);
            } catch (Exception e) {
                LOGGER.error("Addon {} failed on minute tick", addon.getAddonId(), e);
            }
        }
    }
}
