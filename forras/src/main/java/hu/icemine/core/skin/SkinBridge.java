package hu.icemine.core.skin;

import hu.icemine.core.IceMineCore;
import java.util.Optional;
import java.util.UUID;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.InputDataResult;
import net.skinsrestorer.api.property.SkinProperty;
import net.skinsrestorer.api.property.SkinVariant;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Csak ez az osztály használja a SkinsRestorert, így nélküle a plugin többi része gond nélkül betölt. */
final class SkinBridge {

    private SkinBridge() {
    }

    static void apply(IceMineCore core, Player player, String url, boolean slim, Runnable ok, Runnable fail) {
        UUID id = player.getUniqueId();
        String name = player.getName();
        Bukkit.getScheduler().runTaskAsynchronously(core, () -> {
            try {
                SkinsRestorer sr = SkinsRestorerProvider.get();
                if (url == null) {
                    sr.getPlayerStorage().removeSkinIdOfPlayer(id);
                    Bukkit.getScheduler().runTask(core, () -> {
                        Player online = Bukkit.getPlayer(id);
                        if (online != null) {
                            try {
                                sr.getSkinApplier(Player.class).applySkin(online);
                            } catch (Exception exception) {
                                core.getLogger().warning("Skin: nem sikerült visszaállítani " + name + " skinjét (" + exception.getMessage() + ").");
                            }
                        }
                        ok.run();
                    });
                    return;
                }
                Optional<InputDataResult> result = sr.getSkinStorage().findOrCreateSkinData(url, slim ? SkinVariant.SLIM : SkinVariant.CLASSIC);
                if (result.isEmpty()) {
                    core.getLogger().warning("Skin: a SkinsRestorer nem tudta betölteni " + name + " weboldalas skinjét (" + url + ").");
                    fail.run();
                    return;
                }
                sr.getPlayerStorage().setSkinIdOfPlayer(id, result.get().getIdentifier());
                SkinProperty property = result.get().getProperty();
                Bukkit.getScheduler().runTask(core, () -> {
                    Player online = Bukkit.getPlayer(id);
                    if (online != null) {
                        sr.getSkinApplier(Player.class).applySkin(online, property);
                    }
                    ok.run();
                });
            } catch (Exception exception) {
                core.getLogger().warning("Skin: nem sikerült beállítani " + name + " weboldalas skinjét (" + exception.getClass().getSimpleName() + ": " + exception.getMessage() + ").");
                fail.run();
            }
        });
    }
}
