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
(a Kingdoms beállítása: `plugins/IceMine/kingdoms.yml`, az adatai az IceMine adatbázisában, SQLite vagy MySQL).
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
| `/icem kit [kit]` | kitek (ComboFly) | mindenki |
| `/icem rank [játékos]` | rangok és amit adnak | mindenki |
| `/icem setspawn <mód> [aréna]` | játékmód spawn ide | OP |
| `/icem reload [fájl]` | configok újratöltése | OP |
| `/icem admin ...` | adminisztráció (lobby, portál, pénz, hologram, NPC, újraépítés...) | OP |

A `/icemine` és az `/im` is működik. A régi parancsok (`/lobby`, `/spawn`, `/kit`, `/profil`...) is maradtak.

**Játékmód választó** (iránytű a hotbaron): Fyland (fűblokk), Gens (gyémánt kapa),
Duels (netherit kard), BedWars (piros ágy), Kingdoms (aranyblokk), ComboFly, és a Creative "Hamarosan" felirattal.
Ha a Creative elkészül, a `modes.yml`-ben a `status: soon` helyett `open` kell.

**Kozmetikák:** a nyomvonalak és az ölés effektek csak a lobbyban (és a játékmódok lobbyjaiban) látszanak,
játék közben nem. Visszakapcsolás: `plugins/IceMine/lobby.yml` -> `cosmetics.lobby-only: false`.

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
- A játékosok feje fölött a név előtt a rang (LuckPerms előtag, ha nincs, az IceMine rang), a név mellett az életerő
  (pl. `Peti 20❤`). BedWarsban a rang helyett a csapat színe. Beállítás: `config.yml` -> `nametags`
  (`health-position: below` esetén a név alatt látszik, mint régen).
- Tablista (Tab gomb): fejléc és lábléc a `scoreboard.yml` -> `tablist` részből, a nevek előtt a rang, a magasabb rang
  van feljebb. A TAB pluginon keresztül írja ki, így a TAB alap szövegei (Server name, memória, ping számok) nem látszanak.
- Rangot és IceCoint csak a weboldalon lehet venni, a játékban nincs rangvásárló parancs.

### Rangok

A rangokat az IceMine magától létrehozza a LuckPermsben az első indításkor (előtag, súly, név, szülő rang, jogok).
Minden a `plugins/IceMine/ranks.yml`-ben van: mit kap a rang az IceMine-ban (kozmetikák, pénz szorzó, jogok) és
milyen a LuckPermsben (`luckperms` rész). Mindegyik rang megkapja a gyengébbek IceMine jutalmait is.

| Rang | LuckPerms csoport | Súly | Szülő |
|---|---|---|---|
| Játékos | `default` | 0 | |
| Bronz | `bronz` | 10 | |
| Wizard | `wizard` | 20 | bronz |
| IceKing | `iceking` | 30 | wizard |
| Yeti | `yeti` | 40 | iceking |
| Média | `media` | 100 | |
| Építész | `epitesz` | 150 | |
| Jn.Moderátor | `jnmoderator` | 300 | |
| Moderátor | `moderator` | 400 | jnmoderator |
| Sr.Moderátor | `srmoderator` | 500 | moderator |
| Admin | `admin` | 600 | srmoderator |
| Sr.Admin | `sradmin` | 700 | admin |
| Fejlesztő | `fejleszto` | 800 | sradmin |
| Manager | `manager` | 900 | fejleszto |
| Tulajdonos | `tulajdonos` | 1000 | manager (minden jog) |

Rangot adni (konzolba): `lp user Jatekosnev parent add iceking`. A rangváltás fél percen belül életbe lép.

Amit egyszer létrehozott, azt az IceMine többet nem állítja át (a lista: `plugins/IceMine/.luckperms-kesz`), így a
LuckPermsben nyugodtan átírhatod. Ha újra kell, töröld a fájlt, és indítsd újra a szervert.

### Fyland (OneBlock)

