package hu.icemine.kingdoms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class KingdomsTest {

    private static YamlConfiguration shipped() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(KingdomsTest.class.getResourceAsStream("/kingdoms.yml"), StandardCharsets.UTF_8));
    }

    private static KConfig config() {
        KConfig cfg = new KConfig();
        List<String> problems = new ArrayList<>();
        cfg.load(shipped(), problems);
        assertTrue(problems.isEmpty(), problems.toString());
        return cfg;
    }

    @Test
    void shippedConfigLoadsEverything() {
        KConfig cfg = config();
        assertEquals(8, cfg.misc.size());
        assertEquals(4, cfg.powerups.size());
        assertEquals(10, cfg.champion.size());
        assertEquals(8, cfg.structures.size());
        assertEquals(8, cfg.turrets.size());
        assertEquals(200, cfg.claimCostRp);
        assertEquals(100, cfg.claimCostMoney);
        assertEquals(5, cfg.claimBuffer);
        assertNotNull(cfg.structures.get("regulator"));
        KConfig.Placeable laser = cfg.turrets.get("laser");
        assertNotNull(laser);
        assertTrue(laser.get("through-walls", 1) > 0, "a lézer a falon át is lő");
        assertTrue(laser.get("damage", 1) > cfg.turrets.get("arrow").get("damage", 1) * 2, "a lézer nagyot sebez");
        assertTrue(laser.values.get("ammo-cost") > cfg.ammoCost, "a lézer lőszere drágább");
        assertEquals(5, cfg.defaultRanks.size());
        assertEquals(3, cfg.shields.size());
        assertEquals(10, cfg.maxLevel());
        assertTrue(cfg.itemPoints.get("DIAMOND") > cfg.itemPoints.get("IRON_INGOT"));
        assertEquals(0, cfg.structures.get("nexus").price, "a Nexus ingyen jár");
        for (KConfig.Placeable turret : cfg.turrets.values()) {
            assertTrue(turret.price > 0, turret.id);
            assertTrue(turret.block.contains("SKULL") || turret.block.contains("HEAD") || turret.block.contains("PLATE") || turret.block.equals("END_ROD"), turret.id);
        }
    }

    @Test
    void regulatorFlagsToggleAndSurviveSave() {
        Placed placed = new Placed(java.util.UUID.randomUUID(), "structure", "regulator", 10, 64, 20, 1);
        assertFalse(Regulator.off(placed, "pvp"));
        assertTrue(Regulator.toggle(placed, "pvp"));
        assertTrue(Regulator.toggle(placed, "monsters"));
        assertTrue(Regulator.off(placed, "pvp"));
        assertFalse(Regulator.toggle(placed, "pvp"));
        assertFalse(Regulator.off(placed, "pvp"));
        assertTrue(Regulator.off(placed, "monsters"));
        placed.label = "monsters, fire ,ismeretlen,fire";
        assertEquals(java.util.List.of("monsters", "fire"), Regulator.disabled(placed));
        assertEquals(4, Regulator.flags().size());
        for (String flag : Regulator.flags()) {
            assertNotNull(org.bukkit.Material.matchMaterial(Regulator.FLAGS.get(flag)[0]), flag);
        }
    }

    @Test
    void levelsFollowXp() {
        KConfig cfg = config();
        assertEquals(1, cfg.level(0));
        assertEquals(1, cfg.level(999));
        assertEquals(2, cfg.level(1000));
        assertEquals(5, cfg.level(15000));
        assertEquals(10, cfg.level(Long.MAX_VALUE / 4));
        assertEquals(1000, cfg.nextLevelXp(0));
        assertEquals(-1, cfg.nextLevelXp(250000));
    }

    @Test
    void upgradesGetMoreExpensiveAndStopAtMax() {
        KConfig cfg = config();
        KConfig.Upgrade claims = cfg.misc.get("max-claims");
        assertEquals(300, claims.priceFor(0));
        assertEquals(480, claims.priceFor(1));
        assertEquals(-1, claims.priceFor(claims.max));
        assertEquals(5, claims.value(5), 1e-9);
        assertEquals(10, claims.value(99), 1e-9, "a szint nem lehet több a maximumnál");
        KConfig.Placeable arrow = cfg.turrets.get("arrow");
        assertEquals(12, arrow.get("range", 1), 1e-9);
        assertEquals(14, arrow.get("range", 5), 1e-9);
        assertEquals(16, arrow.get("cooldown", 5), 1e-9, "a töltési idő szintenként csökken");
        assertEquals(80, arrow.upgradePrice(1));
        assertEquals(-1, arrow.upgradePrice(arrow.max));
    }

    @Test
    void relationsNeedBothSidesExceptWar() {
        assertEquals(Relation.ENEMY, Relation.resolve(Relation.ENEMY, Relation.NEUTRAL));
        assertEquals(Relation.ENEMY, Relation.resolve(Relation.ALLY, Relation.ENEMY), "ha az egyik hadat üzent, háború van");
        assertEquals(Relation.ALLY, Relation.resolve(Relation.ALLY, Relation.ALLY));
        assertEquals(Relation.NEUTRAL, Relation.resolve(Relation.ALLY, Relation.NEUTRAL), "a szövetséghez mindkét fél kell");
        assertEquals(Relation.NEUTRAL, Relation.resolve(Relation.ALLY, Relation.TRUCE));
        assertEquals(Relation.TRUCE, Relation.resolve(Relation.TRUCE, Relation.TRUCE));
        assertEquals(Relation.NEUTRAL, Relation.resolve(null, null));
        config();
        assertTrue(Relation.ENEMY.invade);
        assertFalse(Relation.ALLY.invade);
        assertTrue(Relation.ALLY.ceasefire && Relation.ALLY.turretCeasefire);
        assertFalse(Relation.NATION.build, "a nemzettársak alapból nem építhetnek egymásnál");
    }

    private static Kingdom kingdom(UUID king) {
        Kingdom kingdom = new Kingdom(UUID.randomUUID(), "Jegvar", king, 1000L);
        for (Rank rank : Rank.defaults()) {
            kingdom.ranks.put(rank.id, rank.copy());
        }
        kingdom.members.put(king, Rank.KING);
        return kingdom;
    }

    @Test
    void ranksAndPermissions() {
        UUID king = UUID.randomUUID();
        UUID knight = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Kingdom kingdom = kingdom(king);
        kingdom.members.put(knight, "knight");
        kingdom.members.put(member, "member");
        assertTrue(kingdom.can(king, KPerm.MANAGE_RANKS), "a király mindent megtehet");
        assertTrue(kingdom.can(knight, KPerm.CLAIM));
        assertFalse(kingdom.can(knight, KPerm.KICK));
        assertFalse(kingdom.can(member, KPerm.CLAIM));
        assertTrue(kingdom.can(member, KPerm.BUILD));
        assertTrue(kingdom.outranks(king, knight));
        assertTrue(kingdom.outranks(knight, member));
        assertFalse(kingdom.outranks(member, knight));
        assertEquals("member", kingdom.lowest().id);
        assertEquals(Rank.KING, kingdom.sortedRanks().get(0).id);
        kingdom.members.put(member, "nincs-ilyen");
        assertEquals("member", kingdom.rankOf(member).id, "ismeretlen rang helyett a legalsó");
    }

    @Test
    void kingdomSurvivesSaveAndLoad() throws Exception {
        UUID king = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Kingdom kingdom = kingdom(king);
        kingdom.members.put(other, "duke");
        kingdom.claims.add(Lands.key(-3, 7));
        kingdom.claims.add(Lands.key(-2, 7));
        kingdom.claimers.put(Lands.key(-3, 7), other);
        kingdom.home = new double[]{-40.5, 70, 120.5, 90};
        kingdom.nexus = new int[]{-41, 69, 121};
        kingdom.bank = 12345;
        kingdom.resourcePoints = 678;
        kingdom.xp = 3500;
        kingdom.pacifist = true;
        kingdom.relations.put(UUID.randomUUID(), Relation.ENEMY);
        kingdom.misc.put("max-claims", 3);
        kingdom.champion.put("health", 4);
        kingdom.ranks.get("duke").name = "Gróf";
        kingdom.ranks.get("duke").perms.remove(KPerm.KICK);
        Placed turret = new Placed(UUID.randomUUID(), "turret", "arrow", -40, 70, 118, 3);
        turret.ammo = 50;
        kingdom.placed.add(turret);
        kingdom.log("Teszt bejegyzés");
        kingdom.bankLog("Teszt", 500, "befizetés");

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(KingdomStore.serialize(kingdom));
        Kingdom copy = Kingdom.read(kingdom.id, yaml, Rank.defaults());
        assertEquals("Jegvar", copy.name);
        assertEquals(king, copy.leader);
        assertEquals("duke", copy.members.get(other));
        assertEquals("Gróf", copy.ranks.get("duke").name);
        assertFalse(copy.ranks.get("duke").perms.contains(KPerm.KICK));
        assertEquals(kingdom.claims, copy.claims);
        assertEquals(other, copy.claimers.get(Lands.key(-3, 7)));
        assertEquals(1, copy.claimedBy(other));
        assertEquals(-40.5, copy.home[0], 1e-9);
        assertEquals(121, copy.nexus[2]);
        assertEquals(Lands.key(-41 >> 4, 121 >> 4), copy.nexusChunk());
        assertEquals(12345, copy.bank);
        assertEquals(678, copy.resourcePoints);
        assertEquals(3500, copy.xp);
        assertTrue(copy.pacifist);
        assertEquals(1, copy.relations.size());
        assertEquals(3, copy.level(copy.misc, "max-claims"));
        assertEquals(4, copy.level(copy.champion, "health"));
        assertEquals(1, copy.placed.size());
        assertEquals(50, copy.placed.get(0).ammo);
        assertEquals(3, copy.placed.get(0).level);
        assertNotNull(copy.placedAt(-40, 70, 118));
        assertEquals(1, copy.log.size());
        assertEquals(1, copy.bankLog.size());
    }

    @Test
    void oldSaveWithoutRanksGetsDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        UUID king = UUID.randomUUID();
        yaml.set("name", "Regi");
        yaml.set("leader", king.toString());
        Kingdom kingdom = Kingdom.read(UUID.randomUUID(), yaml, Rank.defaults());
        assertEquals(5, kingdom.ranks.size());
        assertEquals(Rank.KING, kingdom.members.get(king));
    }

    @Test
    void landKeys() {
        long key = Lands.key(-12, 345);
        assertEquals(-12, Lands.x(key));
        assertEquals(345, Lands.z(key));
        assertEquals("-12;345", Lands.text(key));
        assertEquals(key, (long) Lands.parse("-12;345"));
        assertNull(Lands.parse("nem;jó"));
        assertNull(Lands.parse("1;2;3"));
        Set<Long> neighbours = new HashSet<>();
        for (long next : Lands.neighbours(Lands.key(0, 0))) {
            neighbours.add(next);
        }
        assertTrue(neighbours.contains(Lands.key(1, 0)) && neighbours.contains(Lands.key(0, -1)));
        List<String> map = Lands.render(0, 0, 3, 2, k -> "&8■", "&e◆");
        assertEquals(5, map.size());
        assertTrue(map.get(2).contains("&e◆"));
        Set<Long> claims = new HashSet<>(List.of(Lands.key(0, 0), Lands.key(1, 0), Lands.key(5, 5)));
        assertEquals(2, Lands.islands(claims));
    }

    @Test
    void legacySaveIsConverted() throws Exception {
        File folder = Files.createTempDirectory("kingdoms").toFile();
        File file = new File(folder, "kingdoms.yml");
        UUID king = UUID.randomUUID();
        UUID knight = UUID.randomUUID();
        YamlConfiguration old = new YamlConfiguration();
        old.set("names." + king, "Kiraly");
        old.set("names." + knight, "Lovag");
        old.set("kingdoms.jegvar.name", "Jegvar");
        old.set("kingdoms.jegvar.leader", king.toString());
        old.set("kingdoms.jegvar.created", 5L);
        old.set("kingdoms.jegvar.members." + king, "KING");
        old.set("kingdoms.jegvar.members." + knight, "KNIGHT");
        old.set("kingdoms.jegvar.claims", List.of("1;2", "1;3"));
        old.set("kingdoms.jegvar.home.x", 20.5);
        old.set("kingdoms.jegvar.home.y", 70.0);
        old.set("kingdoms.jegvar.home.z", 40.5);
        old.set("kingdoms.jegvar.home.yaw", 0.0);
        old.save(file);
        KingdomStore.Loaded loaded = KingdomStore.convertLegacy(file, Rank.defaults(), java.util.logging.Logger.getAnonymousLogger());
        assertEquals(1, loaded.kingdoms.size());
        Kingdom kingdom = loaded.kingdoms.get(0);
        assertEquals("Jegvar", kingdom.name);
        assertEquals(Rank.KING, kingdom.members.get(king));
        assertEquals("knight", kingdom.members.get(knight));
        assertEquals(2, kingdom.claims.size());
        assertEquals(20.5, kingdom.home[0], 1e-9);
        assertEquals(2, loaded.players.size());
        for (KPlayer player : loaded.players) {
            assertEquals(kingdom.id, player.kingdom);
        }
        assertFalse(file.exists(), "a régi fájl átnevezve");
        assertTrue(new File(folder, "kingdoms.yml.regi").exists());
    }

    @Test
    void nationSurvivesSaveAndLoad() throws Exception {
        UUID capital = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Nation nation = new Nation(UUID.randomUUID(), "Eszak", capital, 7L);
        nation.kingdoms.add(member);
        nation.resourcePoints = 900;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(KingdomStore.serialize(nation));
        Nation copy = Nation.read(nation.id, yaml);
        assertEquals("Eszak", copy.name);
        assertEquals(capital, copy.capital);
        assertEquals(2, copy.kingdoms.size());
        assertEquals(900, copy.resourcePoints);
    }

    @Test
    void playerSurvivesSaveAndLoad() throws Exception {
        KPlayer player = new KPlayer(UUID.randomUUID(), "Teszt");
        player.kingdom = UUID.randomUUID();
        player.power = 7.5;
        player.kills = 3;
        player.donated = 120;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(KingdomStore.serialize(player));
        KPlayer copy = KPlayer.read(player.uuid, yaml);
        assertEquals(player.kingdom, copy.kingdom);
        assertEquals(7.5, copy.power, 1e-9);
        assertEquals(3, copy.kills);
        assertEquals(120, copy.donated);
    }

    @Test
    void brokenConfigIsReported() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("misc-upgrades.repules.max", 3);
        yaml.set("turrets.lezer.price", 10);
        yaml.set("shields.egesz-nap", 100);
        yaml.set("default-ranks.tag.name", "Tag");
        List<String> problems = new ArrayList<>();
        KConfig cfg = new KConfig();
        cfg.load(yaml, problems);
        assertEquals(4, problems.size(), problems.toString());
        assertEquals(5, cfg.defaultRanks.size(), "király nélkül az alap rangok maradnak");
        assertEquals(8, cfg.turrets.size());
    }
}
