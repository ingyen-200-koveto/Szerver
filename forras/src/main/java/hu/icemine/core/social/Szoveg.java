package hu.icemine.core.social;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.util.ClickText;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Kattintható, prefixes üzenetek a barát és party parancsokhoz. [[szöveg|run:/parancs]] formában lehet gombot tenni bele. */
final class Szoveg {

    private Szoveg() {
    }

    static void kuld(IceMineCore core, CommandSender to, String text) {
        if (to == null) {
            return;
        }
        String line = core.messages().prefix(to) + text;
        if (to instanceof Player player) {
            player.sendMessage(ClickText.line(line, "&7Kattints: &f%value%", "&7Megnyitás"));
        } else {
            to.sendMessage(hu.icemine.core.util.Text.color(line.replaceAll("\\[\\[(.+?)\\|[a-z]+:.+?]]", "$1")));
        }
    }

    static boolean nevJo(String name) {
        return name != null && name.matches("\\.?[A-Za-z0-9_]{1,16}");
    }
}