Mindenki kap egy saját szigetet a semmi közepén, ami egyetlen blokkból áll. Ha kibányászod, a helyén azonnal új blokk
jelenik meg, a blokkok 11 fázisban jönnek: Síkság, Föld alatt, Fagyos tundra, Óceán, Dzsungel, Mocsár, Mélység,
Sivatag, Nether, Virágos rét, End. Néha láda vagy mob is jön, minden fázis végén jutalom láda jár, az End után új kör
kezdődik. A fázis és a haladás fent a bossbaron látszik.

- Csapat: meghívás, kirúgás, tulajdon átadása, látogatók be- és kikapcsolása, otthon beállítása, újrakezdés.
- Piac: amit kibányásztál, eladod (Szigetérme, a rang pénz szorzójával), és vehetsz vizet, lávát, csemetét, magokat, tojásokat.
- Fejlesztések: sziget méret (51x51-től 201x201-ig), csapat méret (2-8 fő), szerencse (több láda).
- Ha leesel, visszahoz a szigetedre, halálkor megmaradnak a tárgyak. Enderláda és portál itt nem használható.
- Sziget szint: a szigetre rakott értékes blokkok (vas-, arany-, gyémánt-, smaragd-, netheritblokk, jeladó...) összértéke.
  Lerakáskor nő, kiütéskor csökken, a `/fy szint` az egész szigetet újraszámolja. A toplista szint szerint megy.
  A blokkok értéke: `fyland.yml` -> `block-values`.
- Csapat: moderátorok (`/fy promote`, `/fy demote`: meghívhatnak, kirúghatnak, kitilthatnak), ideiglenes segítő
  (`/fy coop <játékos>`, amíg fent van, építhet), kitiltás (`/fy ban`, `/fy unban`), zárás (`/fy lock`, `/fy unlock`),
  sziget név (`/fy create <név>`), adatok (`/fy info [játékos]`).
- Látogatás: a látogatók a látogatási pontra érkeznek (`/fy setwarp`). Amíg nincs ilyen, a szigetre nem lehet átmenni
  (kikapcsolható: `visit.require-warp: false`). Ha nevet is adsz (`/pw set <név>`), a sziget felkerül a játékos warpok
  közé: `/pw` (lista, a legjobbra értékeltek elöl), `/pw <név>`, `/pw rate <név> <1-5>`.
- Csatatér: PvP aréna a központ mellett, híd vezet oda (`/fy csatater`). Csak itt lehet ütni egymást, és aki itt hal meg,
  annak leesnek a tárgyai. 20 percenként ellátmány láda érkezik rá (ha legalább 2 fő van a Fylandon).
- Varázsló (`/enchanter`): 8 egyedi varázslat XP szintért (Autoszmelt, Telepátia, Tapasztalat, Robbanó, Vámpír, Méreg,
  Gyorsaság, Éjjellátás). A könyvet a táskádban húzd rá a tárgyra, a könyvön látszik a siker és a tönkremenés esélye.
  Csak a Fylandon hatnak.
- Warp menü: `/fywarp` (szigeted, központ, csatatér, játékos warpok, varázsló).

Parancsok: `/fy` (menü, vagy `/fyland`, `/is`), `/fy haza`, `/fy piac`, `/fy elad` (vagy `/elad`), `/fy meghiv <játékos>`,
`/fy elfogad`, `/fy latogat <játékos>`, `/fy fejlesztes`, `/fy fazisok`, `/fy top`, `/fy help` a teljes listához.
Admin (`icemine.admin.fyland`): `/fy admin fazis <játékos> <fázis>`, `/fy admin torol <játékos>`, `/fy admin tp <játékos>`.
Beállítás: `plugins/IceMine/fyland.yml` (fázisok, blokkok, ládák, mobok, árak, fejlesztések, üzenetek).

### Piac (Kingdoms és Fyland)

Közös piac a Kingdomsnak és a Fylandnak, de külön pénzzel és külön tárgyakkal (Kingdoms: Dollár, Fyland: Szigetérme),
hogy a két mód ne keveredjen. Beállítás: `plugins/IceMine/market.yml`.

