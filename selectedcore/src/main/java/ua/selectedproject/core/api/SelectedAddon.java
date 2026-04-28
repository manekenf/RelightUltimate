package ua.selectedproject.core.api;

import net.minecraft.server.MinecraftServer;

/**
 * Interface for addon modules to register with SelectedCore.
 * Each addon implements this to hook into the core lifecycle.
 */
public interface SelectedAddon {
    /**
     * Unique ID of this addon.
     */
    String getAddonId();

    /**
     * Called when the server starts and core systems are ready.
     */
    default void onServerReady(MinecraftServer server) {}

    /**
     * Called when the server is stopping.
     */
    default void onServerStopping(MinecraftServer server) {}

    /**
     * Called every minute for periodic tasks.
     */
    default void onMinuteTick(MinecraftServer server) {}
}
