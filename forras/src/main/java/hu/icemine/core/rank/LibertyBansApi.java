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

    /** Büntetés és feloldás események: a json-t a küldőnek adja (aszinkron szálon). */
    static void figyel(java.util.function.Consumer<String> kuld, java.util.function.Consumer<Throwable> hiba) {
        Optional<LibertyBans> api = OmnibusProvider.getOmnibus().getRegistry().getProvider(LibertyBans.class);
        if (api.isEmpty()) {
            return;
        }
        LibertyBans lb = api.get();
        space.arim.omnibus.events.EventConsumer<space.arim.libertybans.api.event.PostPunishEvent> uj = event -> {
            try {
                kuld.accept(esemeny(lb, event.getPunishment(), "uj", event.getPunishment().getOperator(), event.getTarget().orElse(null)));
            } catch (Throwable error) {
                hiba.accept(error);
            }
        };
        space.arim.omnibus.events.EventConsumer<space.arim.libertybans.api.event.PostPardonEvent> fel = event -> {
            try {
                kuld.accept(esemeny(lb, event.getPunishment(), "feloldas", event.getOperator(), event.getTarget().orElse(null)));
            } catch (Throwable error) {
                hiba.accept(error);
            }
        };
        OmnibusProvider.getOmnibus().getEventBus().registerListener(space.arim.libertybans.api.event.PostPunishEvent.class,
                space.arim.omnibus.events.ListenerPriorities.NORMAL, uj);
        OmnibusProvider.getOmnibus().getEventBus().registerListener(space.arim.libertybans.api.event.PostPardonEvent.class,
                space.arim.omnibus.events.ListenerPriorities.NORMAL, fel);
    }

    private static String esemeny(LibertyBans lb, Punishment p, String mufaj, space.arim.libertybans.api.Operator op, String target) {
        String jatekos;
        if (p.getVictim() instanceof PlayerVictim pv) {
            jatekos = nameOf(lb, pv.getUUID());
        } else if (p.getVictim() instanceof space.arim.libertybans.api.CompositeVictim cv) {
            jatekos = nameOf(lb, cv.getUUID());
        } else {
            jatekos = null;
        }
        if (jatekos == null) {
            jatekos = target != null && target.matches("[A-Za-z0-9_.]{1,17}") ? target : "IP-cím";
        }
        String staff = op instanceof space.arim.libertybans.api.PlayerOperator po ? nameOf(lb, po.getUUID()) : "Konzol";
        Instant end = p.getEndDate();
        long until = p.getType() == PunishmentType.KICK || p.isPermanent() || end == null ? 0L : end.toEpochMilli();
        return "{\"mufaj\":\"" + mufaj + "\",\"tipus\":\"" + p.getType().name().toLowerCase(java.util.Locale.ROOT) + "\",\"jatekos\":"
                + Buntetesek.q(jatekos) + ",\"staff\":" + Buntetesek.q(staff == null ? "Ismeretlen" : staff) + ",\"indok\":"
                + Buntetesek.q(p.getReason() == null ? "" : p.getReason()) + ",\"lejar\":" + until + ",\"id\":" + p.getIdentifier() + "}";
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
