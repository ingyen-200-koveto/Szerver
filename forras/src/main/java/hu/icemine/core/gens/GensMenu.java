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
        Menu menu = new Menu("gens", 5, core.messages().raw("gens.menu.title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        GensData.Tier first = gens.tiers().isEmpty() ? null : gens.tiers().firstEntry().getValue();
        menu.set(10, Items.of(first == null ? Material.COAL_BLOCK : first.block).name(core.messages().raw("gens.menu.buy.name"))
                .lore(core.messages().list("gens.menu.buy.lore", "%price%", core.economy().format(currency, gens.generatorPrice()))).hideAll().build(), clicker -> {
            gens.buyGenerator(clicker);
            open(clicker);
        });
        GensData.Level level = gens.level(data);
        GensData.Level next = gens.nextLevel(data);
        List<String> levelLore = new ArrayList<>(core.messages().list("gens.menu.level.lore",
                "%level%", String.valueOf(data.level), "%slots%", String.valueOf(level.slots),
                "%multiplier%", String.format(Locale.ROOT, "%.2fx", level.multiplier)));
        if (next == null) {
            levelLore.add(core.messages().raw("gens.menu.level.max"));
        } else {
            levelLore.addAll(core.messages().list("gens.menu.level.next", "%level%", String.valueOf(next.level), "%slots%", String.valueOf(next.slots),
                    "%multiplier%", String.format(Locale.ROOT, "%.2fx", next.multiplier), "%price%", core.economy().format(currency, next.price)));
        }
        menu.set(12, Items.of(Material.EXPERIENCE_BOTTLE).name(core.messages().raw("gens.menu.level.name")).lore(levelLore).build(), clicker -> {
            gens.upgradePlot(clicker);
            open(clicker);
        });
        menu.set(14, Items.of(Material.GOLD_INGOT).name(core.messages().raw("gens.menu.sell.name")).lore(core.messages().list("gens.menu.sell.lore")).build(), clicker -> {
            gens.sellAll(clicker);
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
        menu.set(28, Items.of(Material.WRITABLE_BOOK).name(core.messages().raw("gens.menu.quests.name")).lore(core.messages().list("gens.menu.quests.lore")).build(), this::quests);
        menu.set(30, Items.of(Material.EMERALD).name(core.messages().raw("gens.menu.shop.name")).lore(core.messages().list("gens.menu.shop.lore")).build(), this::shop);
        menu.set(32, Items.of(Material.RED_BED).name(core.messages().raw("gens.menu.home.name")).lore(core.messages().list("gens.menu.home.lore")).build(), clicker -> {
            clicker.closeInventory();
            gens.goHome(clicker);
        });
        menu.set(34, Items.of(Material.BOOK).name(core.messages().raw("gens.menu.tiers.name")).lore(tierLore()).build());
        menu.set(22, Items.head(player).name("&a&l" + player.getName()).lore(core.messages().list("gens.menu.info",
                "%money%", core.economy().format(currency, core.economy().balance(player, currency)),
                "%generators%", String.valueOf(data.generators.size()), "%slots%", String.valueOf(level.slots),
                "%production%", Text.number(gens.productionPerMinute(data)), "%plot%", "#" + data.plot)).build());
        menu.open(player);
    }

    private List<String> tierLore() {
        List<String> lore = new ArrayList<>();
        for (GensData.Tier tier : gens.tiers().values()) {
            String upgrade = tier.upgrade > 0 ? core.economy().format(gens.currency(), tier.upgrade) : "-";
            lore.add(Text.replace(core.messages().raw("gens.menu.tiers.line"), "%level%", String.valueOf(tier.level), "%tier%", tier.name,
                    "%value%", core.economy().format(gens.currency(), tier.value), "%interval%", String.valueOf(tier.interval), "%upgrade%", upgrade));
        }
        lore.add("");
        lore.addAll(core.messages().list("gens.menu.tiers.hint"));
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
