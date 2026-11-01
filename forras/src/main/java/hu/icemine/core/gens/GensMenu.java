package hu.icemine.core.gens;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.economy.Ledger;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class GensMenu {

    private final IceMineCore core;
    private final GensMode gens;

    public GensMenu(IceMineCore core, GensMode gens) {
        this.core = core;
        this.gens = gens;
    }

    public void open(Player player) {
        GensData.Player data = gens.data(player);
        if (data == null || player.getWorld() != gens.world()) {
            core.messages().send(player, "gens.only-in-gens");
            return;
        }
        String currency = gens.currency();
        GensProgress progress = gens.progress();
        Menu menu = new Menu("gens", 5, core.messages().raw("gens.menu.title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        Material hoeIcon = Items.material(HoeUpgrades.MATERIALS[gens.hoe().tier(data.hoe)], Material.WOODEN_HOE);
        menu.set(10, Items.of(hoeIcon).name(core.messages().raw("gens.menu.hoe.name"))
                .lore(core.messages().list("gens.menu.hoe.lore", "%hoe%", gens.hoeName(data), "%level%", String.valueOf(gens.hoe().total(data.hoe)),
                        "%area%", core.messages().raw("gens.hoe.area." + gens.hoe().radius(data.hoe)))).hideAll().build(), this::hoe);
        menu.set(11, fieldItem(data), clicker -> {
            gens.unlockField(clicker);
            open(clicker);
        });
        menu.set(12, Items.of(Material.ANVIL).name(core.messages().raw("gens.menu.upgrades.name"))
                .lore(core.messages().list("gens.menu.upgrades.lore")).build(), this::upgrades);
        hu.icemine.core.data.PlayerProfile profile = core.profiles().profile(player);
        menu.set(13, Items.head(player).name("&a&l" + player.getName()).lore(core.messages().list("gens.menu.info",
                "%money%", core.economy().format(currency, core.economy().balance(player, currency)),
                "%fields%", String.valueOf(gens.unlocked(data)), "%max_fields%", String.valueOf(gens.fields().size()),
                "%production%", Text.number(gens.productionPerMinute(data)), "%plot%", "#" + data.plot,
                "%harvested%", Text.number(profile == null ? 0 : profile.stat("gens.harvested")),
                "%prestige%", String.valueOf(data.prestige), "%rebirth%", String.valueOf(data.rebirth),
                "%multiplier%", String.format(Locale.ROOT, "%.2fx", gens.multiplier(data)))).build());
        menu.set(14, Items.of(Material.GOLD_INGOT).name(core.messages().raw("gens.menu.sell.name")).lore(core.messages().list("gens.menu.sell.lore")).build(), clicker -> {
            gens.sellAll(clicker);
            open(clicker);
        });
        boolean unlocked = gens.autosellUnlocked(player, data);
        List<String> autoLore = new ArrayList<>(core.messages().list("gens.menu.autosell.lore"));
        autoLore.add("");
        if (!unlocked) {
            autoLore.add(core.messages().raw("gens.menu.autosell.buy").replace("%price%", core.economy().format(currency, progress.autosellPrice())));
        } else {
            autoLore.add(core.messages().raw(data.autosell ? "settings.on" : "settings.off"));
            autoLore.add(core.messages().raw("gens.menu.toggle"));
        }
        Items auto = Items.of(Material.HOPPER).name(core.messages().raw("gens.menu.autosell.name")).lore(autoLore);
        if (data.autosell) {
            auto.glow();
        }
        menu.set(15, auto.build(), clicker -> {
            gens.toggleAutosell(clicker);
            open(clicker);
        });
        boolean daily = gens.dailyAvailable(player);
        Items dailyItem = Items.of(daily ? Material.CHEST : Material.ENDER_CHEST).name(core.messages().raw("gens.menu.daily.name"))
                .lore(core.messages().list(daily ? "gens.menu.daily.ready" : "gens.menu.daily.taken", "%money%", core.economy().format(currency, gens.dailyAmount())));
        if (daily) {
            dailyItem.glow();
        }
        menu.set(16, dailyItem.build(), clicker -> {
            gens.daily(clicker);
            open(clicker);
        });
        menu.set(19, prestigeItem(player, data), clicker -> {
            gens.prestige(clicker);
            if (clicker.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                open(clicker);
            }
        });
        menu.set(20, rebirthItem(player, data), clicker -> {
            gens.rebirth(clicker);
            if (clicker.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                open(clicker);
            }
        });
        boolean large = gens.largeBackpack(player, data);
        List<String> packLore = new ArrayList<>(core.messages().list("gens.menu.backpack.lore", "%size%", String.valueOf(progress.backpackSize(large))));
        menu.set(22, Items.of(Material.CHEST).name(core.messages().raw("gens.menu.backpack.name")).lore(packLore).build(), clicker -> {
            clicker.closeInventory();
            gens.openBackpack(clicker);
        });
        if (!large && progress.backpackPrice() > 0) {
            menu.set(23, Items.of(Material.BARREL).name(core.messages().raw("gens.menu.backpack.upgrade-name"))
                    .lore(core.messages().list("gens.menu.backpack.upgrade", "%price%", core.economy().format(currency, progress.backpackPrice()),
                            "%size%", String.valueOf(progress.backpackSize(true)))).build(), clicker -> {
                gens.buyLargeBackpack(clicker);
                open(clicker);
            });
        }
        menu.set(24, Items.of(Material.PLAYER_HEAD).name(core.messages().raw("gens.menu.members.name"))
                .lore(core.messages().list("gens.menu.members.lore", "%count%", String.valueOf(data.trusted.size()))).build(), this::members);
        menu.set(25, Items.of(data.locked ? Material.IRON_DOOR : Material.OAK_DOOR).name(core.messages().raw("gens.menu.lock.name"))
                .lore(core.messages().list(data.locked ? "gens.menu.lock.locked" : "gens.menu.lock.open")).build(), clicker -> {
            gens.toggleLock(clicker);
            open(clicker);
        });
        menu.set(28, Items.of(Material.WRITABLE_BOOK).name(core.messages().raw("gens.menu.quests.name")).lore(core.messages().list("gens.menu.quests.lore")).build(), this::quests);
        menu.set(30, Items.of(Material.EMERALD).name(core.messages().raw("gens.menu.shop.name")).lore(core.messages().list("gens.menu.shop.lore")).build(), this::shop);
        menu.set(32, Items.of(Material.RED_BED).name(core.messages().raw("gens.menu.home.name")).lore(core.messages().list("gens.menu.home.lore")).build(), clicker -> {
            clicker.closeInventory();
            gens.goHome(clicker);
        });
        menu.set(34, Items.of(Material.BOOK).name(core.messages().raw("gens.menu.crops.name")).lore(cropLore(data)).build());
        menu.open(player);
    }

    private ItemStack prestigeItem(Player player, GensData.Player data) {
        GensProgress progress = gens.progress();
        List<String> lore = new ArrayList<>(core.messages().list("gens.menu.prestige.lore", "%prestige%", String.valueOf(data.prestige),
                "%max%", String.valueOf(progress.prestigeMax()), "%bonus%", Text.decimal(progress.prestigeBonus())));
        lore.add("");
        if (data.prestige >= progress.prestigeMax()) {
            lore.add(core.messages().raw("gens.menu.prestige.maxed"));
        } else {
            lore.add(core.messages().raw("gens.menu.prestige.price").replace("%price%",
                    core.economy().format(gens.currency(), progress.prestigeCost(data.prestige, data.rebirth))));
            lore.add(core.messages().raw(progress.canPrestige(gens.unlocked(data), data.prestige) ? "gens.menu.prestige.ready" : "gens.menu.prestige.need")
                    .replace("%level%", String.valueOf(progress.prestigeLevel())));
        }
        Items item = Items.of(Material.NETHER_STAR).name(core.messages().raw("gens.menu.prestige.name")).lore(lore);
        if (progress.canPrestige(gens.unlocked(data), data.prestige)) {
            item.glow();
        }
        return item.build();
    }

    private ItemStack rebirthItem(Player player, GensData.Player data) {
        GensProgress progress = gens.progress();
        List<String> lore = new ArrayList<>(core.messages().list("gens.menu.rebirth.lore", "%rebirth%", String.valueOf(data.rebirth),
                "%bonus%", Text.decimal(progress.rebirthBonus()), "%need%", String.valueOf(progress.rebirthPrestige())));
        lore.add("");
        lore.add(core.messages().raw("gens.menu.rebirth.price").replace("%price%", core.economy().format(gens.currency(), progress.rebirthCost(data.rebirth))));
        boolean ready = progress.canRebirth(data.prestige, data.rebirth);
        lore.add(core.messages().raw(ready ? "gens.menu.rebirth.ready" : "gens.menu.rebirth.need").replace("%need%", String.valueOf(progress.rebirthPrestige())));
        Items item = Items.of(Material.END_CRYSTAL).name(core.messages().raw("gens.menu.rebirth.name")).lore(lore);
        if (ready) {
            item.glow();
        }
        return item.build();
    }

    /** A következő föld megnyitása (vagy hogy már mind megvan). */
    private ItemStack fieldItem(GensData.Player data) {
        GensData.Field next = gens.nextField(data);
        String currency = gens.currency();
        List<String> lore = new ArrayList<>(core.messages().list("gens.menu.field.lore", "%count%", String.valueOf(gens.unlocked(data)),
                "%max%", String.valueOf(gens.fields().size())));
        if (next == null) {
            lore.add(core.messages().raw("gens.menu.field.max"));
            return Items.of(Material.HAY_BLOCK).name(core.messages().raw("gens.menu.field.name")).lore(lore).hideAll().build();
        }
        lore.addAll(core.messages().list("gens.menu.field.next", "%field%", next.name, "%value%", core.economy().format(currency, next.value),
                "%regrow%", String.valueOf(next.regrow), "%price%", core.economy().format(currency, next.price)));
        return Items.of(Items.material(next.crop.drop, Material.WHEAT_SEEDS)).name(core.messages().raw("gens.menu.field.name")).lore(lore).hideAll().build();
    }

    /** A kapa fejlesztései: Bőség, Kaszálás, Aranykéz, Szerencse. */
    public void hoe(Player player) {
        GensData.Player data = gens.data(player);
        if (data == null || player.getWorld() != gens.world()) {
            core.messages().send(player, "gens.only-in-gens");
            return;
        }
        HoeUpgrades upgrades = gens.hoe();
        Menu menu = new Menu("gens-hoe", 4, core.messages().raw("gens.hoe.title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        menu.set(4, gens.hoeItem(data));
        int[] slots = {10, 12, 14, 16};
        int index = 0;
        for (HoeUpgrades.Enchant enchant : upgrades.enchants().values()) {
            if (index >= slots.length) {
                break;
            }
            int level = data.hoe.getOrDefault(enchant.id, 0);
            List<String> lore = new ArrayList<>(enchant.description);
            lore.add("");
            lore.add(core.messages().raw("gens.hoe.now").replace("%value%", effect(upgrades, enchant.id, data)));
            lore.add(core.messages().raw("upgrade.level").replace("%level%", String.valueOf(level)).replace("%max%", String.valueOf(enchant.max))
                    + " " + Text.bar(enchant.max == 0 ? 1 : level / (double) enchant.max, Math.min(20, Math.max(3, enchant.max)), "&a▌", "&8▌"));
            long price = enchant.priceFor(level);
            if (price < 0) {
                lore.add(core.messages().raw("upgrade.lore-max"));
            } else {
                lore.add(core.messages().raw("upgrade.price").replace("%price%", core.economy().format(gens.currency(), price)));
                lore.add(core.messages().raw("upgrade.click"));
            }
            Items item = Items.of(Items.material(enchant.icon, Material.ENCHANTED_BOOK)).name(enchant.name).lore(lore).hideAll();
            if (level > 0) {
                item.glow();
            }
            String id = enchant.id;
            menu.set(slots[index++], item.build(), clicker -> {
                gens.buyHoeUpgrade(clicker, id);
                hoe(clicker);
            });
        }
        int tier = upgrades.tier(data.hoe);
        List<String> tierLore = new ArrayList<>(core.messages().list("gens.hoe.tier-info", "%hoe%", gens.hoeName(data),
                "%level%", String.valueOf(upgrades.total(data.hoe))));
        if (tier + 1 < HoeUpgrades.MATERIALS.length) {
            List<String> names = core.messages().list("gens.hoe.tiers");
            String next = tier + 1 < names.size() ? names.get(tier + 1) : HoeUpgrades.MATERIALS[tier + 1];
            tierLore.add(core.messages().raw("gens.hoe.tier-next").replace("%hoe%", next)
                    .replace("%need%", String.valueOf(upgrades.levelsFor(tier + 1) - upgrades.total(data.hoe))));
        } else {
            tierLore.add(core.messages().raw("gens.hoe.tier-max"));
        }
        menu.set(22, Items.of(Material.EXPERIENCE_BOTTLE).name(core.messages().raw("gens.hoe.tier-name")).lore(tierLore).build());
        menu.set(31, Items.of(Material.ARROW).name(core.messages().raw("menu.back")).build(), this::open);
        menu.open(player);
    }

    private String effect(HoeUpgrades upgrades, String id, GensData.Player data) {
        switch (id) {
            case "boseg":
                return "+" + Text.decimal(upgrades.extraPercent(data.hoe)) + "%";
            case "kaszalas":
                return core.messages().raw("gens.hoe.area." + upgrades.radius(data.hoe));
            case "aranykez":
                return "+" + Text.decimal(upgrades.valuePercent(data.hoe)) + "%";
            default:
                return Text.decimal(upgrades.luckPercent(data.hoe)) + "%";
        }
    }

    public void upgrades(Player player) {
        GensData.Player data = gens.data(player);
        if (data == null) {
            return;
        }
        GensProgress progress = gens.progress();
        Menu menu = new Menu("gens-upgrades", 3, core.messages().raw("gens.upgrade.title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        int slot = 10;
        for (GensProgress.IslandUpgrade upgrade : progress.upgrades().values()) {
            if (slot > 16) {
                break;
            }
            int level = data.upgrades.getOrDefault(upgrade.id, 0);
            List<String> lore = new ArrayList<>(upgrade.description);
            lore.add("");
            lore.add(core.messages().raw("upgrade.level").replace("%level%", String.valueOf(level)).replace("%max%", String.valueOf(upgrade.maxLevel()))
                    + " " + Text.bar(level / (double) upgrade.maxLevel(), Math.min(20, upgrade.maxLevel() * 2), "&a▌", "&8▌"));
            long price = upgrade.priceFor(level);
            if (price < 0) {
                lore.add(core.messages().raw("upgrade.lore-max"));
            } else {
                lore.add(core.messages().raw("upgrade.price").replace("%price%", core.economy().format(gens.currency(), price)));
                lore.add(core.messages().raw("upgrade.click"));
            }
            Items item = Items.of(Items.material(upgrade.icon, Material.ANVIL)).name(upgrade.name).lore(lore).hideAll();
            if (level > 0) {
                item.glow();
            }
            String id = upgrade.id;
            menu.set(slot, item.build(), clicker -> {
                gens.buyIslandUpgrade(clicker, id);
                upgrades(clicker);
            });
            slot += 2;
        }
        menu.set(22, Items.of(Material.ARROW).name(core.messages().raw("menu.back")).build(), this::open);
        menu.open(player);
    }

    public void members(Player player) {
        GensData.Player data = gens.data(player);
        if (data == null) {
            return;
        }
        Menu menu = new Menu("gens-members", 4, core.messages().raw("gens.members.title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        int slot = 10;
        for (java.util.UUID member : data.trusted) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot > 25) {
                break;
            }
            String name = gens.nameOf(member);
            List<String> lore = new ArrayList<>();
            int flags = data.permissionsOf(member);
            lore.add(flagLine("gens.members.build", (flags & GensData.BUILD) != 0));
            lore.add(flagLine("gens.members.containers", (flags & GensData.CONTAINERS) != 0));
            lore.add(flagLine("gens.members.collect", (flags & GensData.COLLECT) != 0));
            lore.add("");
            lore.add(core.messages().raw("gens.members.click"));
            menu.set(slot++, Items.head(org.bukkit.Bukkit.getOfflinePlayer(member)).name("&a" + name).lore(lore).build(), clicker -> member(clicker, member));
        }
        if (data.trusted.isEmpty()) {
            menu.set(13, Items.of(Material.PAPER).name(core.messages().raw("gens.members.empty")).lore(core.messages().list("gens.members.hint")).build());
        }
        menu.set(31, Items.of(Material.ARROW).name(core.messages().raw("menu.back")).build(), this::open);
        menu.open(player);
    }

    private String flagLine(String key, boolean on) {
        return core.messages().raw(key) + " " + core.messages().raw(on ? "settings.on" : "settings.off");
    }

    private void member(Player player, java.util.UUID member) {
        GensData.Player data = gens.data(player);
        if (data == null || !data.trusted.contains(member)) {
            members(player);
            return;
        }
        Menu menu = new Menu("gens-member", 3, core.messages().raw("gens.members.title-one").replace("%player%", gens.nameOf(member)));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        int flags = data.permissionsOf(member);
        Object[][] toggles = {{10, GensData.BUILD, "gens.members.build", Material.BRICKS}, {11, GensData.CONTAINERS, "gens.members.containers", Material.CHEST},
                {12, GensData.COLLECT, "gens.members.collect", Material.IRON_HOE}};
        for (Object[] toggle : toggles) {
            int flag = (Integer) toggle[1];
            boolean on = (flags & flag) != 0;
            Items item = Items.of((Material) toggle[3]).name(core.messages().raw((String) toggle[2]))
                    .lore(core.messages().raw(on ? "settings.on" : "settings.off"), core.messages().raw("gens.menu.toggle"));
            if (on) {
                item.glow();
            }
            menu.set((Integer) toggle[0], item.build(), clicker -> {
                gens.togglePermission(clicker, member, flag);
                member(clicker, member);
            });
        }
        menu.set(16, Items.of(Material.BARRIER).name(core.messages().raw("gens.members.remove")).build(), clicker -> {
            gens.removeMember(clicker, member);
            members(clicker);
        });
        menu.set(22, Items.of(Material.ARROW).name(core.messages().raw("menu.back")).build(), this::members);
        menu.open(player);
    }

    /** A földek listája: mi terem rajta, mennyit ér, és nyitva van-e már. */
    private List<String> cropLore(GensData.Player data) {
        List<String> lore = new ArrayList<>();
        int open = gens.unlocked(data);
        for (GensData.Field field : gens.fields()) {
            String key = field.index < open ? "gens.menu.crops.open" : "gens.menu.crops.locked";
            lore.add(Text.replace(core.messages().raw(key), "%number%", String.valueOf(field.index + 1), "%field%", field.name,
                    "%value%", core.economy().format(gens.currency(), field.value), "%regrow%", String.valueOf(field.regrow),
                    "%price%", core.economy().format(gens.currency(), field.price)));
        }
        lore.add("");
        lore.addAll(core.messages().list("gens.menu.crops.hint"));
        return lore;
    }

    public void quests(Player player) {
        GensData.Player data = gens.data(player);
        if (data == null) {
            return;
        }
        String day = LocalDate.now().toString();
        gens.quests().roll(data, day);
        Menu menu = new Menu("gens-quests", 3, core.messages().raw("gens.quests.title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        int slot = 11;
        for (QuestBook.Quest quest : gens.quests().today(player.getUniqueId(), day)) {
            if (slot > 15) {
                break;
            }
            long progress = gens.quests().progress(data, quest);
            boolean done = data.claimed.contains(quest.id);
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("&8[" + Text.bar(progress / (double) quest.amount, 20, "&a|", "&7|") + "&8]");
            lore.add("&7" + Text.number(progress) + " / " + Text.number(quest.amount));
            lore.add("");
            lore.add(core.messages().raw("gens.quests.reward").replace("%reward%", core.economy().format(gens.currency(), quest.reward)));
            lore.add(core.messages().raw(done ? "gens.quests.done" : "gens.quests.open"));
            Items item = Items.of(done ? Material.LIME_DYE : Material.PAPER).name("&f" + quest.name).lore(lore);
            if (done) {
                item.glow();
            }
            menu.set(slot, item.build());
            slot += 2;
        }
        menu.set(22, Items.of(Material.ARROW).name(core.messages().raw("menu.back")).build(), this::open);
        menu.open(player);
    }

    public void shop(Player player) {
        Menu menu = new Menu("gens-shop", 4, core.messages().raw("gens.shop.title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        ConfigurationSection section = core.configs().get("gens").getConfigurationSection("shop");
        int slot = 10;
        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (slot % 9 == 8) {
                    slot += 2;
                }
                if (slot > 25) {
                    break;
                }
                Material material = Items.material(section.getString(id + ".material", "STONE"), Material.STONE);
                int amount = Math.max(1, Math.min(64, section.getInt(id + ".amount", 16)));
                long price = Math.max(0, section.getLong(id + ".price", 100));
                String name = section.getString(id + ".name", id);
                menu.set(slot++, Items.of(material, amount).name(name).lore(core.messages().list("gens.shop.lore", "%amount%", String.valueOf(amount),
                        "%price%", core.economy().format(gens.currency(), price))).build(), clicker -> {
                    if (clicker.getInventory().firstEmpty() < 0) {
                        core.messages().send(clicker, "gens.inventory-full");
                        return;
                    }
                    if (core.economy().withdraw(clicker, gens.currency(), price, "gens:shop") != Ledger.Result.OK) {
                        core.messages().send(clicker, "gens.no-money", "%price%", core.economy().format(gens.currency(), price));
                        return;
                    }
                    clicker.getInventory().addItem(new ItemStack(material, amount));
                    core.messages().send(clicker, "gens.shop.bought", "%name%", name, "%price%", core.economy().format(gens.currency(), price));
                });
            }
        }
        menu.set(31, Items.of(Material.ARROW).name(core.messages().raw("menu.back")).build(), this::open);
        menu.open(player);
    }
}
