# IceMine lobby szerver

Purpur 1.20.6, Java 21.

## Mi van a repóban

- `server.jar`: Purpur 1.20.6 (build 2233)
- `eula.txt`: a Minecraft EULA elfogadása
- `plugins/*.jar`: az IceMine plugin (`IceMine.jar`, benne minden saját rendszer és játékmód) és a külső pluginok
  (ViaVersion, ViaBackwards, ViaRewind, AuthMe, LuckPerms, WorldEdit, EssentialsX, TAB, Citizens 2.0.35 b3500)
- `forras/`: az IceMine plugin forráskódja (a szerver nem használja)
- `kezdo/`: amit csak egyszer kell a helyére tenni (server.properties, config/paper-global.yml,
  AuthMe config és magyar szövegek, ComboFly pálya). Ezt az IceMine az első indításkor
  magától a helyére másolja, aztán leállítja a szervert. A második indítástól minden a helyén van.

A repóban csak olyan fájlok vannak, amikhez a szerver futás közben nem nyúl, ezért a Git Pull
mindig működik.

TAB 6.2.1: a Modrinthos univerzális jarból ki vannak véve a Java 25-tel fordított részek
(Fabric, Forge, NeoForge és a 26.2-es Paper modul), mert ezek miatt az 1.20.6-os szerver nem
tudta betölteni. A Bukkit/Paper rész érintetlen.

Citizens 2.0.35 build 3500: ez a build még támogatja az 1.20.6-ot (v1_20_R4). Az újabb buildekből
ez már kikerült, a régebbiekben még nincs benne, ezért ezt ne cseréld le.

## IceMine.jar

Az összes saját rendszer egy pluginban van. A beállítások a `plugins/IceMine` mappában vannak
(a Kingdoms beállítása: `plugins/IceMine/kingdoms.yml`, az adatai: `plugins/IceMine/kingdoms`).
A régi `plugins/IceMineCore` és `plugins/IceMineKingdoms` mappát az első indításkor magától
átköltözteti, semmi nem vész el.

Frissítéskor a configokban csak azokat az értékeket cseréli újra, amiket nem írtál át
(a saját módosításaid megmaradnak).

### Üzenetek

Minden üzenet `&b[IceMine] &7` előtaggal megy ki. Ezt egy helyen tudod átírni:
`plugins/IceMine/messages_hu.yml`, legfelül a `prefix` sor.
Az AuthMe és az EssentialsX üzeneteit is erre cseréli (magyarul). A régi fájljukat `.regi` néven
elmenti. Ha ezt nem kéred: `plugins/IceMine/config.yml` -> `kulso-uzenetek: false`.

### Parancsok

| Parancs | Mit csinál | Alapból kié |
|---|---|---|
| `/icem help` | a parancsok listája (csak az látszik, amihez jogod van) | mindenki |
| `/icem stats [játékos]` | profil és statisztikák | mindenki |
| `/icem kit [kit]` | kit választó (KitPvP, SkyPvP, ComboFly) | mindenki |
| `/icem arena` | KitPvP aréna választó | mindenki |
| `/icem rank [játékos]` | rangok és amit adnak | mindenki |
| `/icem setspawn <mód> [aréna]` | játékmód spawn ide | OP |
| `/icem reload [fájl]` | configok újratöltése | OP |
| `/icem admin ...` | adminisztráció (lobby, portál, pénz, hologram, NPC, újraépítés...) | OP |

A `/icemine` és az `/im` is működik. A régi parancsok (`/lobby`, `/spawn`, `/kit`, `/profil`...) is maradtak.

**Főmenü:** `/menu`, vagy a hotbaron a nether csillag. Innen minden elérhető: játékmódok, kitek, Duels, profil
(minden mód statisztikájával), toplisták, rangok, napi jutalom, kozmetikák, beállítások, kapcsolatok, információk,
parkour és Gens. Minden menüben van "Vissza" gomb a főmenübe.