- `/ah`: aukciósház (`/ah sell <ár>` a kezedben lévő tárgyra, 48 óráig él, adó, lejárt tárgyak visszaszedése).
- `/trade <játékos>`: biztonságos csere menüben, pénzzel is, mindkét fél jóváhagyása és 3 mp visszaszámlálás után.
- `/shop` (vagy `/bolt`): bolt, aminek az árai a kereslettől mozognak (amit sokan eladnak, olcsóbb lesz). `/sell ha` és `/sell all`.
- Eladó láda (magától eladja, ami belekerül), Craftoló (tömöríti a tartalmát: 9 vas = 1 vasblokk...), Farm blokk
  (betöltve tartja a környékét). Ezeket a boltban lehet venni.
- `/lottery`: napi lottó (max 3 jegy), a nyertes viszi a kasszát és kulcsokat.
- Kulcsok: jobb klikkre nyílnak, bal klikkre mutatják a tartalmukat. `/kulcs`: a rang heti kulcs csomagja.
- `/baltop`: a leggazdagabbak abban a módban, ahol állsz.
- Admin (`icemine.admin.market`): `/market give <játékos> <sellchest|crafter|farmblock|key:<név>> [db]`, `/market draw <mód>`.

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

### ComboFly lökés és 1.8-as PvP

A ComboFlyban 1.8-as a PvP: nincs ütés cooldown és söprő ütés (`combofly.yml` -> `old-pvp`, `false` = 1.9+).

`plugins/IceMine/combofly.yml`: a lökés öt része külön állítható: Horizontal (`knockback-horizontal`),
Vertical (`knockback-vertical`), Air (`knockback-air-*`, ha az ellenfél már a levegőben van),
Ground (`knockback-ground-*`, ha a földön áll) és Combo (`combo-scaling`, `knockback-combo-horizontal`,
kombónként mennyit változik). Alapból felfelé dob és közel tartja az ellenfelet, így hosszú kombókat lehet csinálni.
Kombó közben nincs részecske és hang effekt, csak a számláló az action baron (`particles-enabled`, `sounds-enabled`).

### Forráskód

A plugin teljes forráskódja a `forras` mappában van (Maven projekt, Paper API, `mvn package` -> `IceMine.jar`).
A szerver nem használja, csak megőrzésre van itt.

## Világok

Ezeket az IceMine hozza létre és építi fel magától:

- a lobby (a fő világ), `fyland`, `combofly_map`, gens, `kingdoms`, `duels`, `ice_bedwars`
- minden játékmódnak külön lobby világ: `lobby_fyland`, `lobby_combofly`,
  `lobby_gens`, `lobby_kingdoms`, `lobby_duels`, `lobby_bedwars` (beállítás: `plugins/IceMine/modelobbies.yml`)
- minden mód lobbyjának saját témája van: Fyland felhők fölötti szigetek, ComboFly ametiszt kristályok,
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
A csapat szigetek és a gyémánt szigetek messze vannak egymástól, hídépítés nélkül nem lehet átjutni.

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

## Kingdoms

Saját írású királyság rendszer a `kingdoms` világban (a KingdomsX csak ötletnek kellett, a kódja nincs benne).
Minden menüből elérhető, a `/k` megnyitja a főmenüt.

- Királyság: alapítás, meghívás, rangok (Király, Trónörökös, Herceg, Lovag, Tag, és bármennyi saját rang),
  rangonként 22 állítható jog és foglalási limit, tulajdon átadás, otthon, királysági chat, napló.
- Földek: chunk foglalás (az első néhány ingyen, utána pénzbe kerül), térkép (`/k map`, auto map, auto foglalás),
  a határ részecskékkel látszik, a földet a hatalom és a szint korlátozza.
- Bank és nexuspont (NP): pénz be és ki (a bank pénzneme a Dollár), tárgyak leadása NP-ért (`/k np`), banki napló.
  Foglalás: az első 3 föld ingyen, utána 200 NP és 100 Dollár; más királyságtól legalább 5 chunk távolság kell.
