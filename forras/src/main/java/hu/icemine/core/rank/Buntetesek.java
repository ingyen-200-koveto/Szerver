package hu.icemine.core.rank;

import hu.icemine.core.IceMineCore;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * A LibertyBans aktív kitiltásait és némításait percenként elküldi a weboldalnak,
 * így a weboldal Kitiltás oldalán a játékos látja a saját büntetését.
 */
public final class Buntetesek {

    private final IceMineCore core;
    private BukkitTask task;
    private String last = "";
    private long lastSent;
    private boolean warned;

    public Buntetesek(IceMineCore core) {
        this.core = core;
    }

    public void start() {
        stop();
        if (Bukkit.getPluginManager().getPlugin("LibertyBans") == null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(core, this::push, 20L * 30, 20L * 60);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void push() {
        String json;
        try {
            json = LibertyBansApi.collect();
        } catch (Throwable error) {
            if (!warned) {
                warned = true;
                core.getLogger().warning("LibertyBans: nem sikerült lekérni a büntetéseket (" + error + ").");
            }
            return;
        }
        if (json == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (json.equals(last) && now - lastSent < 600_000L) {
            return;
        }
        String body = json;
        Bukkit.getScheduler().runTask(core, () -> core.iceCoins().post("/api/mc/buntetesek", body, status -> {
            if (status >= 200 && status < 300) {
                last = body;
                lastSent = now;
            }
        }));
    }

    static String q(String value) {
        return hu.icemine.core.report.ReportCommands.q(value);
    }
}
