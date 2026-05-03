package ua.selectedproject.papibridge;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Bukkit plugin that bridges Fabric-side state (SelectedClans, SelectedPolice) into
 * PlaceholderAPI on Arclight servers.
 * <p>
 * Why a separate plugin and not a Fabric-side PAPI expansion? On Arclight, the Bukkit
 * classloader and the Fabric mod classloader are isolated. {@code Class.forName(
 * "me.clip.placeholderapi.expansion.PlaceholderExpansion")} from a Fabric mod throws
 * {@code ClassNotFoundException}. Even with reflection lookups against the PAPI plugin's
 * own classloader, an {@code instanceof} check inside PAPI's registry would still fail
 * because the {@code PlaceholderExpansion} class loaded by the two paths is not the
 * same {@link Class}. A real Bukkit plugin sidesteps that entirely — it lives in the
 * same classloader as PAPI.
 * <p>
 * The plugin only <i>reads</i> from {@code config/selectedcore/selectedcore.db} (the
 * SQLite database created by SelectedCore). All writes still happen in the Fabric mods.
 * SQLite WAL mode lets concurrent readers see committed writes immediately.
 */
public class RelightPapiBridge extends JavaPlugin {

    @Override
    public void onEnable() {
        // The Fabric mod creates the database at <server-root>/config/selectedcore/selectedcore.db
        // (see SelectedCore.SERVER_STARTED handler — uses FabricLoader.getConfigDir()).
        // Bukkit's working directory is the server root on Arclight, so this relative path resolves.
        File dbFile = new File("config" + File.separator + "selectedcore" + File.separator + "selectedcore.db");
        if (!dbFile.exists()) {
            getLogger().severe("Database not found at " + dbFile.getAbsolutePath() +
                    " — make sure SelectedCore is loaded and ran at least once. " +
                    "Plugin will stay loaded but placeholders will return empty.");
        }

        try {
            DbAccess.init(dbFile, getLogger());
            getLogger().info("Connected to shared database at " + dbFile.getAbsolutePath());
        } catch (Exception e) {
            getLogger().severe("Failed to connect to shared database: " + e.getMessage());
            // Still continue — register expansions; they'll just return empty until DB is ready.
        }

        // Register PAPI expansions. PAPI is on the same classloader as us (we're a Bukkit plugin),
        // so .register() works directly.
        boolean clansOk = new ClansExpansion().register();
        boolean policeOk = new PoliceExpansion().register();
        getLogger().info("PAPI expansions registered — clans=" + clansOk + ", police=" + policeOk);
    }

    @Override
    public void onDisable() {
        DbAccess.close();
    }
}