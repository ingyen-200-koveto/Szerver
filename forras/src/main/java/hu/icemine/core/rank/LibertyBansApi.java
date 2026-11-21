package hu.icemine.core.rank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import space.arim.libertybans.api.LibertyBans;
import space.arim.libertybans.api.PlayerVictim;
import space.arim.libertybans.api.PunishmentType;
import space.arim.libertybans.api.punish.Punishment;
import space.arim.libertybans.api.select.SelectionPredicate;
import space.arim.omnibus.OmnibusProvider;

/** A LibertyBans API hívásai külön osztályban, hogy LibertyBans nélkül se legyen betöltési hiba. */
final class LibertyBansApi {

    private LibertyBansApi() {
    }

    static String collect() throws Exception {
        Optional<LibertyBans> api = OmnibusProvider.getOmnibus().getRegistry().getProvider(LibertyBans.class);
        if (api.isEmpty()) {
            return null;
        }
        LibertyBans lb = api.get();
        List<Punishment> list = lb.getSelector().selectionBuilder()
                .types(SelectionPredicate.matchingAnyOf(PunishmentType.BAN, PunishmentType.MUTE))
                .selectActiveOnly()
                .build()
                .getAllSpecificPunishments()
                .toCompletableFuture()
                .get(15, TimeUnit.SECONDS);
        List<String> items = new ArrayList<>();
        for (Punishment p : list) {
            if (!(p.getVictim() instanceof PlayerVictim)) {
                continue;
            }
            UUID id = ((PlayerVictim) p.getVictim()).getUUID();
            String name = nameOf(lb, id);
            if (name == null) {
                continue;
            }
            Instant end = p.getEndDate();
            long until = p.isPermanent() || end == null ? 0L : end.toEpochMilli();
            items.add("{\"nev\":" + Buntetesek.q(name) + ",\"tipus\":\"" + (p.getType() == PunishmentType.BAN ? "ban" : "mute") + "\",\"lejar\":" + until
                    + ",\"indok\":" + Buntetesek.q(p.getReason() == null ? "" : p.getReason()) + "}");
        }
        java.util.Collections.sort(items);
        return "{\"lista\":[" + String.join(",", items) + "]}";
    }

    private static String nameOf(LibertyBans lb, UUID id) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(id);
        if (player.getName() != null) {
            return player.getName();
        }
        try {
            return lb.getUserResolver().lookupName(id).get(5, TimeUnit.SECONDS).orElse(null);
        } catch (Exception error) {
            return null;
        }
    }
}
