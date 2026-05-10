package ua.selectedproject.clans.chat;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.scoreboard.Team;
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

/**
 * Builds chat messages with the format:
 *     [ClanTag] [TeamPrefix]<Nick>: message
 * <p>
 * The team prefix (PVP/PVE icon, criminal label, police label) comes from the player's
 * scoreboard team — that team is set by SelectedPolice and the prefix may be either
 * a unicode icon glyph or a plain text label, depending on what icons are available.
 * <p>
 * We bypass {@link ServerMessageEvents#ALLOW_CHAT_MESSAGE} default broadcast and
 * re-broadcast a fully-formatted line ourselves, so the clan tag and team prefix
 * appear <i>before</i> the {@code <Nick>} block (vanilla puts the name first).
 */
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
        Clan clan = ClanCache.getClan(sender.getUuid());

        MutableText line = Text.empty();

        // 1. Clan tag (clickable, hoverable) — first
        if (clan != null) {
            CoreConfig config = CoreConfig.getInstance();
            String tagText = String.format(config.clanTagFormat, clan.getTag());
            // Sanitize the configured colour code: take only a leading "§<x>" pair if present.
            String color = sanitizeColor(config.clanTagColor);
            MutableText clanTag = Text.literal(color + tagText + "§r ")
                    .styled(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/clan info " + clan.getTag()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    buildClanHoverText(clan, db))));
            line.append(clanTag);
        }

        // 2. Scoreboard team prefix (PVP/PVE icon, criminal/police label) — second
        Team team = sender.getScoreboardTeam();
        if (team != null) {
            Text teamPrefix = team.getPrefix();
            if (teamPrefix != null && !teamPrefix.getString().isEmpty()) {
                line.append(teamPrefix);
            }
        }

        // 3. Plain nick inside <> — vanilla style, but without team prefix duplication
        line.append(Text.literal("<"));
        line.append(Text.literal(sender.getGameProfile().getName()));
        line.append(Text.literal("> "));

        // 4. Message body
        line.append(message.getContent());

        return line;
    }

    /**
     * Reduce a user-supplied tag-colour string to a single legal Minecraft formatting
     * code. We accept exactly "§x" (where x is 0-9, a-f, k-o, or r); anything else
     * — empty, garbage, multiple codes — falls back to gold.
     */
    private static String sanitizeColor(String raw) {
        if (raw == null || raw.length() < 2 || raw.charAt(0) != '§') return "§6";
        char c = Character.toLowerCase(raw.charAt(1));
        boolean valid = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                || (c >= 'k' && c <= 'o') || c == 'r';
        return valid ? "§" + c : "§6";
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