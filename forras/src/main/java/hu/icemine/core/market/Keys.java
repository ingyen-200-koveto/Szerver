package hu.icemine.core.market;

import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.kitpvp.Kit;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Kulcsok (lootbox): jobb klikk a kulccsal, és kapsz valamit a kulcs jutalmai közül (tárgy, pénz vagy másik kulcs).
 * Bal klikk megmutatja, mi lehet benne. A rangok hetente kulcs csomagot kapnak (/kulcs), piaconként külön.
 */
public final class Keys implements Listener {

    static final String TAG = "market:key:";

    record Reward(ItemStack item, long money, String key, int keyAmount, double weight, String label) {
    }

    static final class Key {
        final String id;
        final String name;
        final Material material;
        final List<String> lore;
        final List<Reward> rewards = new ArrayList<>();
        final double announceBelow;
        double total;

        Key(String id, String name, Material material, List<String> lore, double announceBelow) {
            this.id = id;
            this.name = name;
            this.material = material;
            this.lore = lore;
            this.announceBelow = announceBelow;
        }
    }

    private final Market market;
    private final Random random = new Random();
    private final Map<String, Key> keys = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> rankPackages = new LinkedHashMap<>();
    private long rankCooldown = 7 * 86_400_000L;

    Keys(Market market) {
        this.market = market;
    }

