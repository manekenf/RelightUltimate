package ua.selectedproject.clans.chat;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.selectedproject.core.config.CoreConfig;
import ua.selectedproject.core.config.CoreLocalization;
import ua.selectedproject.core.data.Clan;
import ua.selectedproject.core.data.DatabaseManager;

public final class ChatEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedClans/Chat");

    private ChatEventHandler() {}

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            try {
                Text formatted = buildLine(sender, message);
                MinecraftServer server = sender.getServer();
                if (server == null) return true;
                PlayerManager pm = server.getPlayerManager();
                pm.broadcast(formatted, false);
            } catch (Exception e) {
                LOGGER.error("Failed to build chat line for {}", sender.getName().getString(), e);
                return true; // fall back to vanilla
            }
            return false; // we handled the broadcast ourselves
        });
        LOGGER.info("Chat event handler registered (tag-before-nick mode)");
    }

    private static Text buildLine(ServerPlayerEntity sender, SignedMessage message) {
        DatabaseManager db = DatabaseManager.getInstance();
        Clan clan = db != null ? db.getClanByPlayer(sender.getUuid()) : null;

        MutableText line = Text.empty();

        if (clan != null) {
            CoreConfig config = CoreConfig.getInstance();
            String tagText = String.format(config.clanTagFormat, clan.getTag());
            MutableText clanTag = Text.literal(config.clanTagColor + tagText + "§r ")
                    .styled(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/clan info " + clan.getTag()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    buildClanHoverText(clan, db))));
            line.append(clanTag);
        }

        line.append(Text.literal("<"));
        line.append(sender.getDisplayName());     // включає team-prefix [PVE]/[PVP]/[злочинець]
        line.append(Text.literal("> "));
        line.append(message.getContent());
        return line;
    }

    private static Text buildClanHoverText(Clan clan, DatabaseManager db) {
        CoreLocalization lang = CoreLocalization.getInstance();
        int memberCount = db.getMemberCount(clan.getId());
        MutableText hover = Text.empty();
        hover.append(Text.literal(lang.get("clan.info.header", clan.getName(), clan.getTag()) + "\n"));
        hover.append(Text.literal(lang.get("clan.info.created",
                clan.getCreatedAt().toString().substring(0, 10)) + "\n"));
        hover.append(Text.literal(lang.get("clan.info.members", memberCount)));
        if (clan.getShopNumber() != null) {
            hover.append(Text.literal("\n" + lang.get("clan.info.shop", clan.getShopNumber())));
        }
        return hover;
    }
}