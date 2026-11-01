package hu.icemine.kingdoms;

import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** A Kingdoms összes menüje. Minden gomb működik, a jogokat a KingdomManager ellenőrzi. */
public final class KingdomMenus {

    private static final Material EDGE = Material.ORANGE_STAINED_GLASS_PANE;
    private final IceMineKingdoms plugin;
    private final SimpleDateFormat date = new SimpleDateFormat("yyyy.MM.dd. HH:mm");

    KingdomMenus(IceMineKingdoms plugin) {
        this.plugin = plugin;
    }

    private KingdomManager km() {
        return plugin.manager();
    }

    private KConfig cfg() {
        return plugin.settings();
    }

    private Menu menu(String id, int rows, String title) {
        Menu menu = new Menu("kingdoms-" + id, rows, plugin.label("menus.title-prefix", "&3[IceMine] &8") + title);
        menu.frame(EDGE, null);
        return menu;
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        return Items.of(material).name(name).lore(lore).hideAll().build();
    }

    private static ItemStack button(Material material, String name, String... lore) {
        return button(material, name, List.of(lore));
    }

    private static Material material(String name, Material fallback) {
        return Items.material(name, fallback);
    }

    private void back(Menu menu, int slot, Consumer<Player> action) {
        menu.set(slot, button(Material.ARROW, "&7« Vissza"), action);
    }

    private String rp(long value) {
        return "&e" + hu.icemine.core.util.Text.number(value) + " RP";
    }

    private String yesNo(boolean value) {
        return value ? "&a✔ igen" : "&c✖ nem";
    }

