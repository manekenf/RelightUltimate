package ua.selectedproject.papibridge;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * Placeholders:
 * <ul>
 *   <li>{@code %selectedclans_tag%}     — formatted tag with brackets and color, e.g. {@code §6[ABC]}, empty if no clan</li>
 *   <li>{@code %selectedclans_tag_raw%} — bare tag without brackets, empty if no clan</li>
 *   <li>{@code %selectedclans_name%}    — full clan name, empty if no clan</li>
 * </ul>
 */
public class ClansExpansion extends PlaceholderExpansion {
    @Override public String getIdentifier() { return "selectedclans"; }
    @Override public String getAuthor()     { return "SelectedProject"; }
    @Override public String getVersion()    { return "1.0.0"; }
    /** Keep registered across PAPI reloads (since we have no plugin-instance link). */
    @Override public boolean persist()      { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || params == null) return "";
        UUID uuid = player.getUniqueId();
        DbAccess.ClanInfo clan = DbAccess.getClanByPlayer(uuid);

        return switch (params) {
            case "tag" -> {
                final int targetWidth = 7;
                if (clan == null) yield " ".repeat(targetWidth);
                String full = "[" + clan.tag() + "]";
                int padNeeded = Math.max(0, targetWidth - full.length());
                yield " ".repeat(padNeeded) + "§6" + full;
            }
            case "tag_raw" -> clan != null ? clan.tag() : "";
            case "name"    -> clan != null ? clan.name() : "";
            default        -> null; // PAPI shows the raw placeholder if we return null
        };
    }
}