### Jogok

Minden parancsnak külön joga van, egy helyen: `plugins/IceMine/permissions.yml`.
Akinek nincs joga, ezt kapja: `[IceMine] Ehhez nincs jogod!`

A `/pl`, `/plugins`, `/ver`, `/version`, `/about` és a többi pluginlistát vagy verziót mutató parancs
(LuckPerms, TAB, ViaVersion) a játékosoknak le van tiltva, és a TAB-os kiegészítésben sem látszik.
A lista: `plugins/IceMine/config.yml` -> `blocked-commands`. Az OP-k és az `icemine.admin.commands`
joggal rendelkezők használhatják. Az Essentials parancsait is csak az használhatja, akinek megvan hozzá
az `essentials.<parancs>` joga.

### Rangok

A rang nevét, előtagját és chat színét a LuckPerms adja (ezt te írod meg). Hogy melyik rang mit kap
az IceMine-ban (kitek, kozmetikák, pénz szorzó, egyéb jogok), azt a `plugins/IceMine/ranks.yml`-ben
állítod. Rangok: Default, VIP, VIP+, MVP, MVP+, ELITE, LEGEND, YOUTUBER, HELPER, MODERATOR, ADMIN, OWNER.
Mindegyik megkapja a gyengébbek jutalmait is. A KitPvP-ben 3 kit ingyenes (Harcos, Íjász, Tank), a többi rangokhoz kötött.

A LuckPerms csoport neve kisbetűs, `+` jel nélkül: `default`, `vip`, `vipplus`, `mvp`, `mvpplus`, `elite`,
`legend`, `youtuber`, `helper`, `moderator`, `admin`, `owner`. Példa (konzolba):

```
lp creategroup vip
lp group vip parent add default
lp group vip meta setprefix 100 "&a[VIP] "
lp user Jatekosnev parent add vip
```

A rangváltás fél percen belül életbe lép, újrabelépés nélkül is.

### KitPvP

15 kit, mindegyiknek saját képessége van. A képesség tárgy a hotbar utolsó helyén van, jobb klikkel
használod, utána újratöltődik (a scoreboardon látszik, mennyi van még hátra). A biztonságos zónában nem működik.

| Kit | Képesség | Rang |
|---|---|---|
| Harcos | Csatakiáltás (erő + gyorsaság) | ingyenes |
| Íjász | Nyílzápor (5 nyíl egyszerre) | ingyenes |
| Tank | Kőbőr (ellenállás) | ingyenes |
| Orgyilkos | Árnyéklépés (láthatatlanság) | VIP |
| Berserker | Vérszomj (Erő II) | VIP+ |
| Vadász | Füstbomba (vakítás a közelben) | VIP+ |
| Fagylovag | Jégvihar (lassítás a közelben) | MVP |
| Ugró | Nagy ugrás | MVP |
| Nindzsa | Villanás (előrelendülés) | MVP+ |
| Piromán | Tűzgolyó | ELITE |
| Villámmágus | Villámcsapás arra, akire nézel | ELITE |
| Halász | Horog (magadhoz rántod, akire nézel) | LEGEND |
| Alkimista | Gyógyfőzet (+4 szív) | LEGEND |
| Poszeidón | Szökőár (lökéshullám) | LEGEND |
| Jégkirály | Örök tél (erős lassítás + sebzés) | LEGEND |

Fejlesztések (kristályért, minden kitre érvényes): Élesség, Védelem, Extra élet, Gyorsabb képesség,
Kincsvadász (több pénz ölésért). A kit menü alján és a hotbaron (tapasztalat üveg) éred el.
Beállítás: `plugins/IceMine/kitpvp.yml` -> `kits` (képesség: `ability`) és `upgrades`.

Új kitet játékban is csinálhatsz: vedd fel a tárgyakat és a páncélt, aztán
`/icem kit create <név> [rang]` (törlés: `/icem kit delete <név>`). A név, a leírás és a képesség utána
a `kitpvp.yml`-ben finomítható.

