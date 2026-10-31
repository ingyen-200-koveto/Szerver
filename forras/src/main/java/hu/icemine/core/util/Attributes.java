package hu.icemine.core.util;

import org.bukkit.Keyed;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

public final class Attributes {
    private static Object attackSpeed;

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

    public static void oldCombat(Player player, boolean flag) {
        AttributeInstance attributeinstance = attackSpeed(player);
        if (attributeinstance != null) {
            attributeinstance.setBaseValue(flag ? 1024.0 : attributeinstance.getDefaultValue());
        }
    }
}
