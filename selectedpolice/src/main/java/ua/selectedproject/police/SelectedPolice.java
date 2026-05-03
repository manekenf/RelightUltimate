package ua.selectedproject.police;

import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.selectedproject.core.api.AddonRegistry;
import ua.selectedproject.core.api.SelectedAddon;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import ua.selectedproject.police.network.BindingSyncPayload;

import java.util.List;
import java.util.UUID;

public class SelectedPolice implements ModInitializer, SelectedAddon {
    public static final String MOD_ID = "selectedpolice";
    public static final Logger LOGGER = LoggerFactory.getLogger("SelectedPolice");

    @Override
    public void onInitialize() {
        LOGGER.info("SelectedPolice initializing...");
        PayloadTypeRegistry.playS2C().register(BindingSyncPayload.PACKET_ID, BindingSyncPayload.CODEC);
        PoliceCommands.register();
        PvpEventHandler.register();
        BindingHandler.register();
        PoliceEventHandler.register();
        PrisonSelectionHandler.register();
        AddonRegistry.register(this);

        LOGGER.info("SelectedPolice initialized!");
    }

    @Override
    public String getAddonId() {
        return MOD_ID;
    }

    @Override
    public void onServerReady(MinecraftServer server) {
        LOGGER.info("SelectedPolice server systems ready — initializing database");
        PoliceDatabase.init();
        PvpEventHandler.restoreCriminalTags(server);
    }

    @Override
    public void onMinuteTick(MinecraftServer server) {
        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) return;

        List<UUID> expired = db.clearExpiredCriminals();
        for (UUID uuid : expired) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                PvpEventHandler.applyCriminalTag(player, false);
                player.sendMessage(net.minecraft.text.Text.literal("§aВаш статус злочинця знято."));
            }
            LOGGER.info("Criminal status expired for {}", uuid);
        }
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db != null) db.close();
    }
}