- Nexus: a királyság szíve (jeladó blokk, fölötte "Nexus" felirattal). Alapításkor megkapod, bárhova lerakhatod, és ha
  az a föld még szabad, egyben el is foglalja. A `/k nexus` egyből lerakja eléd. Jobb klikkre innen lehet fejleszteni,
  építményeket és tornyokat venni.
- Fejlesztések: 8 általános, 4 erősítés és 10 bajnok fejlesztés, mind szintekkel.
- Építmények: erőmag, kitermelő, gyógyforrás, bástya, kereskedőház, teleportkapu és Szabályzó (egy földre 1 építmény,
  a Szabályzó és a Nexus nem számít bele). A Szabályzóval földenként állítható: szörnyek, állatok, PvP, tűz.
  Tornyok (földenként 5): íjász, láng, ágyú, mágia, katona, gyógyító, akna és Lézertorony (falon át lő, nagyot sebez,
  drága a lőszere). Lerakás után rájuk kattintva fejleszthetők, tölthetők, ki-be kapcsolhatók, eladhatók (`/k sell`).
- Nexus lopás: ellenséges királyság Nexusát gyémánt kapával ütve elviszed a nexuspontjaik 5%-át (óránként egyszer,
  pajzsos vagy pacifista királyságtól nem).
- Rang jutalmak: `/k fly` (repülés a saját földeken, IceKing és felette), `/k extractor` (minden Kitermelő egyszerre,
  Wizard és felette).
- Kapcsolatok: semleges, szövetséges, fegyverszünet, ellenség (a barátit mindkét félnek el kell fogadnia).
- Nemzetek: több királyság egy nemzetben, közös bank, közös pajzs, főváros.
- Háború: ellenséges földre betörés (`/k invade`), a védő bajnoka és a Nexus őrei harcolnak, győzelem esetén a föld
  átkerül. Pajzs, védelem utáni pajzs, várakozási idő, békés mód, időzített tömeges háború.

Parancsok: `/k` (menü), `/k create <név>`, `/k claim`, `/k unclaim`, `/k map`, `/k invite <játékos>`, `/k home`,
`/k nexus` (`/k nexus open`: a menü bárhonnan), `/k np`, `/k sell`, `/k lore <leírás>`, `/k chat k|a|g` (királysági,
szövetségi, közös), `/k fly`, `/k extractor`, `/k invade`, `/k nation`, `/k help` a teljes listához.
Admin (`icemine.kingdoms.admin`): `/ka reload`, `/ka disband`, `/ka unclaim`, `/ka give`, `/ka rp`, `/ka bank`,
`/ka xp`, `/ka level`, `/ka masswar`, `/ka stopinvasion`, `/ka join`, `/ka setking`, `/ka info`.
Beállítás: `plugins/IceMine/kingdoms.yml` (árak, limitek, hatalom, háború, építmények, tornyok, üzenetek).

A régi királyságok, földek és otthonok az első indításkor maguktól átjönnek az új rendszerbe
(a régi fájl `kingdoms.yml.regi` néven megmarad).

## Első telepítés

1. A szerver mappája legyen üres.
2. Git Clone: https://github.com/ingyen-200-koveto/Szerver.git
3. Indítsd el. Pár másodperc múlva magától leáll (ez az első beállítás).
4. Indítsd el újra.

## Frissítés

A 3.14.3 óta az IceMine magát frissíti: 10 percenként megnézi itt a `version.txt`-t, és ha újabb verzió van,
letölti a `plugins/update` mappába. A szerver következő újraindításakor magától felrakja, Git Pull nem kell hozzá.
Ha van fent admin, szól neki a chatben. Kézzel: `/icem update`. Beállítás: `config.yml` -> `auto-update`
(`restart-when-empty: true` esetén üres szervernél magától újra is indít, ha a panel ezt engedi).

Git Pull csak akkor kell, ha más plugin vagy fájl változik a repóban:

1. Állítsd le a szervert.
2. Git Pull. Ha hibát ad, akkor Hard Reset.
3. Indítsd el.

A repóban lévő fájlokat a szerveren ne töröld és ne írd át, különben a Git Pull hibát ad.
