package hu.icemine.core.duels;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class DuelsMenu {
    private static final int[] MODE_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private final IceMineCore core;
    private final Duels duels;

    DuelsMenu(IceMineCore core, Duels duels) {
        this.core = core;
        this.duels = duels;
    }

    private String raw(String s) {
        return this.core.messages().raw(s);
    }

    public void open(Player player) {
        boolean flag = this.duels.ranked(player);
        Menu menu = new Menu("duels", 6, this.raw("duels.menu.title"));
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        String s = this.duels.queuedFor(player);
        int i = 0;

        for (DuelKit kit : this.duels.kits().values()) {
            if (i >= MODE_SLOTS.length) {
                break;
            }

            boolean flag1 = flag && kit.ranked;
            List<String> result = new ArrayList<>(kit.description);
            result.add("");
            result.add(this.raw("duels.menu.in-queue").replace("%count%", String.valueOf(this.duels.queueSize(kit.id, flag1))));
            result.add(this.raw("duels.menu.playing").replace("%count%", String.valueOf(this.duels.playing(kit.id))));
            if (kit.ranked) {
                result.add(this.raw("duels.menu.your-elo").replace("%elo%", String.valueOf(this.duels.elo(player, kit.id))));
            }

            result.add("");
            boolean flag2 = s != null && s.startsWith(kit.id + ":");
            if (!kit.allowed(player)) {
                result.add(this.raw("duels.menu.locked"));
            } else {
                result.add(
                    flag2
                        ? this.raw("duels.menu.click-leave")
                        : this.raw("duels.menu.click-join").replace("%type%", this.raw(flag1 ? "duels.ranked" : "duels.unranked"))
                );
            }

            Items items = Items.of(kit.icon).name(kit.name).lore(result).hideAll();
            if (flag2) {
                items.glow();
            }

            String s1 = kit.id;
            menu.set(MODE_SLOTS[i++], items.build(), player1 -> {
                if (flag2) {
                    this.duels.leaveQueue(player1, true);
                } else {
                    this.duels.join(player1, s1, this.duels.ranked(player1));
                }

                player1.closeInventory();
            });
        }

        menu.set(
            48,
            Items.of(flag ? Material.GOLDEN_SWORD : Material.WOODEN_SWORD)
                .name(this.raw(flag ? "duels.menu.ranked-on" : "duels.menu.ranked-off"))
                .lore(this.core.messages().list("duels.menu.ranked-lore"))
                .hideAll()
                .build(),
            player1 -> {
                this.duels.toggleRanked(player1);
                this.open(player1);
            }
        );
        menu.set(49, Items.head(player).name(this.raw("duels.menu.stats")).lore(this.statsLore(player)).build(), this::stats);
        menu.set(
            50,
            Items.of(Material.ENDER_EYE).name(this.raw("duels.menu.spectate")).lore(this.core.messages().list("duels.menu.spectate-lore")).build(),
            this::spectate
        );
        if (s != null) {
            menu.set(45, Items.of(Material.BARRIER).name(this.raw("duels.menu.leave-queue")).build(), player1 -> {
                this.duels.leaveQueue(player1, true);
                player1.closeInventory();
            });
        }

        menu.open(player);
    }

    private List<String> statsLore(Player player) {
        PlayerProfile profile = this.core.profiles().profile(player);
        List<String> result = new ArrayList<>();
        long i = profile == null ? 0L : profile.stat("duels.wins");
        long j = profile == null ? 0L : profile.stat("duels.losses");
        result.add("&7Győzelmek: &a" + Text.number(i));
        result.add("&7Vereségek: &c" + Text.number(j));
        result.add("&7W/L: &f" + Text.ratio(i, j));
        result.add(
            "&7Győzelmi sorozat: &e"
                + (profile == null ? 0L : profile.stat("duels.streak"))
                + " &8(legjobb: "
                + (profile == null ? 0L : profile.stat("duels.best_streak"))
                + ")"
        );
        result.add("");
        result.add("&e» Kattints a részletekért!");
        return result;
    }

    public void stats(Player player) {
        PlayerProfile profile = this.core.profiles().profile(player);
        Menu menu = new Menu("duels-stats", 6, this.raw("duels.menu.stats-title"));
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        int i = 0;

        for (DuelKit kit : this.duels.kits().values()) {
            if (i >= MODE_SLOTS.length) {
                break;
            }

            long j = profile == null ? 0L : profile.stat("duels." + kit.id + ".wins");
            long k = profile == null ? 0L : profile.stat("duels." + kit.id + ".losses");
            List<String> result = new ArrayList<>();
            result.add("&7Győzelmek: &a" + j);
            result.add("&7Vereségek: &c" + k);
            if (kit.ranked) {
                int l = this.duels.elo(player, kit.id);
                result.add("&7Elo: &f" + l + " &8(" + Elo.division(l) + "&8)");
            }

            menu.set(MODE_SLOTS[i++], Items.of(kit.icon).name(kit.name).lore(result).hideAll().build());
        }

        menu.set(49, Items.of(Material.ARROW).name(this.raw("menu.back")).build(), this::open);
        menu.open(player);
    }

    public void spectate(Player player) {
        Menu menu = new Menu("duels-spectate", 6, this.raw("duels.menu.spectate-title"));
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        int i = 0;

        for (Match match : this.duels.running()) {
            if (i >= MODE_SLOTS.length) {
                break;
            }

            Player player1 = Bukkit.getPlayer(match.red);
            if (player1 != null) {
                List<String> result = new ArrayList<>();
                result.add("&7" + Text.color(match.kit.name));
                result.add("&7Állás: &f" + match.redWins + " - " + match.blueWins);
                result.add("&7Nézők: &f" + match.spectators.size());
                result.add("");
                result.add("&e» Kattints a nézéshez!");
                menu.set(MODE_SLOTS[i++], Items.head(player1).name("&c" + match.redName + " &7vs &9" + match.blueName).lore(result).build(), player2 -> {
                    player2.closeInventory();
                    Player player3 = Bukkit.getPlayer(match.red);
                    if (player3 != null) {
                        this.duels.spectate(player2, player3);
                    }
                });
            }
        }

        if (i == 0) {
            menu.set(22, Items.of(Material.PAPER).name(this.raw("duels.menu.no-matches")).build());
        }

        menu.set(49, Items.of(Material.ARROW).name(this.raw("menu.back")).build(), this::open);
        menu.open(player);
    }

    public void chooseForInvite(Player player, Player player1, int i) {
        Menu menu = new Menu("duels-invite", 6, this.raw("duels.menu.invite-title").replace("%player%", player1.getName()));
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        int j = 0;

        for (DuelKit kit : this.duels.kits().values()) {
            if (j >= MODE_SLOTS.length) {
                break;
            }

            List<String> result = new ArrayList<>(kit.description);
            result.add("");
            result.add(this.raw("duels.menu.click-invite"));
            String s = kit.id;
            menu.set(MODE_SLOTS[j++], Items.of(kit.icon).name(kit.name).lore(result).hideAll().build(), player3 -> {
                player3.closeInventory();
                Player player4 = Bukkit.getPlayer(player1.getUniqueId());
                if (player4 == null) {
                    this.core.messages().send(player3, "general.no-player");
                } else {
                    this.duels.invite(player3, player4, s, i);
                }
            });
        }

        menu.set(
            49,
            Items.of(i > 1 ? Material.CLOCK : Material.PAPER)
                .name(this.raw(i > 1 ? "duels.menu.bo3-on" : "duels.menu.bo3-off"))
                .lore(this.core.messages().list("duels.menu.bo3-lore"))
                .build(),
            player3 -> this.chooseForInvite(player3, player1, i > 1 ? 1 : 3)
        );
        menu.open(player);
    }
}
