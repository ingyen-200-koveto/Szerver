package hu.icemine.core.skin;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.economy.IceCoins;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * A weboldalon feltöltött skin a szerveren is látszik, tört (nem prémium) klienssel is.
 * Az IceCoin lekérdezés (/api/mc/coins) minden online játékosnál megmondja, van-e skinje a weboldalon,
 * és ha új (vagy megváltozott), a SkinsRestorer ráteszi. Ha a weboldalon visszaállította az alap skint,
 * a SkinsRestorerből is levesszük. Akinek nincs weboldalas skinje, annál nem nyúlunk semmihez.
 * SkinsRestorer nélkül ez a rész nem csinál semmit.
 */
public final class WebSkins {

    private static final long RETRY_MS = 5 * 60 * 1000L;

    private final IceMineCore core;
    private final Map<String, String> applied = new ConcurrentHashMap<>();
    private final Map<String, Long> failedUntil = new ConcurrentHashMap<>();
    private final Map<String, Boolean> busy = new ConcurrentHashMap<>();
    private volatile String url = "https://icemine.hu";

    public WebSkins(IceMineCore core) {
        this.core = core;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    /** Fő szálon hívjuk, a lekérdezett nevekkel és a weboldal válaszával (név -> [verzió, slim 1/0]). */
    public void update(Collection<String> polled, Map<String, long[]> skins) {
        if (!Bukkit.getPluginManager().isPluginEnabled("SkinsRestorer")) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasMetadata("NPC") || player.getName().startsWith(".")) {
                continue;
            }
            String name = IceCoins.key(player);
            if (!polled.contains(name)) {
                continue;
            }
            long[] skin = skins.get(name);
            String want = skin == null || skin[0] <= 0 ? "" : skin[0] + (skin[1] == 1 ? ":slim" : ":classic");
            String had = applied.get(name);
            if (had == null && want.isEmpty()) {
                applied.put(name, "");
                continue;
            }
            if (want.equals(had) || failedUntil.getOrDefault(name, 0L) > now || busy.putIfAbsent(name, Boolean.TRUE) != null) {
                continue;
            }
            String skinUrl = want.isEmpty() ? null : url + "/skin/" + name + ".png?v=" + skin[0];
            SkinBridge.apply(core, player, skinUrl, skin != null && skin[1] == 1,
                    () -> {
                        applied.put(name, want);
                        failedUntil.remove(name);
                        busy.remove(name);
                    },
                    () -> {
                        failedUntil.put(name, System.currentTimeMillis() + RETRY_MS);
                        busy.remove(name);
                    });
        }
    }
}