    void load(YamlConfiguration config, List<String> problems) {
        keys.clear();
        ConfigurationSection section = config.getConfigurationSection("keys");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null) {
                    continue;
                }
                Key key = new Key(id.toLowerCase(Locale.ROOT), entry.getString("name", id), Items.material(entry.getString("material", "TRIPWIRE_HOOK"), Material.TRIPWIRE_HOOK),
                        entry.getStringList("lore"), entry.getDouble("announce-below", 0));
                for (Map<?, ?> raw : entry.getMapList("rewards")) {
                    double weight = raw.get("weight") instanceof Number number ? number.doubleValue() : 1;
                    String label = raw.get("label") == null ? null : String.valueOf(raw.get("label"));
                    if (raw.get("item") != null) {
                        ItemStack item = Kit.parse(null, String.valueOf(raw.get("item")), "market.yml -> keys." + id, problems);
                        if (item != null) {
                            key.rewards.add(new Reward(item, 0, null, 0, weight, label));
                            key.total += weight;
                        }
                    } else if (raw.get("money") instanceof Number money) {
                        key.rewards.add(new Reward(null, money.longValue(), null, 0, weight, label));
                        key.total += weight;
                    } else if (raw.get("key") != null) {
                        int amount = raw.get("amount") instanceof Number number ? number.intValue() : 1;
                        key.rewards.add(new Reward(null, 0, String.valueOf(raw.get("key")).toLowerCase(Locale.ROOT), Math.max(1, amount), weight, label));
                        key.total += weight;
                    } else {
                        problems.add("market.yml -> keys." + id + ": minden jutalomnak kell item, money vagy key");
                    }
                }
                if (key.rewards.isEmpty()) {
                    problems.add("market.yml -> keys." + id + ": nincs egy jutalom sem");
                }
                keys.put(key.id, key);
            }
        }
        rankCooldown = Math.max(1, config.getLong("rank-keys.cooldown-days", 7)) * 86_400_000L;
        rankPackages.clear();
        ConfigurationSection ranks = config.getConfigurationSection("rank-keys.ranks");
        if (ranks != null) {
            for (String rank : ranks.getKeys(false)) {
                Map<String, Integer> pack = new LinkedHashMap<>();
                ConfigurationSection entry = ranks.getConfigurationSection(rank);
                if (entry != null) {
                    for (String key : entry.getKeys(false)) {
                        if (!keys.containsKey(key.toLowerCase(Locale.ROOT))) {
                            problems.add("market.yml -> rank-keys.ranks." + rank + ": ismeretlen kulcs (" + key + ")");
                            continue;
                        }
                        pack.put(key.toLowerCase(Locale.ROOT), Math.max(1, entry.getInt(key)));
                    }
                }
                rankPackages.put(rank.toLowerCase(Locale.ROOT), pack);
            }
        }
    }

    String name(String id) {
        Key key = keys.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
        return key == null ? String.valueOf(id) : key.name;
    }

    ItemStack item(String id, int amount) {
        Key key = keys.get(id.toLowerCase(Locale.ROOT));
        if (key == null) {
            return null;
        }
        List<String> lore = new ArrayList<>(key.lore);
        lore.addAll(market.texts("key-lore"));
        ItemStack item = Items.of(key.material, Math.max(1, Math.min(64, amount))).name(key.name).lore(lore).glow().tag(TAG + key.id).build();
        return item;
    }

    public boolean give(Player player, String id, int amount) {
        ItemStack item = item(id, amount);
        if (item == null) {
            return false;
        }
        Market.give(player, item);
        return true;
    }

    public java.util.Set<String> ids() {
        return keys.keySet();
    }

    private Reward roll(Key key) {
        double value = random.nextDouble() * key.total;
        for (Reward reward : key.rewards) {
            value -= reward.weight();
            if (value <= 0) {
                return reward;
            }
        }
        return key.rewards.get(key.rewards.size() - 1);
    }

    private String label(Market.Scope scope, Reward reward) {
        if (reward.label() != null) {
            return reward.label();
        }
        if (reward.item() != null) {
            return reward.item().getAmount() + "x " + market.itemName(reward.item());
        }
        if (reward.key() != null) {
            return reward.keyAmount() + "x " + name(reward.key());
        }
        return market.money(scope, reward.money());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        String tag = Items.tagOf(event.getItem());
        if (tag == null || !tag.startsWith(TAG)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) {
            return;
        }
        Player player = event.getPlayer();
        Key key = keys.get(tag.substring(TAG.length()));
        if (key == null || key.rewards.isEmpty()) {
            return;
        }
        Market.Scope scope = market.require(player);
        if (scope == null) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            preview(player, key, scope);
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            market.send(player, "inventory-full");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        Reward reward = roll(key);
        if (reward.item() != null) {
            Market.give(player, reward.item().clone());
        } else if (reward.key() != null) {
            give(player, reward.key(), reward.keyAmount());
        } else if (reward.money() > 0) {
            market.core().economy().deposit(player, scope.currency(), reward.money(), "market:key");
        }
        String label = label(scope, reward);
        market.send(player, "key-opened", "%key%", key.name, "%reward%", label);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.2, 0), 25, 0.4, 0.5, 0.4, 0.15);
        if (key.announceBelow > 0 && reward.weight() <= key.announceBelow) {
            market.broadcast(scope, "key-rare", "%player%", player.getName(), "%key%", key.name, "%reward%", label);
        }
    }

    private void preview(Player player, Key key, Market.Scope scope) {
        int rows = Math.max(2, Math.min(6, (key.rewards.size() + 8) / 9 + 1));
        Menu menu = new Menu("market-key-" + key.id, rows, market.text("key-preview-title", "%key%", Text.strip(Text.color(key.name))));
        int slot = 0;
        for (Reward reward : key.rewards) {
            if (slot >= (rows - 1) * 9) {
                break;
            }
            ItemStack icon;
            if (reward.item() != null) {
                icon = reward.item().clone();
            } else if (reward.key() != null) {
                ItemStack keyItem = item(reward.key(), reward.keyAmount());
                icon = keyItem == null ? new ItemStack(Material.TRIPWIRE_HOOK) : keyItem;
            } else {
                icon = Items.of(Material.GOLD_INGOT).name("&e" + market.money(scope, reward.money())).build();
            }
            org.bukkit.inventory.meta.ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(Text.color(market.text("key-chance", "%chance%", Text.decimal(reward.weight() / key.total * 100))));
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            menu.set(slot++, icon);
        }
        menu.open(player);
    }

    /** /kulcs: a rang heti kulcs csomagja (piaconként egyszer). */
    public void claimRank(Player player) {
        Market.Scope scope = market.require(player);
        if (scope == null) {
            return;
        }
        hu.icemine.core.rank.Ranks.Rank rank = market.core().ranks().of(player);
        Map<String, Integer> pack = rank == null ? null : rankPackages.get(rank.id);
        if (pack == null || pack.isEmpty()) {
            market.send(player, "kulcs-no-rank");
            return;
        }
        PlayerProfile profile = market.core().profiles().profile(player);
        if (profile == null) {
            return;
        }
        long last;
        try {
            last = Long.parseLong(profile.setting("market.kulcs." + scope.id(), "0"));
        } catch (NumberFormatException exception) {
            last = 0;
        }
        long left = last + rankCooldown - System.currentTimeMillis();
        if (left > 0) {
            market.send(player, "kulcs-cooldown", "%time%", Text.duration(left));
            return;
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : pack.entrySet()) {
            give(player, entry.getKey(), entry.getValue());
            parts.add(entry.getValue() + "x " + name(entry.getKey()));
        }
        profile.put("market.kulcs." + scope.id(), String.valueOf(System.currentTimeMillis()));
        market.send(player, "kulcs-claimed", "%keys%", String.join("&7, ", parts), "%rank%", rank.name);
    }
}