### Gens

A `/gens` menüben minden elérhető:
- **Sziget fejlesztések:** nagyobb tároló, gyorsabb termelés, jobb ár.
- **Prestige:** ha a sziget elérte a legmagasabb szintet, pénzért újrakezded, cserébe örökre +15% termelés.
- **Rebirth:** 10 prestige után a prestige is nullázódik, de +75% termelés és +3 generátorhely jár.
- **Automatikus eladás:** megvehető, vagy a VIP+ rang adja.
- **Hátizsák:** ide megy a termés, ha tele a táskád. A nagy hátizsák megvehető, vagy az MVP rang adja.
- **Sziget tagok:** tagonként állítható, mit csinálhat: építés, ládák, generátor begyűjtés, generátor fejlesztés.
- **Sziget bezárása:** ha be van zárva, csak a tagjaid látogathatnak meg.
- **ELITE és LEGEND rang:** +2 / +4 generátorhely.

Parancsok: `/gens backpack`, `/gens autosell`, `/gens prestige`, `/gens rebirth`, `/gens members`, `/gens upgrades`,
`/gens lock`. Beállítás: `plugins/IceMine/gens.yml`. Új toplista: Gens prestige.

### ComboFly lökés

`plugins/IceMine/combofly.yml`: a lökés öt része külön állítható: Horizontal (`knockback-horizontal`),
Vertical (`knockback-vertical`), Air (`knockback-air-*`, ha az ellenfél már a levegőben van),
Ground (`knockback-ground-*`, ha a földön áll) és Combo (`combo-scaling`, `knockback-combo-horizontal`,
kombónként mennyit változik). Alapból felfelé dob és közel tartja az ellenfelet, így hosszú kombókat lehet csinálni.

### Forráskód

A plugin teljes forráskódja a `forras` mappában van (Maven projekt, Paper API, `mvn package` -> `IceMine.jar`).
A szerver nem használja, csak megőrzésre van itt.

## Világok

Ezeket az IceMine hozza létre és építi fel magától:

- a lobby (a fő világ), `ice_kitpvp`, `ice_skypvp`, `combofly_map`, gens, `kingdoms`
- minden játékmódnak külön lobby világ: `lobby_kitpvp`, `lobby_skypvp`, `lobby_combofly`,
  `lobby_gens`, `lobby_kingdoms`, `lobby_duels` (beállítás: `plugins/IceMine/modelobbies.yml`)
- minden mód lobbyjának saját témája van: KitPvP gladiátor aréna, SkyPvP felhők fölött, ComboFly ametiszt kristályok,
  Gens farm és bánya, Kingdoms vár udvar, Duels párbaj csarnok (`theme:` a `modelobbies.yml`-ben)

## Duels

13 mód: Classic, NoDebuff, Boxing, Combo, ComboFly, Bridge, OP, Sumo, BuildUHC, Axe, Sword, Crystal
és az IceMine saját kitje. Unranked és ranked sor (Elo), kihívás, visszavágó, best of 3, nézők.
Az arénák a `duels` világban vannak, beállítás: `plugins/IceMine/duels.yml`.
Parancsok: `/duels`, `/duel <név> [mód] [bo3]`, `/duel accept`, `/duel rematch`, `/duels spectate <név>`.

## Első telepítés

1. A szerver mappája legyen üres.
2. Git Clone: https://github.com/ingyen-200-koveto/Szerver.git
3. Indítsd el. Pár másodperc múlva magától leáll (ez az első beállítás).
4. Indítsd el újra.

## Frissítés

1. Állítsd le a szervert.
2. Git Pull. Ha hibát ad, akkor Hard Reset.
3. Indítsd el.

A repóban lévő fájlokat a szerveren ne töröld és ne írd át, különben a Git Pull hibát ad.
