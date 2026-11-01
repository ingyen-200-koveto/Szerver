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

**Főmenü:** `/menu` (a hotbaron már nincs rá tárgy). Innen minden elérhető: játékmódok, kitek, Duels, profil
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

### Scoreboard és névtáblák

- Az oldalsó scoreboardon a játékos weboldalas IceCoin egyenlege látszik (`%icecoin%`). A szerver 30 másodpercenként
  lekérdezi a weboldaltól (https://icemine.hu/api/mc/coins), beállítás: `config.yml` -> `icecoin`.
  Ha nem akarod, hogy bárki lekérdezhesse az egyenlegeket, írj a weboldal `.env` fájljába egy `MC_API_KEY=...` sort,
  és ugyanezt a `config.yml`-ben az `icecoin.api-key`-hez.
- A játékosok feje fölött a név előtt a rang (LuckPerms előtag, ha nincs, az IceMine rang), alatta az életerő (pl. `20 ❤`).
  BedWarsban a rang helyett a csapat színe. Beállítás: `config.yml` -> `nametags`.

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

Nincsenek generátorok: mindenkinek saját farm szigete van 8 földdel, és a növényeket a Gens kapával kell
learatni (bal klikk a beérett növényre). Learatás után pár másodperc alatt visszanő. A termést a sziget
közepén lévő hordónál (jobb klikk), a `/elad` paranccsal vagy a menüben adod el.

- **Földek:** búza (ingyen), sárgarépa, krumpli, cékla, dinnye, tök, bogyó, netherbibircsók. Sorban nyithatók
  meg, mindegyik drágább, és többet ér a termése.
- **Kapa fejlesztések** (jobb klikk a kapával, az üllő a tér közepén, vagy `/gens kapa`): Bőség (extra termés),
  Kaszálás (3x3, 5x5, végül az egész föld egyszerre), Aranykéz (jobb ár), Szerencse (pénzt találsz a földben).
  Ahogy fejleszted, a kapa is jobb lesz: fa, kő, vas, arany, gyémánt, végül netherit.
- **Sziget fejlesztések:** termékenyebb föld, gyorsabb növekedés, jobb ár. A tagjaidnak is jár, amikor nálad aratnak.
- **Prestige:** ha minden föld nyitva van, pénzért újrakezded (a földek lezárulnak, a kapa marad), cserébe örökre +15% termés.
- **Rebirth:** 10 prestige után a prestige, a kapa és a fejlesztések is nullázódnak, de +75% termés jár.
- **Automatikus eladás:** megvehető, vagy a VIP+ rang adja.
- **Hátizsák:** ide megy a termés, ha tele a táskád. A nagy hátizsák megvehető, vagy az MVP rang adja.
- **Sziget tagok:** tagonként állítható, mit csinálhat: építés, ládák, aratás.
- **Sziget bezárása:** ha be van zárva, csak a tagjaid látogathatnak meg.
- **ELITE és LEGEND rang:** +20% / +40% esély extra termésre.

A régi, generátoros szigetek az első belépéskor maguktól farmmá alakulnak: annyi föld lesz nyitva, ahányas
szintű volt a sziget, és a generátorokért meg a megmaradt régi termésért (szén, vas...) pénzt kap vissza a játékos.

Parancsok: `/gens kapa`, `/gens fold` (következő föld megnyitása), `/gens backpack`, `/gens autosell`, `/gens prestige`,
`/gens rebirth`, `/gens members`, `/gens upgrades`, `/gens lock`. Beállítás: `plugins/IceMine/gens.yml`
(a földek a `fields`, a kapa a `hoe` résznél). Toplista: Gens prestige.

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

- a lobby (a fő világ), `ice_kitpvp`, `ice_skypvp`, `combofly_map`, gens, `kingdoms`, `duels`, `ice_bedwars`
- minden játékmódnak külön lobby világ: `lobby_kitpvp`, `lobby_skypvp`, `lobby_combofly`,
  `lobby_gens`, `lobby_kingdoms`, `lobby_duels`, `lobby_bedwars` (beállítás: `plugins/IceMine/modelobbies.yml`)
- minden mód lobbyjának saját témája van: KitPvP gladiátor aréna, SkyPvP felhők fölött, ComboFly ametiszt kristályok,
  Gens farm, Kingdoms vár udvar, Duels párbaj csarnok, BedWars tábor ágyakkal (`theme:` a `modelobbies.yml`-ben)

## Duels

13 mód: Classic, NoDebuff, Boxing, Combo, ComboFly, Bridge, OP, Sumo, BuildUHC, Axe, Sword, Crystal
és az IceMine saját kitje. Unranked és ranked sor (Elo), kihívás, visszavágó, best of 3, nézők.
Az arénák a `duels` világban vannak, beállítás: `plugins/IceMine/duels.yml`.
Classic: fallal körbezárt pálya lelátóval, sarok tornyokkal, fedezékekkel és csapat zászlókkal, négy témában
(`themes:` forest, desert, ice, nether). Sumo: japán kert vízzel, torii kapukkal. Bridge: két csapat sziget,
középen egy kis platform, a kapuk a szigetek végén. BuildUHC: dombos rét fákkal és tóval. Crystal: obszidián pálya.
Ha az arénákat újra kell építeni: `/duels rebuild` (a szabad arénákat építi újra, jog: `icemine.admin.duels`).
Parancsok: `/duels`, `/duel <név> [mód] [bo3]`, `/duel accept`, `/duel rematch`, `/duels spectate <név>`.

## BedWars

4 mód: Solo (8 csapat, 1 fő), Duó (8 csapat, 2 fő), 3v3v3v3 és 4v4v4v4. Négy saját pálya:
Jégcsúcs és Tölgyes (8 csapatos), Sziklavár és Homokkő (4 csapatos). A pályákat az IceMine építi fel az `ice_bedwars`
világban, az első indításkor egyesével (pár másodperc pályánként), a saját lobbyja a `lobby_bedwars`.

Benne van: csapatválasztás, generátorok (vas, arany, gyémánt, smaragd, szintlépés időre), bolt 8 kategóriával,
csapatfejlesztések (élesség, védelem, sietség, kovácsműhely, gyógyító kút), 4 féle csapda (sorban 3 db),
ágy rombolás, végső ölés, újraéledés, néző mód, statisztika, toplista, jutalom pénz (a rang szorzójával), ölés effektek.

Beszállás: a BedWars lobbyban (`lobby_bedwars`) négy szobor áll, Solo, Duó, 3v3v3v3 és 4v4v4v4. Rákattintasz, és már
viszlek is a legjobb szabad arénába, parancs nem kell. A szobrok fölött látszik, hányan játszanak az adott módban.
A szobrok a `plugins/IceMine/modelobbies.yml` fájlban vannak (`lobbies.bedwars.npcs`), áthelyezés: állj oda, és
`/icem admin npc move modelobby_bedwars/solo` (vagy duo, 3v3v3v3, 4v4v4v4).

Parancsok (nem kötelező): `/bw` (menü), `/bw play <solo|duo|3v3v3v3|4v4v4v4>`, `/bw leave`, `/bw nez [játékos]`, `/bw stats [játékos]`, `/bw top`.
Admin (`icemine.admin.bedwars`): `/bw list`, `/bw start <aréna>`, `/bw stop <aréna>`, `/bw rebuild <aréna>`, `/bw reload`.
Beállítás: `plugins/IceMine/bedwars.yml` (módok, hány aréna legyen módonként, melyik pályán, árak, generátorok, események).

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
