package hu.icemine.core.board;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.util.Text;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * A tablista (fejléc, lábléc, a nevek előtagja és a sorrend). Ha a TAB plugin fent van, az API-ján keresztül írjuk,
 * így a TAB alap beállításai (angol "Server name", memória, ping számok...) nem látszanak, és nem villog, mert csak
 * egy plugin írja. TAB nélkül a sima Bukkit fejléc és lábléc megy. Reflexióval, fordítási függőség nélkül.
 */
public final class TabList {

    private final IceMineCore core;
    private final Map<UUID, String> sentHeader = new HashMap<>();
    private final Map<UUID, String> sentPrefix = new HashMap<>();
    private final Map<UUID, String> sentSort = new HashMap<>();
    private final Map<UUID, Boolean> objectiveOff = new HashMap<>();
    private Object api;
    private Method getPlayer;
    private Method headerFooter;
    private Method setHeaderAndFooter;
    private Method formatManager;
    private Method setTabPrefix;
    private Method setTabSuffix;
    private Method sortingManager;
    private Method forceTeamName;
    private Method objectiveManager;
    private Method setObjectiveEnabled;
    private boolean checked;
    private boolean warned;

    public TabList(IceMineCore core) {
        this.core = core;
    }

    private boolean connect() {
        if (checked) {
            return api != null;
        }
        checked = true;
        Plugin plugin = Bukkit.getPluginManager().getPlugin("TAB");
        if (plugin == null || !plugin.isEnabled()) {
            return false;
        }
        try {
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class<?> type = Class.forName("me.neznamy.tab.api.TabAPI", true, loader);
            Class<?> player = Class.forName("me.neznamy.tab.api.TabPlayer", true, loader);
            api = type.getMethod("getInstance").invoke(null);
            getPlayer = type.getMethod("getPlayer", UUID.class);
            headerFooter = type.getMethod("getHeaderFooterManager");
            setHeaderAndFooter = Class.forName("me.neznamy.tab.api.tablist.HeaderFooterManager", true, loader)
                    .getMethod("setHeaderAndFooter", player, String.class, String.class);
            formatManager = type.getMethod("getTabListFormatManager");
            Class<?> format = Class.forName("me.neznamy.tab.api.tablist.TabListFormatManager", true, loader);
            setTabPrefix = format.getMethod("setPrefix", player, String.class);
            setTabSuffix = format.getMethod("setSuffix", player, String.class);
            sortingManager = type.getMethod("getSortingManager");
            forceTeamName = Class.forName("me.neznamy.tab.api.tablist.SortingManager", true, loader).getMethod("forceTeamName", player, String.class);
            objectiveManager = type.getMethod("getPlayerListObjectiveManager");
            setObjectiveEnabled = Class.forName("me.neznamy.tab.api.playerlistobjective.PlayerListObjectiveManager", true, loader)
                    .getMethod("setEnabled", player, Boolean.class);
            core.getLogger().info("TAB megtalálva: a tablistát (fejléc, lábléc, rangok, sorrend) az IceMine állítja be rajta keresztül.");
            return true;
        } catch (Throwable error) {
            api = null;
            core.getLogger().warning("A TAB fent van, de az API-ja nem érhető el (" + error + "), a tablistát nem tudom beállítani.");
            return false;
        }
    }

    /** Újracsatlakozás (pl. /icem reload vagy a TAB újratöltése után), és minden újra kimegy. */
    public void reset() {
        checked = false;
        api = null;
        sentHeader.clear();
        sentPrefix.clear();
        sentSort.clear();
        objectiveOff.clear();
    }

    public void forget(Player player) {
        sentHeader.remove(player.getUniqueId());
        sentPrefix.remove(player.getUniqueId());
        sentSort.remove(player.getUniqueId());
        objectiveOff.remove(player.getUniqueId());
    }

    private Object tabPlayer(Player player) throws Exception {
        return getPlayer.invoke(api, player.getUniqueId());
    }

    private void warn(Throwable error) {
        if (!warned) {
            warned = true;
            core.getLogger().warning("Hiba a tablista beállításánál (TAB), egyszer írom ki: " + error);
        }
    }

    /** Fejléc és lábléc (színkódokkal, a sorok \n-nel elválasztva). Csak akkor küldjük, ha változott. */
    public void headerFooter(Player player, String header, String footer) {
        String colored = Text.color(header);
        String coloredFooter = Text.color(footer);
        String key = colored + "\u0000" + coloredFooter;
        if (key.equals(sentHeader.get(player.getUniqueId()))) {
            return;
        }
        if (connect()) {
            try {
                Object tabPlayer = tabPlayer(player);
                if (tabPlayer == null) {
                    return;
                }
                setHeaderAndFooter.invoke(headerFooter.invoke(api), tabPlayer, colored, coloredFooter);
                sentHeader.put(player.getUniqueId(), key);
            } catch (Throwable error) {
                warn(error);
            }
            return;
        }
        player.setPlayerListHeaderFooter(colored, coloredFooter);
        sentHeader.put(player.getUniqueId(), key);
    }

    /**
     * A név a tablistában: rang előtag, és a sorrend (a magasabb rang feljebb). A TAB ping számait is kikapcsoljuk.
     * TAB nélkül a Bukkit lista neve kapja az előtagot.
     */
    public void name(Player player, String prefix, int order) {
        String colored = Text.color(prefix == null ? "" : prefix);
        String sort = sortKey(order, player.getName());
        if (connect()) {
            try {
                Object tabPlayer = tabPlayer(player);
                if (tabPlayer == null) {
                    return;
                }
                if (!colored.equals(sentPrefix.get(player.getUniqueId()))) {
                    Object manager = formatManager.invoke(api);
                    if (manager != null) {
                        setTabPrefix.invoke(manager, tabPlayer, colored);
                        setTabSuffix.invoke(manager, tabPlayer, "");
                    }
                    sentPrefix.put(player.getUniqueId(), colored);
                }
                if (!sort.equals(sentSort.get(player.getUniqueId()))) {
                    Object manager = sortingManager.invoke(api);
                    if (manager != null) {
                        forceTeamName.invoke(manager, tabPlayer, sort);
                    }
                    sentSort.put(player.getUniqueId(), sort);
                }
                if (!objectiveOff.containsKey(player.getUniqueId())) {
                    Object manager = objectiveManager.invoke(api);
                    if (manager != null) {
                        setObjectiveEnabled.invoke(manager, tabPlayer, Boolean.FALSE);
                    }
                    objectiveOff.put(player.getUniqueId(), true);
                }
            } catch (Throwable error) {
                warn(error);
            }
            return;
        }
        String listName = colored + player.getName();
        if (!listName.equals(player.getPlayerListName())) {
            player.setPlayerListName(listName);
        }
    }

    /** A TAB csapatnév a sorrendhez: két számjegy a rangból (kisebb = feljebb), aztán a név (max 16 karakter). */
    static String sortKey(int order, String name) {
        int rank = Math.max(0, Math.min(99, 99 - order));
        String base = name.length() > 12 ? name.substring(0, 12) : name;
        String hash = Integer.toString(Math.abs(name.hashCode() % 1296), 36);
        return String.format("%02d", rank) + base + (hash.length() < 2 ? "0" + hash : hash);
    }
}
