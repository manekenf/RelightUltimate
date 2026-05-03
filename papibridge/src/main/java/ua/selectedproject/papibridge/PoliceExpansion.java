package ua.selectedproject.papibridge;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * Placeholders:
 * <ul>
 *   <li>{@code %selectedpolice_icon%}      — colored bracketed label: {@code §c[злочинець]}, {@code §c[PVP]}, or {@code §a[PVE]}</li>
 *   <li>{@code %selectedpolice_status%}    — uppercase: {@code CRIMINAL}, {@code PVP}, {@code PVE}</li>
 *   <li>{@code %selectedpolice_is_police%} — {@code true} / {@code false}</li>
 *   <li>{@code %selectedpolice_is_bound%}  — {@code true} / {@code false} — used to drive the model swap on the client mod</li>
 *   <li>{@code %selectedpolice_is_leashed%}— {@code true} / {@code false}</li>
 * </ul>
 */
public class PoliceExpansion extends PlaceholderExpansion {
    @Override public String getIdentifier() { return "selectedpolice"; }
    @Override public String getAuthor()     { return "SelectedProject"; }
    @Override public String getVersion()    { return "1.0.0"; }
    @Override public boolean persist()      { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || params == null) return "";
        UUID uuid = player.getUniqueId();
        DbAccess.PoliceStatus s = DbAccess.getPoliceStatus(uuid);

        return switch (params) {
            case "icon" -> {
                if (s.criminal())                 yield "    §c[злочинець]";  // tmp text
                else if (DbAccess.isPolice(uuid)) yield "      §b[поліція]";  // tmp text
                else if (s.pvp())                 yield "                \uE102"; // pvp icon
                else                              yield "                \uE103"; // pve icon
            }
            case "status" -> {
                if (s.criminal())                 yield "CRIMINAL";
                else if (DbAccess.isPolice(uuid)) yield "POLICE";
                else if (s.pvp())                 yield "PVP";
                else                              yield "PVE";
            }
            case "is_police" -> Boolean.toString(DbAccess.isPolice(uuid));
            case "is_bound" -> Boolean.toString(s.bound());
            case "is_leashed" -> Boolean.toString(s.leashed());
            default -> null;
        };
    }
}