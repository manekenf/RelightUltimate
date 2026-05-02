package ua.selectedproject.clans;

import ua.selectedproject.clans.block.ClanBoardBlock;
import ua.selectedproject.clans.chat.ChatEventHandler;
import ua.selectedproject.clans.chat.ClanCommands;
import ua.selectedproject.clans.network.NetworkHandler;
import ua.selectedproject.core.api.AddonRegistry;
import ua.selectedproject.core.api.SelectedAddon;
import ua.selectedproject.core.data.DatabaseManager;
import ua.selectedproject.core.config.CoreConfig;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SelectedClans implements ModInitializer, SelectedAddon {
    public static final String MOD_ID = "selectedclans";
    public static final Logger LOGGER = LoggerFactory.getLogger("SelectedClans");

    @Override
    public void onInitialize() {
        LOGGER.info("SelectedClans initializing...");

        // Register blocks & items
        ClanBoardBlock.register();

        // Register networking
        NetworkHandler.registerServerPayloads();
        NetworkHandler.registerServerReceivers();

        // Register chat event handler
        ChatEventHandler.register();

        // Register commands
        ClanCommands.register();

        // Register as addon with SelectedCore
        AddonRegistry.register(this);

        LOGGER.info("SelectedClans initialized!");
    }

    @Override
    public String getAddonId() {
        return MOD_ID;
    }

    @Override
    public void onServerReady(MinecraftServer server) {
        LOGGER.info("SelectedClans server systems ready");

        ua.selectedproject.clans.placeholder.ClanPapiExpansion.register();
    }

    @Override
    public void onMinuteTick(MinecraftServer server) {
        DatabaseManager db = DatabaseManager.getInstance();
        if (db == null) return;

        // Expire old invites
        db.expireOldInvites(CoreConfig.getInstance().inviteExpirationMinutes * 60L);

        // Check clans scheduled for deletion
        var clansToDelete = db.getClansScheduledForDeletion();
        for (var clan : clansToDelete) {
            String clanName = clan.getName();
            String clanTag = clan.getTag();

            // Discord notification
            var bot = ua.selectedproject.core.discord.DiscordBot.getInstance();
            if (bot != null && bot.isRunning()) {
                bot.notifyClanDeleted(clanName, clanTag);
                bot.sendDirectMessage(clan.getLeaderUuid(),
                        "💀 Клан видалено",
                        "Ваш клан **" + clanName + "** було видалено.");
            }

            db.deleteClan(clan.getId());

            // Broadcast in-game
            var lang = ua.selectedproject.core.config.CoreLocalization.getInstance();
            String prefix = lang.get("prefix");
            var msg = net.minecraft.text.Text.literal(lang.get("clan.deletion.deleted", prefix, clanName, clanTag));
            ua.selectedproject.clans.network.ClanActionHandler.broadcastToServer(server, msg);

            LOGGER.info("Clan '{}' [{}] auto-deleted", clanName, clanTag);
        }
    }
}
