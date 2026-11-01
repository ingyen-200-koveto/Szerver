package hu.icemine.core.fyland;

import hu.icemine.core.util.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/**
 * Egy fázis (pl. Síkság, Föld alatt, Nether): hány blokkig tart, milyen blokkok jönnek (súllyal), milyen eséllyel jön
 * láda és mi van benne, milyen eséllyel és milyen mob jelenik meg, és mi van a fázis végén járó jutalom ládában.
 */
final class FyPhase {

    record Weighted<T>(T value, double weight) {
    }

    /** Láda tartalom: anyag, darab (min-max), esély. */
    record Loot(Material material, int min, int max, double chance) {

        ItemStack roll(Random random) {
            if (random.nextDouble() > chance) {
                return null;
            }
            int amount = min >= max ? min : min + random.nextInt(max - min + 1);
            return amount <= 0 ? null : new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
        }
    }

    final String id;
    final String name;
    final Material icon;
    final int blocks;
    final List<Weighted<Material>> pool = new ArrayList<>();
    final List<Weighted<EntityType>> mobs = new ArrayList<>();
    final List<Loot> chest = new ArrayList<>();
    final List<Loot> reward = new ArrayList<>();
    final List<String> description = new ArrayList<>();
    double chestChance;
    double mobChance;
    private double poolTotal;
    private double mobTotal;

    FyPhase(String id, String name, Material icon, int blocks) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.blocks = blocks;
    }

    Material pick(Random random) {
        if (pool.isEmpty()) {
            return Material.GRASS_BLOCK;
        }
        double roll = random.nextDouble() * poolTotal;
        for (Weighted<Material> entry : pool) {
            roll -= entry.weight();
            if (roll <= 0) {
                return entry.value();
            }
        }
        return pool.get(pool.size() - 1).value();
    }

    EntityType mob(Random random) {
        if (mobs.isEmpty()) {
            return null;
        }
        double roll = random.nextDouble() * mobTotal;
        for (Weighted<EntityType> entry : mobs) {
            roll -= entry.weight();
            if (roll <= 0) {
                return entry.value();
            }
        }
        return mobs.get(mobs.size() - 1).value();
    }

    static FyPhase read(String id, ConfigurationSection section, List<String> problems) {
        String where = "fyland.yml -> phases." + id;
        FyPhase phase = new FyPhase(id, section.getString("name", id), Items.material(section.getString("icon", "GRASS_BLOCK"), Material.GRASS_BLOCK),
                Math.max(10, section.getInt("blocks", 300)));
        phase.description.addAll(section.getStringList("description"));
        ConfigurationSection pool = section.getConfigurationSection("blocks-pool");
        if (pool != null) {
            for (String key : pool.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                double weight = pool.getDouble(key);
                if (material == null || !material.isBlock() || material.isAir()) {
                    problems.add(where + ".blocks-pool: ismeretlen blokk (" + key + ")");
                    continue;
                }
                if (material == Material.WATER || material == Material.LAVA) {
                    problems.add(where + ".blocks-pool: folyadék nem lehet varázsblokk (" + key + ")");
                    continue;
                }
                if (weight > 0) {
                    phase.pool.add(new Weighted<>(material, weight));
                    phase.poolTotal += weight;
                }
            }
        }
        if (phase.pool.isEmpty()) {
            problems.add(where + ": nincs egy blokk sem a blocks-pool részben");
        }
        ConfigurationSection mobs = section.getConfigurationSection("mobs");
        if (mobs != null) {
            for (String key : mobs.getKeys(false)) {
                EntityType type = entity(key);
                double weight = mobs.getDouble(key);
                if (type == null || !type.isAlive() || !type.isSpawnable()) {
                    problems.add(where + ".mobs: ismeretlen mob (" + key + ")");
                    continue;
                }
                if (weight > 0) {
                    phase.mobs.add(new Weighted<>(type, weight));
                    phase.mobTotal += weight;
                }
            }
        }
        phase.chestChance = clamp(section.getDouble("chest-chance", 0.03), 0, 0.5);
        phase.mobChance = clamp(section.getDouble("mob-chance", 0.04), 0, 0.5);
        phase.chest.addAll(loot(where + ".chest", section.getStringList("chest"), problems));
        phase.reward.addAll(loot(where + ".reward", section.getStringList("reward"), problems));
        return phase;
    }

    static EntityType entity(String name) {
        try {
            return EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** "ANYAG min-max esély" vagy "ANYAG darab esély" vagy "ANYAG darab" (esély nélkül mindig benne van). */
    static List<Loot> loot(String where, List<String> specs, List<String> problems) {
        List<Loot> result = new ArrayList<>();
        for (String spec : specs) {
            String[] parts = spec.trim().split("\\s+");
            Material material = Material.matchMaterial(parts[0]);
            if (material == null || material.isAir() || !material.isItem()) {
                problems.add(where + ": ismeretlen tárgy (" + parts[0] + ")");
                continue;
            }
            int min = 1;
            int max = 1;
            double chance = 1.0;
            try {
                if (parts.length > 1) {
                    String[] range = parts[1].split("-");
                    min = Integer.parseInt(range[0]);
                    max = range.length > 1 ? Integer.parseInt(range[1]) : min;
                }
                if (parts.length > 2) {
                    chance = Double.parseDouble(parts[2]);
                }
            } catch (NumberFormatException exception) {
                problems.add(where + ": hibás sor (" + spec + "), formátum: ANYAG min-max esély");
                continue;
            }
            result.add(new Loot(material, Math.max(1, min), Math.max(Math.max(1, min), max), clamp(chance, 0, 1)));
        }
        return result;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