    /** Lapozós lista: a középső 28 hely (4 sor), 45/53-on lapozó gombok. */
    private <T> void paged(Menu menu, List<T> items, int page, java.util.function.Function<T, ItemStack> icon, java.util.function.Function<T, Consumer<Player>> click,
                           java.util.function.IntConsumer reopen) {
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        int per = slots.length;
        int pages = Math.max(1, (items.size() + per - 1) / per);
        int current = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < per; i++) {
            int index = current * per + i;
            if (index >= items.size()) {
                break;
            }
            T item = items.get(index);
            menu.set(slots[i], icon.apply(item), click.apply(item));
        }
        if (current > 0) {
            menu.set(45, button(Material.SPECTRAL_ARROW, "&e« Előző oldal"), player -> reopen.accept(current - 1));
        }
        if (current < pages - 1) {
            menu.set(53, button(Material.SPECTRAL_ARROW, "&eKövetkező oldal »"), player -> reopen.accept(current + 1));
        }
    }

    // ---- főmenü ----

    public void main(Player player) {
        Kingdom kingdom = km().kingdomOf(player.getUniqueId());
        if (kingdom == null) {
            start(player, 0);
            return;
        }
        Menu menu = menu("main", 6, kingdom.display());
        int level = km().level(kingdom);
        menu.set(4, button(Material.BEACON, kingdom.display(), infoLore(kingdom, player)), p -> info(p, kingdom));
        menu.set(10, button(Material.PLAYER_HEAD, "&a&lTagok", "&7" + kingdom.members.size() + "/" + km().maxMembers(kingdom) + " tag, " + km().online(kingdom).size() + " fent",
                "", "&b▶ Rangok állítása, kirúgás"), p -> members(p, 0));
        menu.set(11, button(Material.NAME_TAG, "&6&lRangok", "&7" + kingdom.ranks.size() + " rang", "", "&b▶ Jogok, nevek, sorrend"), this::ranks);
        menu.set(12, button(Material.GRASS_BLOCK, "&2&lFöldek", "&7" + kingdom.claims.size() + "/" + km().maxClaims(kingdom) + " chunk",
                "&7Power: &f" + format(km().power(kingdom)), "", "&b▶ Foglalás, elengedés, térkép"), this::lands);
        menu.set(13, button(Material.GOLD_INGOT, "&e&lBank", "&7Egyenleg: &f" + plugin.money(kingdom.bank), "", "&b▶ Befizetés, kivét, napló"), this::bank);
        menu.set(14, button(Material.EMERALD, "&a&lResource Point", "&7Van: " + rp(kingdom.resourcePoints), "", "&b▶ Tárgyak beváltása"), this::points);
        menu.set(15, button(Material.ANVIL, "&d&lFejlesztések", "&7Szint: &f" + level + "&7/&f" + cfg().maxLevel(), "", "&b▶ Erősítések, képességek"), this::upgrades);
        menu.set(16, button(Material.LODESTONE, "&b&lNexus és építmények", "&7Nexus: " + (kingdom.hasNexus() ? "&alerakva" : "&cmég nincs"),
                "&7Építmények: &f" + kingdom.count("structure", null), "", "&b▶ Vásárlás, fejlesztés"), this::structures);
        menu.set(19, button(Material.SKELETON_SKULL, "&c&lTornyok", "&7Lerakva: &f" + kingdom.count("turret", null), "", "&b▶ Vásárlás, lőszer, fejlesztés"), this::turrets);
        menu.set(20, button(Material.ZOMBIE_HEAD, "&4&lBajnok", "&7Ő védi a földjeiteket inváziókor.", "", "&b▶ A bajnok fejlesztései"), this::champion);
        menu.set(21, button(Material.WHITE_BANNER, "&f&lKapcsolatok", "&7Szövetségesek: &d" + km().withRelation(kingdom, Relation.ALLY).size(),
                "&7Ellenségek: &c" + km().withRelation(kingdom, Relation.ENEMY).size(), "", "&b▶ Szövetség, háború, béke"), p -> relations(p, 0));
        Nation nation = km().nationOf(kingdom);
        menu.set(22, button(Material.SHIELD, "&9&lNemzet", "&7" + (nation == null ? "Nincs nemzetetek." : "Nemzet: &f" + nation.name), "", "&b▶ Nemzet ügyei"), this::nation);
        menu.set(23, button(Material.IRON_SWORD, "&c&lHáború", "&7Inváziók, pajzs, masswar.", plugin.invasions().masswar() ? "&4&lMASSWAR VAN!" : "", "&b▶ Megnyitás"), this::war);
        menu.set(24, button(Material.GOLDEN_HELMET, "&6&lToplista", "&7A legerősebb királyságok.", "", "&b▶ Megnyitás"), p -> top(p, "might"));
        menu.set(25, button(Material.COMPARATOR, "&7&lBeállítások", "&7Nyitott belépés, szín, leírás,", "&7pacifizmus, átnevezés.", "", "&b▶ Megnyitás"), this::settings);
        menu.set(28, button(Material.RED_BED, "&c&lHaza", "&7Teleport a királyság otthonába."), p -> {
            p.closeInventory();
            km().home(p, null);
        });
        menu.set(29, button(Material.COMPASS, "&e&lOtthon ide", "&7Az otthon a mostani helyedre kerül."), p -> {
            km().setHome(p);
            main(p);
        });
        menu.set(30, button(Material.GRASS_BLOCK, "&a&lFoglalás itt", "&7Az a chunk, ahol most állsz."), p -> {
            km().claim(p, false);
            main(p);
        });
        menu.set(31, button(Material.FILLED_MAP, "&f&lTérkép", "&7A környék a chatben."), p -> {
            p.closeInventory();
            plugin.showMap(p);
        });
        menu.set(32, button(Material.DEAD_BUSH, "&6&lElengedés itt", "&7Az a chunk, ahol most állsz."), p -> {
            km().unclaim(p);
            main(p);
        });
        KPlayer data = km().player(player);
        menu.set(33, button(Material.WRITABLE_BOOK, "&b&lKirálysági chat", "&7Most: " + (data.kingdomChat ? "&abekapcsolva" : "&ckikapcsolva"),
                "&7Vagy kezdd !-lel az üzenetet."), p -> {
            data.kingdomChat = !data.kingdomChat;
            plugin.send(p, data.kingdomChat ? "chat-on" : "chat-off");
            main(p);
        });
        menu.set(34, button(Material.BOOK, "&f&lNapló", "&7Mi történt a királyságban."), p -> log(p, kingdom, 0));
        menu.set(49, button(Material.BARRIER, "&c&lBezárás"), Player::closeInventory);
        menu.open(player);
    }

    private List<String> infoLore(Kingdom kingdom, Player viewer) {
        List<String> lore = new ArrayList<>();
        int level = km().level(kingdom);
        long next = cfg().nextLevelXp(kingdom.xp);
        lore.add("&7Király: &f" + km().nameOf(kingdom.leader));
        if (!kingdom.description.isEmpty()) {
            lore.add("&8\"" + kingdom.description + "\"");
        }
        lore.add("&7Szint: &f" + level + (next < 0 ? " &8(max)" : " &8(" + hu.icemine.core.util.Text.number(kingdom.xp) + "/" + hu.icemine.core.util.Text.number(next) + " XP)"));
        lore.add("&7Tagok: &f" + kingdom.members.size() + "/" + km().maxMembers(kingdom) + " &8(" + km().online(kingdom).size() + " fent)");
        lore.add("&7Földek: &f" + kingdom.claims.size() + "/" + km().maxClaims(kingdom));
        lore.add("&7Power: &f" + format(km().power(kingdom)));
        lore.add("&7Erő: &f" + hu.icemine.core.util.Text.number(km().might(kingdom)));
        Kingdom mine = km().kingdomOf(viewer.getUniqueId());
        if (mine == kingdom) {
            lore.add("&7Resource Point: " + rp(kingdom.resourcePoints));
            lore.add("&7Bank: &f" + plugin.money(kingdom.bank));
        } else {
            lore.add("&7Kapcsolat veletek: " + km().relation(mine, kingdom).display());
        }
        Nation nation = km().nationOf(kingdom);
        lore.add("&7Nemzet: &f" + (nation == null ? "-" : nation.name));
        if (kingdom.pacifist) {
            lore.add("&a☮ Pacifista");
        }
        if (kingdom.shieldUntil > System.currentTimeMillis()) {
            lore.add("&b⛨ Pajzs: &f" + plugin.duration(kingdom.shieldUntil - System.currentTimeMillis()));
        }
        lore.add("&7Inváziók: &a" + kingdom.invasionsWon + " &7győzelem, &c" + kingdom.invasionsLost + " &7vereség");
        lore.add("&7Ölések: &f" + kingdom.kills + " &8| &7Halálok: &f" + kingdom.deaths);
        lore.add("&7Alapítva: &f" + date.format(new Date(kingdom.created)));
        return lore;
    }

    private static String format(double value) {
        return Math.abs(value - Math.rint(value)) < 0.01 ? String.valueOf((long) Math.rint(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    public void info(Player player, Kingdom kingdom) {
        Menu menu = menu("info", 4, kingdom.name);
        menu.set(13, button(Material.BEACON, kingdom.display(), infoLore(kingdom, player)));
        List<String> members = new ArrayList<>();
        for (Rank rank : kingdom.sortedRanks()) {
            for (Map.Entry<UUID, String> entry : kingdom.members.entrySet()) {
                if (entry.getValue().equals(rank.id) && members.size() < 20) {
                    boolean online = Bukkit.getPlayer(entry.getKey()) != null;
                    members.add(rank.color + rank.symbol + " " + (online ? "&a" : "&7") + km().nameOf(entry.getKey()));
                }
            }
        }
        if (kingdom.members.size() > 20) {
            members.add("&8... és még " + (kingdom.members.size() - 20));
        }
        menu.set(11, button(Material.PLAYER_HEAD, "&a&lTagok", members));
        Kingdom mine = km().kingdomOf(player.getUniqueId());
        if (mine != null && mine != kingdom) {
            menu.set(15, button(Material.WHITE_BANNER, "&f&lKapcsolat", "&7Most: " + km().relation(mine, kingdom).display(), "", "&b▶ Módosítás"),
                    p -> relationWith(p, kingdom));
        } else if (mine == null && (kingdom.open || km().invited(player.getUniqueId(), kingdom))) {
            menu.set(15, button(Material.LIME_DYE, "&a&lCsatlakozás", "&7Belépés ebbe a királyságba."), p -> {
                p.closeInventory();
                km().join(p, kingdom.name);
            });
        }
        back(menu, 31, mine == kingdom ? this::main : p -> top(p, "might"));
        menu.open(player);
    }

    // ---- királyság nélkül ----

    private void start(Player player, int page) {
        Menu menu = menu("start", 6, "Királyságok");
        menu.set(4, button(Material.NETHER_STAR, "&6&lÚj királyság", "&7Alapíts sajátot, és foglalj földet!", "", "&b▶ Kattints, és írd be a nevét"),
                p -> plugin.borders().ask(p, "prompt-kingdom-name", text -> {
                    if (km().create(p, text) != null) {
                        main(p);
                    }
                }));
        List<Kingdom> list = new ArrayList<>();
        list.addAll(km().invitesOf(player.getUniqueId()));
        for (Kingdom kingdom : km().top(Comparator.comparingLong(k -> -km().might(k)))) {
            if (kingdom.open && !list.contains(kingdom)) {
                list.add(kingdom);
            }
        }
        paged(menu, list, page, kingdom -> {
            boolean invited = km().invited(player.getUniqueId(), kingdom);
            List<String> lore = new ArrayList<>(infoLore(kingdom, player));
            lore.add("");
            lore.add(invited ? "&a✉ Meghívtak ide!" : "&7Nyitott: bárki beléphet.");
            lore.add("&b▶ Kattints a csatlakozáshoz");
            return button(material(kingdom.ranks.get(Rank.KING) == null ? "EMERALD_BLOCK" : kingdom.ranks.get(Rank.KING).material, Material.EMERALD_BLOCK), kingdom.display(), lore);
        }, kingdom -> p -> {
            p.closeInventory();
            km().join(p, kingdom.name);
        }, next -> start(player, next));
        menu.set(49, button(Material.GOLDEN_HELMET, "&6&lToplista", "&7A legerősebb királyságok."), p -> top(p, "might"));
        menu.open(player);
    }

    // ---- tagok ----

    public void members(Player player, int page) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null) {
            return;
        }
        Menu menu = menu("members", 6, "Tagok");
        List<UUID> list = new ArrayList<>(kingdom.members.keySet());
        list.sort(Comparator.comparingInt((UUID id) -> kingdom.rankOf(id) == null ? 99 : kingdom.rankOf(id).priority).thenComparing(km()::nameOf));
        paged(menu, list, page, id -> {
            KPlayer data = km().player(id);
            Rank rank = kingdom.rankOf(id);
            boolean online = Bukkit.getPlayer(id) != null;
            List<String> lore = new ArrayList<>();
            lore.add("&7Rang: " + (rank == null ? "-" : rank.display()));
            lore.add("&7" + (online ? "&aFent van" : "Utoljára: &f" + (data.lastSeen == 0 ? "-" : date.format(new Date(data.lastSeen)))));
            lore.add("&7Power: &f" + format(data.power));
            lore.add("&7Ölések: &f" + data.kills + " &8| &7Halálok: &f" + data.deaths);
            lore.add("&7Beadott RP: &e" + data.donated);
            lore.add("&7Foglalt földek: &f" + kingdom.claimedBy(id));
            if (data.joined > 0) {
                lore.add("&7Belépett: &f" + date.format(new Date(data.joined)));
            }
            lore.add("");
            lore.add("&b▶ Kattints a kezeléshez");
            return Items.head(Bukkit.getOfflinePlayer(id)).name((online ? "&a" : "&7") + km().nameOf(id)).lore(lore).build();
        }, id -> p -> member(p, id), next -> members(player, next));
        back(menu, 49, this::main);
        menu.open(player);
    }

    private void member(Player player, UUID target) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null || !kingdom.members.containsKey(target)) {
            return;
        }
        String name = km().nameOf(target);
        Menu menu = menu("member", 4, name);
        Rank rank = kingdom.rankOf(target);
        menu.set(4, Items.head(Bukkit.getOfflinePlayer(target)).name("&a" + name).lore("&7Rang: " + (rank == null ? "-" : rank.display())).build());
        menu.set(11, button(Material.LIME_DYE, "&a&lElőléptetés", "&7Egy ranggal feljebb."), p -> {
            km().shift(p, name, true);
            member(p, target);
        });
        menu.set(12, button(Material.ORANGE_DYE, "&6&lLefokozás", "&7Egy ranggal lejjebb."), p -> {
            km().shift(p, name, false);
            member(p, target);
        });
        menu.set(13, button(Material.NAME_TAG, "&e&lRang választása", "&7Pontos rang beállítása."), p -> chooseRank(p, target));
        menu.set(14, button(Material.IRON_DOOR, "&c&lKirúgás", "&7Kikerül a királyságból."), p -> {
            km().kick(p, name);
            members(p, 0);
        });
        if (player.getUniqueId().equals(kingdom.leader) && !target.equals(kingdom.leader)) {
            menu.set(15, button(Material.GOLDEN_HELMET, "&6&lTrón átadása", "&7Ő lesz az új király.", "&cEzt nem lehet visszacsinálni!", "", "&b▶ Kattints kétszer"),
                    p -> confirmThen(p, "transfer:" + target, () -> km().transfer(p, name), () -> member(p, target)));
        }
        back(menu, 31, p -> members(p, 0));
        menu.open(player);
    }

    private final java.util.Map<UUID, String> pendingConfirm = new java.util.HashMap<>();
    private final java.util.Map<UUID, Long> pendingAt = new java.util.HashMap<>();

    /** Két kattintás kell a veszélyes gombokhoz (10 mp-en belül). */
    private void confirmThen(Player player, String what, Runnable action, Runnable reopen) {
        long now = System.currentTimeMillis();
        if (what.equals(pendingConfirm.get(player.getUniqueId())) && now - pendingAt.getOrDefault(player.getUniqueId(), 0L) < 10_000L) {
            pendingConfirm.remove(player.getUniqueId());
            action.run();
            return;
        }
        pendingConfirm.put(player.getUniqueId(), what);
        pendingAt.put(player.getUniqueId(), now);
        plugin.send(player, "click-again");
        reopen.run();
    }

    private void chooseRank(Player player, UUID target) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null) {
            return;
        }
        Menu menu = menu("chooserank", 4, "Rang: " + km().nameOf(target));
        int slot = 10;
        for (Rank rank : kingdom.sortedRanks()) {
            if (rank.king() || slot > 25) {
                continue;
            }
            if (slot % 9 == 8) {
                slot += 2;
            }
            menu.set(slot++, button(material(rank.material, Material.IRON_BLOCK), rank.display(), "&b▶ Ezt a rangot kapja"), p -> {
                km().setRank(p, target, rank.id);
                member(p, target);
            });
        }
        back(menu, 31, p -> member(p, target));
        menu.open(player);
    }

    // ---- rangok ----

    public void ranks(Player player) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null) {
            return;
        }
        Menu menu = menu("ranks", 5, "Rangok");
        int slot = 10;
        for (Rank rank : kingdom.sortedRanks()) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot > 34) {
                break;
            }
            int count = 0;
            for (String id : kingdom.members.values()) {
                if (id.equals(rank.id)) {
                    count++;
                }
            }
            List<String> lore = new ArrayList<>();
            lore.add("&7Tagok: &f" + count);
            lore.add("&7Foglalhat: &f" + (rank.maxClaims < 0 ? "korlátlan" : rank.maxClaims + " chunkot"));
            lore.add("&7Jogok: &f" + (rank.king() ? "mind" : rank.perms.size() + "/" + KPerm.values().length));
            lore.add("");
            lore.add(rank.king() ? "&8A király rangja nem szerkeszthető." : "&b▶ Kattints a szerkesztéshez");
            menu.set(slot++, button(material(rank.material, Material.IRON_BLOCK), rank.display(), lore), rank.king() ? null : p -> rank(p, rank.id));
        }
        menu.set(40, button(Material.EMERALD, "&a&lÚj rang", "&7Írd be a nevét a chatbe."), p -> plugin.borders().ask(p, "prompt-rank-name", text -> {
            Rank created = km().createRank(p, text);
            if (created != null) {
                rank(p, created.id);
            } else {
                ranks(p);
            }
        }));
        back(menu, 36, this::main);
        menu.open(player);
    }

    private void rank(Player player, String rankId) {
        Kingdom kingdom = km().mine(player);
        Rank rank = kingdom == null ? null : kingdom.ranks.get(rankId);
        if (rank == null) {
            return;
        }
        Menu menu = menu("rank", 6, rank.name);
        menu.set(4, button(material(rank.material, Material.IRON_BLOCK), rank.display(), "&7Sorrend: &f" + (kingdom.sortedRanks().indexOf(rank) + 1) + ". hely"));
        menu.set(10, button(Material.NAME_TAG, "&e&lÁtnevezés", "&7Írd be az új nevet a chatbe."), p -> plugin.borders().ask(p, "prompt-rank-rename", text -> {
            km().renameRank(p, rank, text);
            rank(p, rank.id);
        }));
        menu.set(11, button(Material.ORANGE_DYE, "&6&lSzín", "&7Most: " + rank.color + "ez", "&b▶ Kattints a következőhöz"), p -> {
            km().cycleColor(p, rank);
            rank(p, rank.id);
        });
        menu.set(12, button(Material.PAPER, "&f&lJel", "&7Most: " + rank.color + rank.symbol, "&b▶ Kattints a következőhöz"), p -> {
            km().cycleSymbol(p, rank);
            rank(p, rank.id);
        });
        menu.set(13, button(Material.GRASS_BLOCK, "&2&lFoglalási limit", "&7Most: &f" + (rank.maxClaims < 0 ? "korlátlan" : rank.maxClaims),
                "&7Bal klikk: &a+1 &8| &7Jobb klikk: &c-1"), p -> {
            km().changeMaxClaims(p, rank, 1);
            rank(p, rank.id);
        }, p -> {
            km().changeMaxClaims(p, rank, -1);
            rank(p, rank.id);
        });
        menu.set(14, button(Material.LIME_DYE, "&a&lFeljebb", "&7Egy hellyel előrébb a rangsorban."), p -> {
            km().moveRank(p, rank, true);
            rank(p, rank.id);
        });
        menu.set(15, button(Material.GRAY_DYE, "&7&lLejjebb", "&7Egy hellyel hátrébb a rangsorban."), p -> {
            km().moveRank(p, rank, false);
            rank(p, rank.id);
        });
        menu.set(16, button(Material.LAVA_BUCKET, "&c&lTörlés", "&7A tagjai a legalsó rangot kapják.", "", "&b▶ Kattints kétszer"),
                p -> confirmThen(p, "rank-delete:" + rank.id, () -> {
                    km().deleteRank(p, rank);
                    ranks(p);
                }, () -> rank(p, rank.id)));
        int slot = 19;
        for (KPerm perm : KPerm.values()) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot > 43) {
                break;
            }
            boolean on = rank.perms.contains(perm);
            Items item = Items.of(material(perm.icon, Material.PAPER)).name((on ? "&a" : "&7") + perm.title)
                    .lore(on ? "&a✔ Megvan" : "&c✖ Nincs meg", "&b▶ Kattints a váltáshoz").hideAll();
            if (on) {
                item.glow();
            }
            menu.set(slot++, item.build(), p -> {
                km().togglePerm(p, rank, perm);
                rank(p, rank.id);
            });
        }
        back(menu, 49, this::ranks);
        menu.open(player);
    }

    // ---- földek ----

    public void lands(Player player) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null) {
            return;
        }
        Menu menu = menu("lands", 4, "Földek");
        KConfig cfg = cfg();
        boolean free = kingdom.claims.size() < cfg.freeClaims;
        menu.set(4, button(Material.GRASS_BLOCK, "&2&lFöldjeitek", "&7Foglalt: &f" + kingdom.claims.size() + "/" + km().maxClaims(kingdom),
                "&7Darabokban: &f" + Lands.islands(kingdom.claims), "&7Power: &f" + format(km().power(kingdom)) + " &8(" + format(cfg.claimsPerPower) + " chunk / power)",
                "&7A következő ára: " + (free ? "&aingyenes" : rp(cfg.claimCostRp) + (cfg.claimCostMoney > 0 ? " &7+ &f" + plugin.money(cfg.claimCostMoney) : ""))));
        menu.set(10, button(Material.LIME_CONCRETE, "&a&lFoglalás itt", "&7Az a chunk, ahol most állsz."), p -> {
            km().claim(p, false);
            lands(p);
        });
        menu.set(11, button(Material.RED_CONCRETE, "&c&lElengedés itt", "&7Az a chunk, ahol most állsz."), p -> {
            km().unclaim(p);
            lands(p);
        });
        KPlayer data = km().player(player);
        menu.set(12, button(Material.HOPPER, "&e&lAutomata foglalás", "&7Most: " + (data.autoClaim ? "&abekapcsolva" : "&ckikapcsolva"),
                "&7Bekapcsolva minden chunkot lefoglal,", "&7ahova belépsz."), p -> {
            data.autoClaim = !data.autoClaim;
            plugin.send(p, data.autoClaim ? "autoclaim-on" : "autoclaim-off");
            lands(p);
        });
        menu.set(13, button(Material.FILLED_MAP, "&f&lTérkép", "&7A környék a chatben."), p -> {
            p.closeInventory();
            plugin.showMap(p);
        });
        menu.set(14, button(Material.GLOWSTONE_DUST, "&b&lHatár mutatása", "&7Részecskékkel kirajzolja a chunk szélét."), p -> {
            p.closeInventory();
            plugin.borders().show(p, p.getLocation().getBlockX() >> 4, p.getLocation().getBlockZ() >> 4);
        });
        menu.set(15, button(Material.MAP, "&f&lAutomata térkép", "&7Most: " + (data.autoMap ? "&abekapcsolva" : "&ckikapcsolva"),
                "&7Minden új chunkban kiírja a térképet."), p -> {
            data.autoMap = !data.autoMap;
            lands(p);
        });
        menu.set(16, button(Material.TNT, "&4&lMinden föld elengedése", "&7Csak a király teheti meg.", "", "&b▶ Kattints kétszer"), p -> {
            km().unclaimAll(p);
            lands(p);
        });
        back(menu, 31, this::main);
        menu.open(player);
    }

    // ---- bank ----

    public void bank(Player player) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null) {
            return;
        }
        Menu menu = menu("bank", 5, "Bank");
        menu.set(4, button(Material.GOLD_BLOCK, "&e&lKirálysági bank", "&7Egyenleg: &f" + plugin.money(kingdom.bank),
                "&7A te pénzed: &f" + plugin.money(plugin.core().economy().balance(player, plugin.currency()))));
        long[] amounts = {100, 1000, 10000};
        for (int i = 0; i < amounts.length; i++) {
            long amount = amounts[i];
            menu.set(10 + i, button(Material.GOLD_NUGGET, "&a&lBefizetés: " + plugin.money(amount), "&7A saját pénzedből a bankba."), p -> {
                km().deposit(p, amount);
                bank(p);
            });
            menu.set(19 + i, button(Material.IRON_NUGGET, "&c&lKivét: " + plugin.money(amount), "&7A bankból a saját pénzedbe.", "&8(ehhez jog kell)"), p -> {
                km().withdraw(p, amount);
                bank(p);
            });
        }
        menu.set(13, button(Material.WRITABLE_BOOK, "&a&lBefizetés (összeg)", "&7Írd be az összeget a chatbe."), p -> plugin.borders().ask(p, "prompt-amount", text -> {
            km().deposit(p, plugin.amount(p, text));
            bank(p);
        }));
        menu.set(22, button(Material.WRITABLE_BOOK, "&c&lKivét (összeg)", "&7Írd be az összeget a chatbe."), p -> plugin.borders().ask(p, "prompt-amount", text -> {
            km().withdraw(p, plugin.amount(p, text));
            bank(p);
        }));
        long[] points = {10, 100, 1000};
        for (int i = 0; i < points.length; i++) {
            long amount = points[i];
            menu.set(14 + i, button(Material.EMERALD, "&e&l" + amount + " RP vásárlása", "&7Ára: &f" + plugin.money(amount * cfg().moneyPerPoint) + " &7a bankból."), p -> {
                km().convert(p, amount);
                bank(p);
            });
        }
        List<String> log = new ArrayList<>();
        int shown = 0;
        for (String line : kingdom.bankLog) {
            String[] parts = line.split("\\|", 4);
            if (parts.length < 4 || shown >= 12) {
                continue;
            }
            long amount = parse(parts[2]);
            log.add("&8" + date.format(new Date(parse(parts[0]))) + " &f" + parts[1] + " " + (amount >= 0 ? "&a+" : "&c") + plugin.money(amount) + " &7" + parts[3]);
            shown++;
        }
        if (log.isEmpty()) {
            log.add("&7Még nincs semmi.");
        }
        menu.set(31, button(Material.BOOK, "&f&lNapló", log));
        back(menu, 36, this::main);
        menu.open(player);
    }

    private static long parse(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    // ---- Resource Point ----

    public void points(Player player) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null) {
            return;
        }
        Menu menu = menu("points", 4, "Resource Point");
        KPlayer data = km().player(player);
        menu.set(4, button(Material.EMERALD_BLOCK, "&a&lResource Point", "&7A királyságé: " + rp(kingdom.resourcePoints), "&7Te eddig beadtál: " + rp(data.donated),
                "", "&7Ebből vásároltok fejlesztést, építményt,", "&7tornyot, földet és pajzsot."));
        menu.set(11, button(Material.CHEST, "&a&lTárgyak beadása", "&7Megnyílik egy láda: tedd bele, amit beváltanál,", "&7és csukd be. Ami nem váltható, visszakapod."),
                p -> plugin.donations().open(p, kingdom));
        List<String> values = new ArrayList<>();
        int count = 0;
        for (Map.Entry<String, Long> entry : cfg().itemPoints.entrySet()) {
            if (count++ >= 18) {
                values.add("&8... és még " + (cfg().itemPoints.size() - 18));
                break;
            }
            values.add("&7" + entry.getKey().toLowerCase(Locale.ROOT).replace('_', ' ') + ": &e" + entry.getValue() + " RP");
        }
        menu.set(13, button(Material.BOOK, "&f&lMit mennyiért?", values));
        menu.set(15, button(Material.GOLD_INGOT, "&e&lPénzért", "&7A bankban átválthattok pénzt RP-re:", "&f" + plugin.money(cfg().moneyPerPoint) + " &7= &e1 RP", "", "&b▶ A bank megnyitása"), this::bank);
        back(menu, 31, this::main);
        menu.open(player);
    }

    // ---- fejlesztések ----

    public void upgrades(Player player) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null) {
            return;
        }
        Menu menu = menu("upgrades", 4, "Fejlesztések");
        menu.set(4, button(Material.EXPERIENCE_BOTTLE, "&d&lKirálysági szint: " + km().level(kingdom), "&7XP: &f" + hu.icemine.core.util.Text.number(kingdom.xp),
                cfg().nextLevelXp(kingdom.xp) < 0 ? "&aElértétek a legmagasabb szintet!" : "&7Következő szint: &f" + hu.icemine.core.util.Text.number(cfg().nextLevelXp(kingdom.xp)) + " XP",
                "", "&7XP jár: RP beadásért, foglalásért,", "&7ölésért és sikeres invázióért.", "&7Szintenként több föld és tag."));
        menu.set(11, button(Material.ANVIL, "&6&lKirálysági fejlesztések", "&7Több föld, több tag, robbanásvédelem,", "&7Nexus őrök, dicsőség, riadó."), p -> category(p, "misc"));
        menu.set(13, button(Material.BLAZE_POWDER, "&c&lErősítések", "&7A saját földeteken erősebbek,", "&7ellenállóbbak és gyorsabban gyógyultok."), p -> category(p, "powerups"));
        menu.set(15, button(Material.ZOMBIE_HEAD, "&4&lBajnok", "&7Aki inváziókor megvédi a földeket."), this::champion);
        back(menu, 31, this::main);
        menu.open(player);
    }

    public void champion(Player player) {
        category(player, "champion");
    }

    private void category(Player player, String category) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null) {
            return;
        }
        String title = switch (category) {
            case "powerups" -> "Erősítések";
            case "champion" -> "Bajnok";
            default -> "Királysági fejlesztések";
        };
        Menu menu = menu("upgrades-" + category, 5, title);
        Map<String, Integer> levels = km().levels(kingdom, category);
        int slot = 10;
        for (KConfig.Upgrade upgrade : km().upgrades(category).values()) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot > 34) {
                break;
            }
            int level = kingdom.level(levels, upgrade.id);
            List<String> lore = new ArrayList<>(upgrade.description);
            lore.add("");
            lore.add("&7Szint: &f" + level + "&7/&f" + upgrade.max + " " + hu.icemine.core.util.Text.bar(upgrade.max == 0 ? 1 : level / (double) upgrade.max,
                    Math.min(20, Math.max(5, upgrade.max)), "&a▌", "&8▌"));
            long price = upgrade.priceFor(level);
            if (price < 0) {
                lore.add("&a✔ Maximum");
            } else {
                lore.add("&7Ár: " + rp(price));
                if (km().level(kingdom) < upgrade.minLevel) {
                    lore.add("&c✖ " + upgrade.minLevel + ". királysági szint kell");
                } else {
                    lore.add("&b▶ Kattints a fejlesztéshez");
                }
            }
            Items item = Items.of(material(upgrade.icon, Material.ANVIL)).name(upgrade.name).lore(lore).hideAll();
            if (level > 0) {
                item.glow();
            }
            menu.set(slot++, item.build(), p -> {
                km().buyUpgrade(p, category, upgrade.id);
                category(p, category);
            });
        }
        if (category.equals("champion")) {
            menu.set(4, button(Material.ZOMBIE_HEAD, "&4&lA bajnokotok", championStats(kingdom)));
        }
        menu.set(40, button(Material.EMERALD, "&a&lResource Point: &e" + kingdom.resourcePoints));
        back(menu, 36, category.equals("misc") || category.equals("powerups") ? this::upgrades : this::main);
        menu.open(player);
    }

    private List<String> championStats(Kingdom kingdom) {
        List<String> lore = new ArrayList<>();
        KConfig cfg = cfg();
        double health = cfg.championHealth + value(kingdom, "health");
        lore.add("&7Életerő: &c" + format(health) + " &8(+ Nexus földjén több)");
        lore.add("&7Sebzés: &f" + format(cfg.championDamage + value(kingdom, "damage")));
        String[] armor = {"nincs", "bőr", "lánc", "vas", "gyémánt", "netherit"};
        lore.add("&7Páncél: &f" + armor[Math.max(0, Math.min(armor.length - 1, kingdom.level(kingdom.champion, "armor")))]);
        lore.add("&7Invázió ideje: &f" + plugin.duration(cfg.invasionSeconds * 1000L));
        return lore;
    }

    private double value(Kingdom kingdom, String id) {
        KConfig.Upgrade upgrade = cfg().champion.get(id);
        return upgrade == null ? 0 : upgrade.value(kingdom.level(kingdom.champion, id));
    }

    // ---- építmények és tornyok ----

    public void structures(Player player) {
        shop(player, "structure");
    }

    public void turrets(Player player) {
        shop(player, "turret");
    }

    private void shop(Player player, String kind) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null) {
            return;
        }
        boolean turret = kind.equals("turret");
        Menu menu = menu(kind + "s", 5, turret ? "Tornyok" : "Nexus és építmények");
        Map<String, KConfig.Placeable> defs = turret ? cfg().turrets : cfg().structures;
        int slot = 10;
        for (KConfig.Placeable def : defs.values()) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot > 25) {
                break;
            }
            List<String> lore = new ArrayList<>(def.description);
            lore.add("");
            int placed = kingdom.count(kind, def.id);
            if (def.id.equals("nexus")) {
                lore.add(kingdom.hasNexus() ? "&a✔ Lerakva &7(" + kingdom.nexus[0] + ", " + kingdom.nexus[1] + ", " + kingdom.nexus[2] + ")" : "&eMég nincs lerakva!");
                lore.add(kingdom.hasNexus() ? "&7Szint: &f" + km().nexusLevel(kingdom) : "&b▶ Kattints, és megkapod a Nexus blokkot");
            } else {
                lore.add("&7Ár: " + rp(def.price));
                lore.add("&7Lerakva: &f" + placed + (def.limit > 0 ? "&7/&f" + def.limit : ""));
                lore.add("&7Max szint: &f" + def.max);
                if (km().level(kingdom) < def.minLevel) {
                    lore.add("&c✖ " + def.minLevel + ". királysági szint kell");
                } else if (!kingdom.hasNexus()) {
                    lore.add("&c✖ Előbb rakjátok le a Nexust");
                } else {
                    lore.add("&b▶ Kattints a vásárláshoz");
                }
            }
            menu.set(slot++, button(material(def.block, Material.STONE), def.name, lore), p -> {
                plugin.structures().buy(p, kind, def.id);
                shop(p, kind);
            });
        }
        menu.set(31, button(Material.CHEST_MINECART, turret ? "&f&lLerakott tornyok" : "&f&lLerakott építmények", "&7Lista, állapot, kezelés."), p -> placedList(p, kind, 0));
        menu.set(40, button(Material.EMERALD, "&a&lResource Point: &e" + kingdom.resourcePoints));
        back(menu, 36, this::main);
        menu.open(player);
    }

    private void placedList(Player player, String kind, int page) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null) {
            return;
        }
        Menu menu = menu("placed-" + kind, 6, kind.equals("turret") ? "Lerakott tornyok" : "Lerakott építmények");
        List<Placed> list = plugin.structures().of(kingdom, kind, null);
        paged(menu, list, page, placed -> {
            KConfig.Placeable def = plugin.structures().def(placed);
            List<String> lore = new ArrayList<>();
            lore.add("&7Hely: &f" + placed.x + ", " + placed.y + ", " + placed.z);
            lore.add("&7Szint: &f" + placed.level + (def == null ? "" : "&7/&f" + def.max));
            if (placed.turret()) {
                lore.add("&7Lőszer: &f" + placed.ammo);
            }
            lore.add("&7Állapot: " + (placed.enabled ? "&aműködik" : "&ckikapcsolva"));
            lore.add("");
            lore.add("&b▶ Kattints a kezeléshez");
            return button(material(def == null ? "STONE" : def.block, Material.STONE), def == null ? placed.type : def.name, lore);
        }, placed -> p -> placed(p, kingdom, placed), next -> placedList(player, kind, next));
        back(menu, 49, p -> shop(p, kind));
        menu.open(player);
    }

    /** Egy lerakott építmény vagy torony menüje. */
    public void placed(Player player, Kingdom kingdom, Placed placed) {
        KConfig.Placeable def = plugin.structures().def(placed);
        if (def == null || !kingdom.placed.contains(placed)) {
            return;
        }
        boolean own = kingdom.members.containsKey(player.getUniqueId());
        Menu menu = menu("placed", 5, plugin.strip(def.name));
        List<String> info = new ArrayList<>(def.description);
        info.add("");
        info.add("&7Szint: &f" + placed.level + "&7/&f" + def.max);
        switch (placed.type) {
            case "extractor" -> info.add("&7Összegyűlt: " + rp(placed.stored) + " &8/ " + Math.round(def.get("capacity", placed.level)));
            case "powercell" -> info.add("&7Véd: &f" + (int) Math.floor(def.get("radius", placed.level)) + " chunk sugarú körben");
            case "regenerator", "bastion" -> info.add("&7Hatótáv: &f" + format(def.get("radius", placed.level)) + " blokk");
            default -> {
                if (placed.turret()) {
                    info.add("&7Lőszer: &f" + placed.ammo);
                    if (def.values.containsKey("range")) {
                        info.add("&7Hatótáv: &f" + format(def.get("range", placed.level)) + " blokk");
                    }
                }
            }
        }
        menu.set(4, button(material(def.block, Material.STONE), def.name, info));
        if (own) {
            long price = def.upgradePrice(placed.level);
            menu.set(19, button(Material.ANVIL, "&d&lFejlesztés", price < 0 ? "&a✔ Maximum szint" : "&7Ár: " + rp(price), price < 0 ? "" : "&b▶ Kattints"), p -> {
                plugin.structures().upgrade(p, kingdom, placed);
                placed(p, kingdom, placed);
            });
            menu.set(21, button(placed.enabled ? Material.LIME_DYE : Material.GRAY_DYE, placed.enabled ? "&a&lBekapcsolva" : "&c&lKikapcsolva", "&b▶ Kattints a váltáshoz"), p -> {
                plugin.structures().toggle(p, kingdom, placed);
                placed(p, kingdom, placed);
            });
            if (!placed.type.equals("nexus")) {
                menu.set(25, button(Material.HOPPER, "&e&lFelvétel", "&7Visszakerül a táskádba (a szintje megmarad)."), p -> {
                    p.closeInventory();
                    plugin.structures().pickUp(p, kingdom, placed);
                });
            } else if (player.getUniqueId().equals(kingdom.leader)) {
                menu.set(25, button(Material.HOPPER, "&e&lNexus áthelyezése", "&7Felveszed, és máshol újra lerakhatod.", "", "&b▶ Kattints kétszer"),
                        p -> confirmThen(p, "nexus-move", () -> {
                            p.closeInventory();
                            plugin.structures().pickUp(p, kingdom, placed);
                        }, () -> placed(p, kingdom, placed)));
            }
        }
        if (placed.turret() && own) {
            menu.set(23, button(Material.ARROW, "&f&lLőszer feltöltése", "&7+" + cfg().ammoPerRefill + " lövés", "&7Ár: " + rp(cfg().ammoCost * cfg().ammoPerRefill)), p -> {
                plugin.structures().refill(p, kingdom, placed);
                placed(p, kingdom, placed);
            });
        }
        switch (placed.type) {
            case "nexus" -> menu.set(23, button(Material.BEACON, "&b&lKirályság menü", "&7Minden menü egy helyen."), this::main);
            case "extractor" -> {
                if (own) {
                    menu.set(23, button(Material.EMERALD, "&a&lBegyűjtés", "&7Összegyűlt: " + rp(placed.stored)), p -> {
                        plugin.structures().collect(p, kingdom, placed);
                        placed(p, kingdom, placed);
                    });
                }
            }
            case "warppad" -> menu.set(23, button(Material.ENDER_PEARL, "&5&lTeleport", "&7Válassz egy másik kaput."), p -> warps(p, kingdom, placed));
            case "outpost" -> menu.set(23, button(Material.EMERALD, "&a&lVásárlás", "&7Tárgyak Resource Pointért."), p -> outpost(p, kingdom, placed));
            default -> {
            }
        }
        back(menu, 40, own ? p -> placedList(p, placed.kind, 0) : Player::closeInventory);
        menu.open(player);
    }

    private void warps(Player player, Kingdom kingdom, Placed from) {
        Menu menu = menu("warps", 4, "Teleportkapuk");
        int slot = 10;
        for (Placed pad : plugin.structures().of(kingdom, "structure", "warppad")) {
            if (pad == from) {
                continue;
            }
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot > 25) {
                break;
            }
            menu.set(slot++, button(Material.END_PORTAL_FRAME, "&5&l" + (pad.label.isEmpty() ? "Kapu" : pad.label), "&7" + pad.x + ", " + pad.y + ", " + pad.z,
                    "&b▶ Teleport ide"), p -> {
                p.closeInventory();
                plugin.structures().warp(p, kingdom, from, pad);
            });
        }
        if (slot == 10) {
            menu.set(13, button(Material.PAPER, "&7Nincs másik kapu", "&7Rakjatok le még egyet."));
        }
        if (kingdom.members.containsKey(player.getUniqueId())) {
            menu.set(31, button(Material.NAME_TAG, "&e&lKapu elnevezése", "&7Most: &f" + (from.label.isEmpty() ? "-" : from.label)), p -> plugin.borders().ask(p, "prompt-warp-name", text -> {
                from.label = text.replace("&", "").substring(0, Math.min(16, text.replace("&", "").length()));
                kingdom.dirty = true;
                warps(p, kingdom, from);
            }));
        }
        back(menu, 27, p -> placed(p, kingdom, from));
        menu.open(player);
    }

    private void outpost(Player player, Kingdom kingdom, Placed placed) {
        Menu menu = menu("outpost", 4, "Kereskedőház");
        int slot = 10;
        for (Map.Entry<String, Long> entry : cfg().outpost.entrySet()) {
            Material material = Material.matchMaterial(entry.getKey());
            if (material == null || !material.isItem()) {
                continue;
            }
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot > 25) {
                break;
            }
            long price = entry.getValue();
            menu.set(slot++, button(material, "&f" + entry.getKey().toLowerCase(Locale.ROOT).replace('_', ' '), "&7Ár: " + rp(price), "&b▶ Kattints a vásárláshoz"), p -> {
                plugin.structures().buyFromOutpost(p, kingdom, material, price);
                outpost(p, kingdom, placed);
            });
        }
        menu.set(31, button(Material.EMERALD, "&a&lResource Point: &e" + kingdom.resourcePoints));
        back(menu, 27, p -> placed(p, kingdom, placed));
        menu.open(player);
    }

    // ---- kapcsolatok ----

    public void relations(Player player, int page) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null) {
            return;
        }
        Menu menu = menu("relations", 6, "Kapcsolatok");
        List<Kingdom> list = new ArrayList<>();
        for (UUID id : kingdom.relationRequests.keySet()) {
            Kingdom other = km().kingdom(id);
            if (other != null) {
                list.add(other);
            }
        }
        for (Kingdom other : km().top(Comparator.comparingInt((Kingdom k) -> km().relation(kingdom, k).ordinal()).thenComparing(k -> k.name))) {
            if (other != kingdom && !list.contains(other)) {
                list.add(other);
            }
        }
        paged(menu, list, page, other -> {
            Relation relation = km().relation(kingdom, other);
            Relation request = kingdom.relationRequests.get(other.id);
            List<String> lore = new ArrayList<>();
            lore.add("&7Kapcsolat: " + relation.display());
            lore.add("&7Szint: &f" + km().level(other) + " &8| &7Tagok: &f" + other.members.size() + " &8| &7Föld: &f" + other.claims.size());
            if (request != null) {
                lore.add("");
                lore.add("&e✉ " + (request == Relation.ALLY ? "Szövetséget" : "Fegyverszünetet") + " kértek tőletek!");
            }
            lore.add("");
            lore.add("&b▶ Kattints a módosításhoz");
            Material icon = switch (relation) {
                case NATION -> Material.LIGHT_BLUE_BANNER;
                case ALLY -> Material.MAGENTA_BANNER;
                case TRUCE -> Material.YELLOW_BANNER;
                case ENEMY -> Material.RED_BANNER;
                default -> request != null ? Material.LIME_BANNER : Material.WHITE_BANNER;
            };
            return button(icon, other.display(), lore);
        }, other -> p -> relationWith(p, other), next -> relations(player, next));
        back(menu, 49, this::main);
        menu.open(player);
    }

    private void relationWith(Player player, Kingdom other) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null) {
            return;
        }
        Relation relation = km().relation(kingdom, other);
        Menu menu = menu("relation", 4, other.name);
        menu.set(4, button(Material.BEACON, other.display(), "&7Kapcsolat: " + relation.display()));
        menu.set(10, button(Material.MAGENTA_BANNER, "&d&lSzövetség", "&7Kölcsönös: ők is el kell fogadják.", "&7Nem sebzitek egymást, a tornyok nem lőnek,", "&7mehettek egymás otthonába."), p -> {
            km().requestRelation(p, other.name, Relation.ALLY);
            relationWith(p, other);
        });
        menu.set(12, button(Material.YELLOW_BANNER, "&e&lFegyverszünet", "&7Kölcsönös: nem harcoltok egymással."), p -> {
            km().requestRelation(p, other.name, Relation.TRUCE);
            relationWith(p, other);
        });
        menu.set(14, button(Material.WHITE_BANNER, "&f&lSemleges", "&7Szövetség felbontása, vagy béke kötése."), p -> {
            km().requestRelation(p, other.name, Relation.NEUTRAL);
            relationWith(p, other);
        });
        menu.set(16, button(Material.RED_BANNER, "&c&lHáború", "&7Egyoldalú: ellenségnek nyilvánítjátok.", "&7Ezután megtámadhatjátok a földjeiket."), p -> {
            km().requestRelation(p, other.name, Relation.ENEMY);
            relationWith(p, other);
        });
        menu.set(22, button(Material.BOOK, "&f&lInfó", "&7A királyság adatai."), p -> info(p, other));
        if (relation.home && other.home != null) {
            menu.set(24, button(Material.RED_BED, "&c&lTeleport hozzájuk", "&7Az otthonukba."), p -> {
                p.closeInventory();
                km().home(p, other.name);
            });
        }
        back(menu, 27, p -> relations(p, 0));
        menu.open(player);
    }

    // ---- nemzet ----

    public void nation(Player player) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null) {
            return;
        }
        Nation nation = km().nationOf(kingdom);
        Menu menu = menu("nation", 5, nation == null ? "Nemzet" : nation.name);
        if (nation == null) {
            menu.set(11, button(Material.NETHER_STAR, "&9&lNemzet alapítása", "&7Ára: &f" + plugin.money(cfg().nationCost) + " &7a bankból",
                    "&7Kell hozzá: &f" + cfg().nationMinLevel + ". &7szint", "", "&b▶ Kattints, és írd be a nevét"), p -> plugin.borders().ask(p, "prompt-nation-name", text -> {
                        km().createNation(p, text);
                        nation(p);
                    }));
            menu.set(15, button(Material.PAPER, "&f&lMeghívás elfogadása", "&7Írd be a nemzet nevét."), p -> plugin.borders().ask(p, "prompt-nation-join", text -> {
                km().joinNation(p, text);
                nation(p);
            }));
        } else {
            Kingdom capital = km().kingdom(nation.capital);
            List<String> lore = new ArrayList<>();
            lore.add("&7Főváros: " + (capital == null ? "-" : capital.display()));
            lore.add("&7Királyságok: &f" + nation.kingdoms.size() + "/" + cfg().nationMaxKingdoms);
            int members = 0;
            int claims = 0;
            for (UUID id : nation.kingdoms) {
                Kingdom member = km().kingdom(id);
                if (member != null) {
                    members += member.members.size();
                    claims += member.claims.size();
                }
            }
            lore.add("&7Lakosok: &f" + members + " &8| &7Föld: &f" + claims);
            lore.add("&7Erő: &f" + hu.icemine.core.util.Text.number(km().nationMight(nation)));
            lore.add("&7Közös kassza: " + rp(nation.resourcePoints));
            lore.add("&7Alapítva: &f" + date.format(new Date(nation.created)));
            menu.set(4, button(Material.SHIELD, "&9&l" + nation.name, lore));
            int slot = 10;
            for (UUID id : nation.kingdoms) {
                Kingdom member = km().kingdom(id);
                if (member == null || slot > 16) {
                    continue;
                }
                boolean isCapital = id.equals(nation.capital);
                menu.set(slot++, button(isCapital ? Material.GOLD_BLOCK : Material.IRON_BLOCK, member.display(), isCapital ? "&6♛ Főváros" : "&7Tag",
                        isCapital ? "" : "&8Jobb klikk: kirúgás (csak a főváros királya)"), p -> info(p, member), p -> {
                    km().kickFromNation(p, member.name);
                    nation(p);
                });
            }
            menu.set(28, button(Material.WRITABLE_BOOK, "&a&lKirályság meghívása", "&7Írd be a királyság nevét."), p -> plugin.borders().ask(p, "prompt-kingdom-invite", text -> {
                km().inviteToNation(p, text);
                nation(p);
            }));
            menu.set(29, button(Material.EMERALD, "&e&l100 RP a közös kasszába", "&7A királyság RP-jéből."), p -> {
                km().donateToNation(p, 100);
                nation(p);
            });
            int index = 0;
            for (Map.Entry<Integer, Long> shield : cfg().shields.entrySet()) {
                if (index >= 3) {
                    break;
                }
                int hours = shield.getKey();
                long price = shield.getValue() * Math.max(1, nation.kingdoms.size());
                menu.set(30 + index++, button(Material.SHIELD, "&b&lNemzeti pajzs: " + hours + " óra", "&7Minden tagra érvényes.", "&7Ár: " + rp(price) + " &7a közös kasszából"), p -> {
                    km().buyNationShield(p, hours);
                    nation(p);
                });
            }
            menu.set(33, button(Material.OAK_DOOR, "&6&lKilépés a nemzetből", "&7A főváros nem léphet ki."), p -> {
                km().leaveNation(p);
                nation(p);
            });
            menu.set(34, button(Material.TNT, "&4&lNemzet feloszlatása", "&7Csak a főváros királya.", "", "&b▶ Kattints kétszer"), p -> {
                km().disbandNation(p);
                nation(p);
            });
        }
        menu.set(40, button(Material.GOLDEN_HELMET, "&6&lNemzetek toplistája"), this::nationTop);
        back(menu, 36, this::main);
        menu.open(player);
    }

    private void nationTop(Player player) {
        Menu menu = menu("nationtop", 4, "Nemzetek");
        List<Nation> list = new ArrayList<>(km().nations());
        list.sort(Comparator.comparingLong((Nation n) -> -km().nationMight(n)));
        int slot = 10;
        int place = 1;
        for (Nation nation : list) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot > 25) {
                break;
            }
            Kingdom capital = km().kingdom(nation.capital);
            menu.set(slot++, button(Material.SHIELD, "&e" + place++ + ". &9&l" + nation.name, "&7Főváros: " + (capital == null ? "-" : capital.display()),
                    "&7Királyságok: &f" + nation.kingdoms.size(), "&7Erő: &f" + hu.icemine.core.util.Text.number(km().nationMight(nation))));
        }
        if (list.isEmpty()) {
            menu.set(13, button(Material.PAPER, "&7Még nincs egy nemzet sem."));
        }
        back(menu, 31, this::nation);
        menu.open(player);
    }

    // ---- háború ----

    public void war(Player player) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null) {
            return;
        }
        Menu menu = menu("war", 5, "Háború");
        List<String> running = plugin.invasions().describe(kingdom);
        if (running.isEmpty()) {
            running = List.of("&7Most nincs invázió.");
        }
        menu.set(4, button(Material.IRON_SWORD, "&c&lInváziók", running));
        long cost = plugin.invasions().masswar() && cfg().masswarFree ? 0 : cfg().invadeCost;
        menu.set(10, button(Material.DIAMOND_SWORD, "&4&lInvázió itt", "&7Állj egy ellenséges földre, és kattints.", "&7Megjelenik a bajnokuk: ha legyőzitek",
                "&7" + plugin.duration(cfg().invasionSeconds * 1000L) + " alatt, a föld a tiétek.", "&7Ár: " + rp(cost)), p -> {
            p.closeInventory();
            plugin.invasions().start(p);
        });
        menu.set(11, button(Material.WHITE_BANNER, "&f&lFeladás", "&7A mostani inváziótok vége."), p -> {
            plugin.invasions().surrender(p);
            war(p);
        });
        List<String> enemies = new ArrayList<>();
        for (Kingdom enemy : km().withRelation(kingdom, Relation.ENEMY)) {
            if (enemies.size() < 15) {
                enemies.add("&c" + enemy.name + " &8(" + enemy.claims.size() + " föld)");
            }
        }
        if (enemies.isEmpty()) {
            enemies.add("&7Nincs ellenségetek.");
        }
        menu.set(13, button(Material.RED_BANNER, "&c&lEllenségek", enemies));
        long now = System.currentTimeMillis();
        List<String> status = new ArrayList<>();
        status.add("&7Pajzs: " + (kingdom.shieldUntil > now ? "&b" + plugin.duration(kingdom.shieldUntil - now) : "&cnincs"));
        status.add("&7Támadási várakozás: " + (kingdom.invadeCooldownUntil > now ? "&e" + plugin.duration(kingdom.invadeCooldownUntil - now) : "&anincs"));
        status.add("&7Győzelmek: &a" + kingdom.invasionsWon + " &8| &7Vereségek: &c" + kingdom.invasionsLost);
        status.add("&7Elfoglalt: &f" + kingdom.landsTaken + " &8| &7Elveszett: &f" + kingdom.landsLost + " föld");
        menu.set(15, button(Material.BOOK, "&f&lÁllapot", status));
        menu.set(16, button(kingdom.pacifist ? Material.LIME_BANNER : Material.GRAY_BANNER, "&a&lPacifizmus: " + (kingdom.pacifist ? "be" : "ki"),
                "&7Pacifista királyságot nem lehet megtámadni,", "&7de ti sem támadhattok, és nem lehet ellenségetek.",
                "&7Váltás után " + cfg().pacifistCooldownHours + " óráig nem lehet visszaváltani."), p -> {
            km().togglePacifist(p);
            war(p);
        });
        int index = 0;
        for (Map.Entry<Integer, Long> shield : cfg().shields.entrySet()) {
            if (index >= 5) {
                break;
            }
            int hours = shield.getKey();
            long price = shield.getValue();
            menu.set(19 + index * 2, button(Material.SHIELD, "&b&lPajzs: " + hours + " óra", "&7Ennyi ideig nem támadhatnak meg.", "&7Ha ti támadtok, a pajzs eltűnik.",
                    "&7Ár: " + rp(price)), p -> {
                km().buyShield(p, hours);
                war(p);
            });
            index++;
        }
        String masswar = plugin.invasions().masswar() ? "&4&lMOST VAN! &7Még: &f" + plugin.duration(plugin.invasions().masswarEnds() - now)
                : cfg().masswarEnabled ? "&7Következő: &f" + plugin.duration(Math.max(0, plugin.invasions().masswarNext() - now)) + " múlva" : "&7Kikapcsolva";
        menu.set(31, button(Material.TNT_MINECART, "&4&lMasswar", masswar, "&7Ilyenkor a semleges királyságokat is", "&7meg lehet támadni, és ingyenes az invázió."));
        back(menu, 36, this::main);
        menu.open(player);
    }

    // ---- toplista ----

    public void top(Player player, String sort) {
        Comparator<Kingdom> order = switch (sort) {
            case "level" -> Comparator.comparingLong((Kingdom k) -> -k.xp);
            case "claims" -> Comparator.comparingInt((Kingdom k) -> -k.claims.size());
            case "members" -> Comparator.comparingInt((Kingdom k) -> -k.members.size());
            default -> Comparator.comparingLong((Kingdom k) -> -km().might(k));
        };
        Menu menu = menu("top", 6, "Toplista");
        List<Kingdom> list = km().top(order);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < slots.length && i < list.size(); i++) {
            Kingdom kingdom = list.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("&7Király: &f" + km().nameOf(kingdom.leader));
            lore.add("&7Szint: &f" + km().level(kingdom) + " &8| &7Erő: &f" + hu.icemine.core.util.Text.number(km().might(kingdom)));
            lore.add("&7Tagok: &f" + kingdom.members.size() + " &8| &7Föld: &f" + kingdom.claims.size());
            lore.add("");
            lore.add("&b▶ Részletek");
            Material icon = i == 0 ? Material.GOLD_BLOCK : i == 1 ? Material.IRON_BLOCK : i == 2 ? Material.COPPER_BLOCK : Material.STONE_BRICKS;
            menu.set(slots[i], button(icon, "&e" + (i + 1) + ". " + kingdom.display(), lore), p -> info(p, kingdom));
        }
        if (list.isEmpty()) {
            menu.set(22, button(Material.PAPER, "&7Még nincs egy királyság sem."));
        }
        String[][] sorts = {{"might", "Erő", "NETHER_STAR"}, {"level", "Szint", "EXPERIENCE_BOTTLE"}, {"claims", "Föld", "GRASS_BLOCK"}, {"members", "Tagok", "PLAYER_HEAD"}};
        for (int i = 0; i < sorts.length; i++) {
            String id = sorts[i][0];
            Items item = Items.of(material(sorts[i][2], Material.PAPER)).name((id.equals(sort) ? "&a&l" : "&7") + "Rendezés: " + sorts[i][1]).hideAll();
            if (id.equals(sort)) {
                item.glow();
            }
            menu.set(46 + i, item.build(), p -> top(p, id));
        }
        menu.set(51, button(Material.SHIELD, "&9&lNemzetek"), this::nationTop);
        back(menu, 45, p -> {
            if (km().kingdomOf(p.getUniqueId()) == null) {
                start(p, 0);
            } else {
                main(p);
            }
        });
        menu.open(player);
    }

    // ---- beállítások ----

    public void settings(Player player) {
        Kingdom kingdom = km().mine(player);
        if (kingdom == null) {
            return;
        }
        Menu menu = menu("settings", 4, "Beállítások");
        menu.set(10, button(kingdom.open ? Material.OAK_DOOR : Material.IRON_DOOR, "&f&lBelépés: " + (kingdom.open ? "&anyitott" : "&cmeghívásos"),
                "&7Nyitott királyságba bárki beléphet."), p -> {
            km().toggleOpen(p);
            settings(p);
        });
        menu.set(11, button(Material.ORANGE_DYE, "&6&lSzín", "&7Most: " + kingdom.display(), "&b▶ Kattints a következőhöz"), p -> {
            km().cycleKingdomColor(p);
            settings(p);
        });
        menu.set(12, button(Material.WRITABLE_BOOK, "&f&lLeírás", "&7Most: &f" + (kingdom.description.isEmpty() ? "-" : kingdom.description)),
                p -> plugin.borders().ask(p, "prompt-description", text -> {
                    km().describe(p, text);
                    settings(p);
                }));
        menu.set(13, button(Material.NAME_TAG, "&e&lÁtnevezés", "&7Csak a király.", "&7Ár: &f" + plugin.money(plugin.getConfig().getLong("rename-cost", 5000)) + " &7a bankból"),
                p -> plugin.borders().ask(p, "prompt-kingdom-name", text -> {
                    km().rename(p, text);
                    settings(p);
                }));
        menu.set(14, button(kingdom.pacifist ? Material.LIME_BANNER : Material.GRAY_BANNER, "&a&lPacifizmus: " + (kingdom.pacifist ? "be" : "ki"),
                "&7Nem támadhatnak, de ti sem támadhattok."), p -> {
            km().togglePacifist(p);
            settings(p);
        });
        menu.set(15, button(Material.BELL, "&e&lKörüzenet", "&7Üzenet minden tagnak (címként is)."), p -> plugin.borders().ask(p, "prompt-announcement", text -> km().announce(p, text)));
        menu.set(16, button(Material.OAK_DOOR, "&6&lKilépés", "&7Kilépsz a királyságból."), p -> {
            p.closeInventory();
            km().leave(p);
        });
        menu.set(22, button(Material.TNT, "&4&lFeloszlatás", "&7Csak a király. Minden föld felszabadul.", "", "&b▶ Kattints kétszer"), p -> {
            km().disband(p);
            if (km().kingdomOf(p.getUniqueId()) != null) {
                settings(p);
            } else {
                p.closeInventory();
            }
        });
        back(menu, 27, this::main);
        menu.open(player);
    }

    // ---- napló ----

    private void log(Player player, Kingdom kingdom, int page) {
        Menu menu = menu("log", 6, "Napló");
        List<String> list = new ArrayList<>(kingdom.log);
        paged(menu, list, page, line -> {
            String[] parts = line.split("\\|", 2);
            String when = parts.length == 2 ? date.format(new Date(parse(parts[0]))) : "";
            return button(Material.PAPER, "&f" + (parts.length == 2 ? parts[1] : line), "&8" + when);
        }, line -> null, next -> log(player, kingdom, next));
        back(menu, 49, this::main);
        menu.open(player);
    }
}
