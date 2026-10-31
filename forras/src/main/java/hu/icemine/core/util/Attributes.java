package hu.icemine.core.util;

import org.bukkit.Keyed;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

public final class Attributes {
    private static Object attackSpeed;
    private static Object maxHealth;

    private Attributes() {
    }

    public static AttributeInstance attackSpeed(Player player) {
        if (attackSpeed == null) {
            for (Object object : Registry.ATTRIBUTE) {
                if (object instanceof Keyed && ((Keyed)object).getKey().getKey().endsWith("attack_speed")) {
                    attackSpeed = object;
                    break;
                }
            }
        }

        return attackSpeed == null ? null : player.getAttribute((Attribute)attackSpeed);
    }

    /** A max élet attribútum (registryből keresve, mert az enum neve verziónként más). */
    public static AttributeInstance maxHealth(Player player) {
        if (maxHealth == null) {
            for (Object object : Registry.ATTRIBUTE) {
                if (object instanceof Keyed && ((Keyed) object).getKey().getKey().endsWith("max_health")) {
                    maxHealth = object;
                    break;
                }
            }
        }
        return maxHealth == null ? null : player.getAttribute((Attribute) maxHealth);
    }

    /** Max élet beállítása (20 = 10 szív). */
    public static void setMaxHealth(Player player, double value) {
        AttributeInstance attribute = maxHealth(player);
        if (attribute == null) {
            return;
        }
        attribute.setBaseValue(Math.max(2.0, Math.min(100.0, value)));
        if (player.getHealth() > attribute.getValue()) {
            player.setHealth(attribute.getValue());
        }
    }

    public static void oldCombat(Player player, boolean flag) {
        AttributeInstance attributeinstance = attackSpeed(player);
        if (attributeinstance != null) {
            attributeinstance.setBaseValue(flag ? 1024.0 : attributeinstance.getDefaultValue());
        }
    }
}
