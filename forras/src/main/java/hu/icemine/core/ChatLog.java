package hu.icemine.core;

import hu.icemine.core.util.Text;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Chat napló: minden chat üzenet (a királysági és szövetségi chat is), a parancsok, a be- és kilépések naponta egy
 * fájlba kerülnek (plugins/IceMine/chatlog/ÉÉÉÉ-HH-NN.log). A jelszavas parancsoknál (/login, /register...) a jelszó
 * helyett csillag kerül a naplóba. A legutóbbi sorok játékban is megnézhetők: /icem chatlog [játékos] [oldal]
 */
public final class ChatLog implements Listener {

    static final Set<String> SECRET = Set.of("login", "l", "log", "register", "reg", "changepassword", "changepass", "cp", "unregister", "unreg",
            "authme:login", "authme:register", "authme:changepassword", "authme:unregister", "email", "authme:email", "totp", "2fa");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MEMORY = 1000;

    private final IceMineCore core;
    private final Deque<String> recent = new ArrayDeque<>();
    private ExecutorService writer;
    private boolean enabled = true;
    private boolean commands = true;
    private boolean joins = true;
    private int keepDays = 30;

    public ChatLog(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config) {
        enabled = config.getBoolean("chatlog.enabled", true);
        commands = config.getBoolean("chatlog.commands", true);
        joins = config.getBoolean("chatlog.joins", true);
        keepDays = Math.max(1, config.getInt("chatlog.keep-days", 30));
        if (enabled && writer == null) {
            writer = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "IceMine-chatlog");
                thread.setDaemon(true);
                return thread;
            });
            writer.execute(this::cleanup);
        }
    }

    public void shutdown() {
        if (writer != null) {
            writer.shutdown();
            try {
                writer.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            writer = null;
        }
    }

    private File folder() {
        return new File(core.getDataFolder(), "chatlog");
    }

    private void cleanup() {
        File[] files = folder().listFiles((dir, name) -> name.endsWith(".log"));
        if (files == null) {
            return;
        }
        LocalDate limit = LocalDate.now().minusDays(keepDays);
        for (File file : files) {
            try {
                LocalDate date = LocalDate.parse(file.getName().substring(0, 10));
                if (date.isBefore(limit)) {
                    Files.deleteIfExists(file.toPath());
                }
            } catch (RuntimeException | IOException ignored) {
                // nem napló fájl, vagy nem törölhető
            }
        }
    }

    /** A jelszavas parancsoknál csak a parancs neve marad, a többi csillag. */
    static String mask(String command) {
        String[] parts = command.trim().split("\\s+", 2);
        String name = parts[0].startsWith("/") ? parts[0].substring(1).toLowerCase(Locale.ROOT) : parts[0].toLowerCase(Locale.ROOT);
        if (SECRET.contains(name) && parts.length > 1) {
            return parts[0] + " ****";
        }
        return command;
    }

    private String where(Player player) {
        String mode = core.modes() == null ? null : core.modes().current(player);
        return mode == null ? (core.lobby() != null && core.lobby().inLobby(player) ? "lobby" : player.getWorld().getName()) : mode;
    }

    private void write(String line) {
        if (!enabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String full = "[" + TIME.format(now) + "] " + line;
        synchronized (recent) {
            recent.addLast(full);
            while (recent.size() > MEMORY) {
                recent.pollFirst();
            }
        }
        ExecutorService target = writer;
        if (target == null) {
            return;
        }
        String day = now.toLocalDate().toString();
        try {
            target.execute(() -> {
                try {
                    File dir = folder();
                    if (!dir.isDirectory() && !dir.mkdirs()) {
                        return;
                    }
                    Files.writeString(new File(dir, day + ".log").toPath(), full + System.lineSeparator(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException ignored) {
                    // ha nem írható, legfeljebb kimarad egy sor
                }
            });
        } catch (RuntimeException ignored) {
            // leállás közben
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = Text.strip(event.getMessage());
        String channel = event.isCancelled() ? " (külön csatorna)" : "";
        write("[" + where(player) + "] " + player.getName() + channel + ": " + message);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!commands) {
            return;
        }
        write("[" + where(event.getPlayer()) + "] " + event.getPlayer().getName() + " parancs: " + mask(event.getMessage()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (joins) {
            write("[+] " + event.getPlayer().getName() + " belépett (" + event.getPlayer().getAddress().getAddress().getHostAddress() + ")");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (joins) {
            write("[-] " + event.getPlayer().getName() + " kilépett");
        }
    }

    /** A legutóbbi sorok (a legújabb a végén), opcionálisan egy játékos nevére szűrve. */
    List<String> recent(String player) {
        List<String> result = new ArrayList<>();
        String needle = player == null ? null : " " + player.toLowerCase(Locale.ROOT);
        synchronized (recent) {
            for (String line : recent) {
                if (needle == null || line.toLowerCase(Locale.ROOT).contains(needle)) {
                    result.add(line);
                }
            }
        }
        return result;
    }

    /** /icem chatlog [játékos] [oldal]: a legutóbbi sorok, oldalanként 15, az első oldal a legfrissebb. */
    public void show(CommandSender sender, String[] args) {
        String player = null;
        int page = 1;
        for (int i = 1; i < args.length; i++) {
            try {
                page = Math.max(1, Integer.parseInt(args[i]));
            } catch (NumberFormatException exception) {
                player = args[i];
            }
        }
        List<String> lines = recent(player);
        int per = 15;
        int pages = Math.max(1, (lines.size() + per - 1) / per);
        page = Math.min(page, pages);
        if (lines.isEmpty()) {
            core.messages().send(sender, "chatlog.empty");
            return;
        }
        int end = lines.size() - (page - 1) * per;
        int start = Math.max(0, end - per);
        core.messages().send(sender, "chatlog.header", "%page%", String.valueOf(page), "%pages%", String.valueOf(pages),
                "%filter%", player == null ? core.messages().raw("chatlog.all") : player);
        for (String line : lines.subList(start, end)) {
            sender.sendMessage(Text.color("&7" + line.replace("§", "")));
        }
        core.messages().send(sender, "chatlog.footer");
    }
}
