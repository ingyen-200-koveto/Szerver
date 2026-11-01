package hu.icemine.core.bedwars;

import hu.icemine.core.util.Text;
import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** A BedWars oldalsó scoreboard egy játékosnak, és a csapatszínek a neve fölött. */
public final class BwBoard {

    private static final int MAX = 15;

    private final Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
    private final Objective objective;
    private int shown;

    public BwBoard(Player player, String title) {
        objective = scoreboard.registerNewObjective("icemine_bw", "dummy", Text.color(title));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        try {
            Class<?> format = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            Object blank = format.getMethod("blank").invoke(null);
            Method method = Objective.class.getMethod("numberFormat", format);
            method.invoke(objective, blank);
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < MAX; i++) {
            scoreboard.registerNewTeam("line" + i).addEntry(entry(i));
        }
        for (TeamColor color : TeamColor.values()) {
            Team team = scoreboard.registerNewTeam("bw_" + color.id());
            ChatColor chat = ChatColor.getByChar(color.chat.charAt(1));
            if (chat != null) {
                team.setColor(chat);
            }
            team.setPrefix(Text.color(color.chat + "&l" + color.letter + " " + color.chat));
            team.setAllowFriendlyFire(false);
            team.setCanSeeFriendlyInvisibles(true);
        }
        player.setScoreboard(scoreboard);
    }

    private static String entry(int line) {
        return ChatColor.values()[line].toString() + ChatColor.RESET;
    }

    public void lines(List<String> lines) {
        int count = Math.min(MAX, lines.size());
        for (int i = 0; i < count; i++) {
            Team team = scoreboard.getTeam("line" + i);
            String text = Text.color(lines.get(i));
            if (team != null && !text.equals(team.getPrefix())) {
                team.setPrefix(text);
            }
            objective.getScore(entry(i)).setScore(count - i);
        }
        for (int i = count; i < shown; i++) {
            scoreboard.resetScores(entry(i));
        }
        shown = count;
    }

    /** A játékos neve ennek a csapatnak a színével látszik (null = semmilyen csapat). */
    public void color(String name, TeamColor color) {
        for (TeamColor other : TeamColor.values()) {
            Team team = scoreboard.getTeam("bw_" + other.id());
            if (team == null) {
                continue;
            }
            if (other == color) {
                if (!team.hasEntry(name)) {
                    team.addEntry(name);
                }
            } else if (team.hasEntry(name)) {
                team.removeEntry(name);
            }
        }
    }
}
