package ua.selectedproject.police.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import ua.selectedproject.police.PoliceDatabase;

import java.util.UUID;

/**
 * PlaceholderAPI expansion exposing PVP/PVE status placeholders.
 *
 * Registered only when PAPI is present on the classpath (Arclight server with PAPI plugin).
 *
 * Available placeholders:
 *   %selectedpolice_icon%   — §a🛡 (PVE) or §c⚔ (PVP)
 *   %selectedpolice_status% — PVE or PVP
 */
public class PoliceExpansion extends PlaceholderExpansion {

    @Override
    public String getIdentifier() {
        return "selectedpolice";
    }

    @Override
    public String getAuthor() {
        return "SelectedProject";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    /** Keep the expansion registered across plugin reloads. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";

        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) return "";

        UUID uuid = player.getUniqueId();
        boolean pvp = db.isPvp(uuid);

        return switch (params) {
            case "icon"   -> pvp ? "§c⚔" : "§a🛡";
            case "status" -> pvp ? "PVP" : "PVE";
            default       -> null; // PAPI treats null as "placeholder not found"
        };
    }
}
