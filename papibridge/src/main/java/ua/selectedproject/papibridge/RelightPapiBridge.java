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

    /** Retry config: at boot the Fabric side may not have created the DB yet, so we
     *  poll until it appears or we give up. 30s × 20 = 10 minutes total. */
    private static final int  DB_CONNECT_MAX_ATTEMPTS = 20;
    private static final long DB_CONNECT_RETRY_TICKS  = 30L * 20L; // 30s in 20Hz ticks

    @Override
    public void onEnable() {
        File dbFile = new File("config" + File.separator + "selectedcore" + File.separator + "selectedcore.db");

        // Attempt #1 immediately so the common case (Fabric already started) is zero-latency.
        if (!tryConnect(dbFile)) {
            scheduleConnectRetry(dbFile, 1);
        }

        // Always register expansions — they fall back to empty placeholders until the DB connects.
        boolean clansOk = new ClansExpansion().register();
        boolean policeOk = new PoliceExpansion().register();
        getLogger().info("PAPI expansions registered — clans=" + clansOk + ", police=" + policeOk);
    }

    private boolean tryConnect(File dbFile) {
        if (DbAccess.isConnected()) return true;
        if (!dbFile.exists()) return false;
        try {
            DbAccess.init(dbFile, getLogger());
            getLogger().info("Connected to shared database at " + dbFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            getLogger().warning("DB connect attempt failed: " + e.getMessage());
            return false;
        }
    }

    private void scheduleConnectRetry(File dbFile, int attempt) {
        if (attempt > DB_CONNECT_MAX_ATTEMPTS) {
            getLogger().severe("Database not found at " + dbFile.getAbsolutePath() +
                    " after " + DB_CONNECT_MAX_ATTEMPTS + " attempts. Placeholders will stay empty " +
                    "until you restart the plugin.");
            return;
        }
        getLogger().info("DB not ready yet (attempt " + attempt + "/" +
                DB_CONNECT_MAX_ATTEMPTS + "); retrying in 30s.");
        getServer().getScheduler().runTaskLater(this, () -> {
            if (!tryConnect(dbFile)) {
                scheduleConnectRetry(dbFile, attempt + 1);
            }
        }, DB_CONNECT_RETRY_TICKS);
    }

    @Override
    public void onDisable() {
        DbAccess.close();
    }
}