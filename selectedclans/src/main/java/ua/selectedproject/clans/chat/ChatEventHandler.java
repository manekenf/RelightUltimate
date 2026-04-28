package ua.selectedproject.clans.chat;

import ua.selectedproject.core.config.CoreLocalization;
import ua.selectedproject.core.config.CoreConfig;
import ua.selectedproject.core.data.DatabaseManager;
import ua.selectedproject.core.data.Clan;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles clan tag injection into chat messages using Fabric Message API.
 * Tags are clickable — clicking runs /clan info <tag> to show clan details.
 */
public class ChatEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedClans/Chat");

    public static void register() {
        // Decorate chat messages with clan tags
        ServerMessageDecoratorEvent.EVENT.register(ServerMessageDecoratorEvent.CONTENT_PHASE,
                (sender, message) -> {
                    if (sender == null) return message;

                    DatabaseManager db = DatabaseManager.getInstance();
                    if (db == null) return message;

                    Clan clan = db.getClanByPlayer(sender.getUuid());
                    if (clan == null) return message;

                    // Build clickable clan tag
                    CoreConfig config = CoreConfig.getInstance();
                    String tagText = String.format(config.clanTagFormat, clan.getTag());

                    MutableText clanTag = Text.literal(config.clanTagColor + tagText + "§r ")
                            .styled(style -> style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                            "/clan info " + clan.getTag()))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            buildClanHoverText(clan, db))));

                    // Prepend tag to the message
                    MutableText decorated = Text.empty();
                    decorated.append(clanTag);
                    decorated.append(message);

                    return decorated;
                });

        LOGGER.info("Chat event handler registered");
    }

    /**
     * Build the hover tooltip text shown when hovering over a clan tag.
     * Shows: full name, date, member count, shop number.
     */
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
