package hu.icemine.core.fyland;

import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** A Fyland menüi: főmenü, fázisok, fejlesztések, piac (eladás, vásárlás), tagok, beállítások, toplista, látogatás. */
final class FyMenus {

    private static final int[] INNER = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final FylandMode mode;

    FyMenus(FylandMode mode) {
        this.mode = mode;
    }

    private String t(String key, String... pairs) {
        return mode.text("menu." + key, pairs);
    }

    private List<String> lines(String key, String... pairs) {
        return mode.texts("menu." + key, pairs);
    }

    private void back(Menu menu, int slot) {
        menu.set(slot, Items.of(Material.ARROW).name(t("back")).build(), this::main);
    }

    void main(Player player) {
        FyIsland island = mode.islandOf(player);
        if (island == null || !mode.inWorld(player)) {
            mode.goHome(player);
            return;
        }
        FyPhase phase = mode.phase(island);
        Menu menu = new Menu("fyland-main", 5, t("main-title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        menu.set(4, Items.head(Bukkit.getOfflinePlayer(island.owner)).name(t("island-name", "%owner%", island.display())).lore(lines("island-lore",
                "%worth%", Text.number(island.worth), "%next%", Text.number(mode.worth().toNext(island)),
                "%phase%", phase.name, "%done%", Text.number(island.phaseBlocks), "%total%", Text.number(phase.blocks),
                "%blocks%", Text.number(island.blocks), "%level%", String.valueOf(mode.level(island)), "%cycle%", String.valueOf(island.cycle),
                "%members%", island.size() + "/" + mode.maxMembers(island), "%size%", String.valueOf(mode.radius(island) * 2 + 1),
                "%money%", mode.money(mode.core().economy().balance(player, mode.currency())))).build());
        menu.set(19, Items.of(Material.RED_BED).name(t("home")).lore(lines("home-lore")).build(), clicker -> {
            clicker.closeInventory();
            mode.goHome(clicker);
        });
        menu.set(20, Items.of(phase.icon).name(t("phases")).lore(lines("phases-lore", "%phase%", phase.name)).hideAll().build(), this::phases);
        menu.set(21, Items.of(Material.ANVIL).name(t("upgrades")).lore(lines("upgrades-lore")).build(), this::upgrades);
        menu.set(22, Items.of(Material.EMERALD).name(t("market")).lore(lines("market-lore")).build(), this::market);
        menu.set(23, Items.of(Material.PLAYER_HEAD).name(t("members")).lore(lines("members-lore", "%members%", island.size() + "/" + mode.maxMembers(island))).build(), this::members);
        menu.set(24, Items.of(Material.COMPARATOR).name(t("settings")).lore(lines("settings-lore")).build(), this::settings);
        menu.set(25, Items.of(Material.GOLDEN_HELMET).name(t("top")).lore(lines("top-lore")).hideAll().build(), this::top);
        menu.set(30, Items.of(Material.COMPASS).name(t("warps")).lore(lines("warps-lore")).build(), clicker -> mode.battle().warpMenu(clicker));
        menu.set(31, Items.of(Material.ENDER_EYE).name(t("visit")).lore(lines("visit-lore")).build(), this::visitList);
        menu.set(32, Items.of(Material.ENCHANTING_TABLE).name(t("enchanter")).lore(lines("enchanter-lore")).build(), clicker -> mode.enchants().open(clicker));
        menu.set(40, Items.of(Material.NETHER_STAR).name(t("lobby")).build(), clicker -> {
            clicker.closeInventory();
            mode.core().modes().sendAndReport(clicker, hu.icemine.core.mode.ModeService.LOBBY);
        });
        menu.open(player);
    }

    void phases(Player player) {
        FyIsland island = mode.islandOf(player);
        if (island == null) {
            return;
        }
        List<FyPhase> phases = mode.phases();
        int rows = Math.max(3, Math.min(6, (phases.size() + 6) / 7 + 2));
        Menu menu = new Menu("fyland-phases", rows, t("phases-title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        int current = Math.floorMod(island.phase, phases.size());
        for (int i = 0; i < phases.size() && i < INNER.length; i++) {
            if (INNER[i] >= menu.size() - 9) {
                break;
            }
            FyPhase phase = phases.get(i);
            List<String> lore = new ArrayList<>();
            for (String line : phase.description) {
                lore.add(line);
            }
            lore.add("");
            lore.add(t("phase-blocks", "%blocks%", Text.number(phase.blocks)));
            Items item;
            if (i < current || island.cycle > 1 && i != current) {
                lore.add(t("phase-done"));
                item = Items.of(phase.icon);
            } else if (i == current) {
                lore.add(t("phase-now", "%done%", Text.number(island.phaseBlocks), "%total%", Text.number(phase.blocks)));
                lore.add(Text.bar(island.phaseBlocks / (double) phase.blocks, 20, "&a|", "&8|"));
                item = Items.of(phase.icon).glow();
            } else {
                lore.add(t("phase-locked"));
                item = Items.of(Material.GRAY_STAINED_GLASS);
            }
            menu.set(INNER[i], item.name((i + 1) + ". " + phase.name).lore(lore).hideAll().build());
        }
        back(menu, menu.size() - 5);
        menu.open(player);
    }

    void upgrades(Player player) {
        FyIsland island = mode.islandOf(player);
        if (island == null) {
            return;
        }
        Menu menu = new Menu("fyland-upgrades", 4, t("upgrades-title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        int[] slots = {11, 13, 15, 20, 22, 24};
        int index = 0;
        for (FylandMode.Upgrade upgrade : mode.upgrades().values()) {
            if (index >= slots.length) {
                break;
            }
            int level = island.level(upgrade.id());
            List<String> lore = new ArrayList<>(upgrade.description());
            lore.add("");
            lore.add(t("upgrade-level", "%level%", String.valueOf(level), "%max%", String.valueOf(upgrade.max())));
            lore.add(t("upgrade-now", "%value%", format(upgrade, upgrade.value(level))));
            if (level < upgrade.max()) {
                lore.add(t("upgrade-next", "%value%", format(upgrade, upgrade.value(level + 1))));
                lore.add(t("upgrade-price", "%price%", mode.money(upgrade.prices().get(level))));
                lore.add("");
                lore.add(t("upgrade-click"));
            } else {
                lore.add(t("upgrade-maxed"));
            }
            String id = upgrade.id();
            menu.set(slots[index++], Items.of(upgrade.icon()).name(upgrade.name()).lore(lore).hideAll().build(), clicker -> {
                mode.buyUpgrade(clicker, id);
                upgrades(clicker);
            });
        }
        menu.set(31, Items.of(Material.GOLD_INGOT).name(t("balance", "%money%", mode.money(mode.core().economy().balance(player, mode.currency())))).build());
        back(menu, 27);
        menu.open(player);
    }

    private static String format(FylandMode.Upgrade upgrade, double value) {
        String number = value == Math.rint(value) ? String.valueOf((long) value) : Text.decimal(value);
        if (upgrade.format().contains("%percent%")) {
            return upgrade.format().replace("%percent%", Text.decimal(value * 100));
        }
        if (upgrade.format().contains("%size%")) {
            return upgrade.format().replace("%size%", String.valueOf((long) value * 2 + 1));
        }
        return upgrade.format().replace("%value%", number);
    }

    void market(Player player) {
        Menu menu = new Menu("fyland-market", 6, t("market-title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        int slot = 0;
        for (Map.Entry<Material, Long> entry : mode.sellPrices().entrySet()) {
            int count = mode.count(player, entry.getKey());
            if (count <= 0) {
                continue;
            }
            if (slot >= INNER.length) {
                break;
            }
            Material material = entry.getKey();
            long value = entry.getValue();
            menu.set(INNER[slot++], Items.of(material, Math.min(64, count)).name("&f" + mode.itemName(material)).lore(lines("sell-lore",
                    "%price%", mode.money(value), "%count%", Text.number(count), "%total%", mode.money(value * count))).build(), clicker -> {
                mode.sell(clicker, material);
                market(clicker);
            });
        }
        if (slot == 0) {
            menu.set(22, Items.of(Material.PAPER).name(t("sell-empty")).lore(lines("sell-empty-lore")).build());
        }
        menu.set(48, Items.of(Material.HOPPER).name(t("sell-all")).lore(lines("sell-all-lore")).build(), clicker -> {
            mode.sell(clicker, null);
            market(clicker);
        });
        menu.set(49, Items.of(Material.GOLD_INGOT).name(t("balance", "%money%", mode.money(mode.core().economy().balance(player, mode.currency())))).build());
        menu.set(50, Items.of(Material.CHEST).name(t("buy")).lore(lines("buy-lore")).build(), this::buy);
        back(menu, 45);
        menu.open(player);
    }

    void buy(Player player) {
        List<FylandMode.Offer> offers = mode.buyOffers();
        int rows = Math.max(3, Math.min(6, (offers.size() + 6) / 7 + 2));
        Menu menu = new Menu("fyland-buy", rows, t("buy-title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        for (int i = 0; i < offers.size() && i < INNER.length; i++) {
            if (INNER[i] >= menu.size() - 9) {
                break;
            }
            FylandMode.Offer offer = offers.get(i);
            boolean stack = offer.item().getMaxStackSize() > 1;
            menu.set(INNER[i], Items.of(offer.item().getType(), offer.item().getAmount()).name("&f" + mode.itemName(offer.item().getType()))
                    .lore(lines(stack ? "buy-offer-stack" : "buy-offer", "%price%", mode.money(offer.price()), "%amount%", String.valueOf(offer.item().getAmount() * 4))).build(),
                    clicker -> {
                        mode.buy(clicker, offer, 1);
                        buy(clicker);
                    }, clicker -> {
                        mode.buy(clicker, offer, stack ? 4 : 1);
                        buy(clicker);
                    });
        }
        menu.set(menu.size() - 5, Items.of(Material.ARROW).name(t("back")).build(), this::market);
        menu.set(menu.size() - 1, Items.of(Material.GOLD_INGOT).name(t("balance", "%money%", mode.money(mode.core().economy().balance(player, mode.currency())))).build());
        menu.open(player);
    }

    void members(Player player) {
        FyIsland island = mode.islandOf(player);
        if (island == null) {
            return;
        }
        boolean owner = island.owner.equals(player.getUniqueId());
        boolean manager = island.manager(player.getUniqueId());
        Menu menu = new Menu("fyland-members", 4, t("members-title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        menu.set(10, Items.head(Bukkit.getOfflinePlayer(island.owner)).name(t("member-owner", "%player%", island.ownerName)).lore(lines("member-owner-lore")).build());
        int slot = 11;
        for (Map.Entry<UUID, String> entry : island.members.entrySet()) {
            if (slot == 17) {
                slot = 19;
            }
            if (slot > 25) {
                break;
            }
            String name = entry.getValue();
            boolean online = Bukkit.getPlayer(entry.getKey()) != null;
            boolean moderator = island.moderators.contains(entry.getKey());
            List<String> lore = new ArrayList<>(lines(online ? "member-online" : "member-offline"));
            if (moderator) {
                lore.add(0, t("member-moderator"));
            }
            if (owner) {
                lore.addAll(lines(moderator ? "member-manage-demote" : "member-manage-promote"));
            } else if (manager && !moderator) {
                lore.addAll(lines("member-manage-kick"));
            }
            UUID id = entry.getKey();
            menu.set(slot++, Items.head(Bukkit.getOfflinePlayer(entry.getKey())).name((moderator ? "&b" : "&f") + name).lore(lore).build(),
                    manager ? clicker -> {
                        mode.kick(clicker, name);
                        members(clicker);
                    } : null,
                    owner ? clicker -> {
                        if (clicker.isSneaking()) {
                            mode.transfer(clicker, name);
                        } else {
                            mode.social().promote(clicker, name, !island.moderators.contains(id));
                        }
                        members(clicker);
                    } : null);
        }
        menu.set(31, Items.of(Material.WRITABLE_BOOK).name(t("invite")).lore(lines("invite-lore", "%members%", island.size() + "/" + mode.maxMembers(island))).build(), clicker -> {
            clicker.closeInventory();
            mode.click(clicker, "invite-how");
        });
        if (!owner) {
            menu.set(35, Items.of(Material.BARRIER).name(t("leave")).lore(lines("leave-lore")).build(), clicker -> {
                clicker.closeInventory();
                mode.leaveIsland(clicker);
            });
        }
        back(menu, 27);
        menu.open(player);
    }

    void settings(Player player) {
        FyIsland island = mode.islandOf(player);
        if (island == null) {
            return;
        }
        boolean owner = island.owner.equals(player.getUniqueId());
        Menu menu = new Menu("fyland-settings", 3, t("settings-title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        menu.set(11, Items.of(island.visitors ? Material.OAK_DOOR : Material.IRON_DOOR).name(t(island.visitors ? "visitors-open" : "visitors-closed"))
                .lore(lines(island.manager(player.getUniqueId()) ? "visitors-lore" : "owner-only-lore")).build(), clicker -> {
            mode.toggleVisitors(clicker);
            settings(clicker);
        });
        menu.set(12, Items.of(Material.RED_BED).name(t("sethome")).lore(lines("sethome-lore")).build(), clicker -> {
            clicker.closeInventory();
            mode.setHome(clicker);
        });
        menu.set(13, Items.of(Material.OAK_SIGN).name(t("setwarp")).lore(lines(island.hasWarp ? "setwarp-lore-set" : "setwarp-lore",
                "%name%", island.warpName.isBlank() ? "-" : island.warpName)).build(), clicker -> {
            clicker.closeInventory();
            mode.social().setWarp(clicker, null);
        });
        menu.set(14, Items.of(Material.EXPERIENCE_BOTTLE).name(t("recalc")).lore(lines("recalc-lore", "%worth%", Text.number(island.worth),
                "%level%", String.valueOf(mode.level(island)))).build(), clicker -> {
            clicker.closeInventory();
            mode.worth().recalc(clicker);
        });
        menu.set(16, Items.of(Material.TNT).name(t("reset")).lore(lines(owner ? "reset-lore" : "owner-only-lore")).build(), clicker -> {
            clicker.closeInventory();
            mode.reset(clicker);
        });
        back(menu, 22);
        menu.open(player);
    }

    void top(Player player) {
        Menu menu = new Menu("fyland-top", 4, t("top-title"));
        menu.frame(Material.YELLOW_STAINED_GLASS_PANE, null);
        int[] slots = {13, 21, 22, 23, 19, 20, 24, 25, 11, 15};
        List<FyIsland> top = mode.top(slots.length);
        for (int i = 0; i < top.size(); i++) {
            FyIsland island = top.get(i);
            FyPhase phase = mode.phase(island);
            String label = island.display();
            menu.set(slots[i], Items.head(Bukkit.getOfflinePlayer(island.owner)).name(t("top-entry", "%place%", String.valueOf(i + 1), "%owner%", island.display()))
                    .lore(lines("top-entry-lore", "%blocks%", Text.number(island.blocks), "%level%", String.valueOf(mode.level(island)), "%worth%", Text.number(island.worth),
                            "%phase%", phase.name, "%members%", String.valueOf(island.size()), "%cycle%", String.valueOf(island.cycle))).build(), clicker -> {
                clicker.closeInventory();
                mode.visit(clicker, island, label);
            });
        }
        if (top.isEmpty()) {
            menu.set(13, Items.of(Material.PAPER).name(t("top-empty")).build());
        }
        back(menu, 27);
        menu.open(player);
    }

    void visitList(Player player) {
        Menu menu = new Menu("fyland-visit", 4, t("visit-title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        int slot = 0;
        List<FyIsland> seen = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            FyIsland island = mode.islandOf(online);
            if (island == null || seen.contains(island) || !mode.social().visitable(island, player) || island.member(player.getUniqueId())) {
                continue;
            }
            seen.add(island);
            if (slot >= 14) {
                break;
            }
            String label = island.display();
            menu.set(INNER[slot++], Items.head(Bukkit.getOfflinePlayer(island.owner)).name("&f" + island.display())
                    .lore(lines("visit-entry-lore", "%phase%", mode.phase(island).name, "%blocks%", Text.number(island.blocks))).build(), clicker -> {
                clicker.closeInventory();
                mode.visit(clicker, island, label);
            });
        }
        if (slot == 0) {
            menu.set(13, Items.of(Material.PAPER).name(t("visit-empty")).build());
        }
        back(menu, 27);
        menu.open(player);
    }
}
