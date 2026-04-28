package ua.selectedproject.core;

import ua.selectedproject.core.api.AddonRegistry;
import ua.selectedproject.core.config.CoreConfig;
import ua.selectedproject.core.config.CoreLocalization;
import ua.selectedproject.core.data.DatabaseManager;
import ua.selectedproject.core.discord.DiscordBot;
import ua.selectedproject.core.economy.CoinItems;
import ua.selectedproject.core.hologram.HologramManager;
import ua.selectedproject.core.network.VelocityHelper;
import ua.selectedproject.core.portal.HubPortalBlock;
import ua.selectedproject.core.portal.PortalLighter;
import ua.selectedproject.core.portal.PortalLighterItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class SelectedCore implements ModInitializer {
    public static final String MOD_ID = "selectedcore";
    public static final Logger LOGGER = LoggerFactory.getLogger("SelectedCore");

    private static MinecraftServer serverInstance;
    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        LOGGER.info("SelectedCore initializing...");

        // Load config
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        CoreConfig.load(configDir);
        CoreLocalization.init(configDir, CoreConfig.getInstance().language);

        // Register core blocks & items
        HubPortalBlock.register();
        PortalLighterItem.register();
        CoinItems.register();

        // Register portal lighter (only if hub enabled)
        if (CoreConfig.getInstance().enableHubDimension) {
            PortalLighter.register();
        }

        // Register core commands
        CoreCommands.register();

        // Server lifecycle
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverInstance = server;

            // Initialize database
            String dbDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).toString();
            DatabaseManager.init(dbDir);

            // Initialize Velocity messaging
            VelocityHelper.init();

            // Start Discord bot
            new Thread(() -> {
                try {
                    DiscordBot.start();
                } catch (Exception e) {
                    LOGGER.error("Discord bot failed to start", e);
                }
            }, "SelectedCore-Discord").start();

            // Hub dimension
            if (CoreConfig.getInstance().enableHubDimension) {
                var hubWorld = ua.selectedproject.core.dimension.HubDimension.getHubWorld(server);
                if (hubWorld != null) {
                    ua.selectedproject.core.dimension.HubDimension.ensureSpawnPlatform(hubWorld);
                    LOGGER.info("Hub dimension ready");
                }
            }

            LOGGER.info("SelectedCore server started — all systems ready");

            // Notify all addons
            AddonRegistry.notifyServerReady(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            AddonRegistry.notifyServerStopping(server);
            HologramManager.getInstance().removeAll(server.getOverworld());
            DiscordBot.stop();
            DatabaseManager db = DatabaseManager.getInstance();
            if (db != null) db.close();
            serverInstance = null;
            LOGGER.info("SelectedCore shutting down");
        });

        // Tick events
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            // Every second — resource world timer
            if (tickCounter % 20 == 0) {
                ua.selectedproject.core.resourceworld.ResourceWorldScheduler.getInstance().tick(server);
            }

            // Every minute — addon tick + maintenance
            if (tickCounter % 1200 == 0) {
                AddonRegistry.notifyMinuteTick(server);
            }

            if (tickCounter > 1_000_000) tickCounter = 0;
        });

        LOGGER.info("SelectedCore initialized!");
    }

    public static MinecraftServer getServer() {
        return serverInstance;
    }
}
