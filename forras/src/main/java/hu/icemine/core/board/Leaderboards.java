package hu.icemine.core.board;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.data.ProfileService;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Leaderboards {

    public static final class Board {
        public final String id;
        public final String title;
        public final String source;
        public final String key;
        public final boolean ascending;
        public final String format;

        Board(String id, String title, String source, String key, boolean ascending, String format) {
            this.id = id;
            this.title = title;
            this.source = source;
            this.key = key;
            this.ascending = ascending;
            this.format = format;
        }
    }

    private final IceMineCore core;
    private final Map<String, Board> boards = new LinkedHashMap<>();
    private final Map<String, List<ProfileService.Entry>> cache = new ConcurrentHashMap<>();
    private int size = 10;
    private volatile boolean refreshing;

    public Leaderboards(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        boards.clear();
        size = Math.max(3, Math.min(15, config.getInt("leaderboards.size", 10)));
        ConfigurationSection section = config.getConfigurationSection("leaderboards.boards");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            String source = section.getString(id + ".source", "stat").toLowerCase(Locale.ROOT);
            if (!source.equals("stat") && !source.equals("balance")) {
                problems.add("config.yml -> leaderboards.boards." + id + ".source: stat vagy balance lehet");
                continue;
            }
            String key = section.getString(id + ".key", "");
            if (key.isBlank()) {
                problems.add("config.yml -> leaderboards.boards." + id + ".key: hiányzik");
                continue;
            }
            boards.put(id.toLowerCase(Locale.ROOT), new Board(id.toLowerCase(Locale.ROOT), section.getString(id + ".title", id), source, key,
                    section.getBoolean(id + ".ascending", false), section.getString(id + ".format", "number").toLowerCase(Locale.ROOT)));
        }
    }

    public Map<String, Board> boards() {
        return boards;
    }

    public Board board(String id) {
        return id == null ? null : boards.get(id.toLowerCase(Locale.ROOT));
    }

    public List<ProfileService.Entry> entries(String id) {
        List<ProfileService.Entry> entries = cache.get(id);
        return entries == null ? Collections.emptyList() : entries;
    }

    public String format(Board board, long value) {
        switch (board.format) {
            case "time":
                return Text.timer(value);
            case "money":
                return core.economy().format(board.key, value);
            default:
                return Text.number(value);
        }
    }

    public List<String> lines(String id) {
        Board board = board(id);
        List<String> lines = new ArrayList<>();
        if (board == null) {
            return lines;
        }
        lines.add(board.title);
        List<ProfileService.Entry> entries = entries(board.id);
        String format = core.messages().raw("leaderboard.line");
        for (int i = 0; i < size; i++) {
            if (i < entries.size()) {
                ProfileService.Entry entry = entries.get(i);
                lines.add(Text.replace(format, "%place%", String.valueOf(i + 1), "%player%", entry.name, "%value%", format(board, entry.value)));
            } else {
                lines.add(Text.replace(core.messages().raw("leaderboard.empty"), "%place%", String.valueOf(i + 1)));
            }
        }
        return lines;
    }

    public void refresh() {
        if (refreshing || boards.isEmpty()) {
            return;
        }
        refreshing = true;
        core.profiles().saveDirty().whenComplete((ignored, error) -> {
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (Board board : boards.values()) {
                CompletableFuture<List<ProfileService.Entry>> query = board.source.equals("balance")
                        ? core.profiles().topBalance(board.key, size)
                        : core.profiles().topStat(board.key, board.ascending, size);
                futures.add(query.thenAccept(entries -> cache.put(board.id, entries)));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((done, failure) -> refreshing = false);
        });
    }
}
