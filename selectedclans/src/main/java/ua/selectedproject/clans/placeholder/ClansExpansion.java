package ua.selectedproject.clans.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import ua.selectedproject.core.config.CoreConfig;
import ua.selectedproject.core.data.Clan;
import ua.selectedproject.core.data.DatabaseManager;

import java.util.UUID;

public class ClansExpansion extends PlaceholderExpansion {
    @Override public String getIdentifier() { return "selectedclans"; }
    @Override public String getAuthor()     { return "SelectedProject"; }
    @Override public String getVersion()    { return "1.0.0"; }
    @Override public boolean persist()      { return true; }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || params == null) return "";
        DatabaseManager db = DatabaseManager.getInstance();
        if (db == null) return "";
        UUID uuid = player.getUniqueId();
        Clan clan = db.getClanByPlayer(uuid);
        return switch (params) {
            case "tag" -> {
                if (clan == null) yield "";
                CoreConfig cfg = CoreConfig.getInstance();
                yield cfg.clanTagColor + String.format(cfg.clanTagFormat, clan.getTag());
            }
            case "tag_raw" -> clan != null ? clan.getTag() : "";
            case "name"    -> clan != null ? clan.getName() : "";
            default -> null;
        };
    }
}