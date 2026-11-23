# IceMine lobby szerver

Purpur 1.20.6, Java 21.

## Mi van a repóban

- `server.jar`: Purpur 1.20.6 (build 2233)
- `eula.txt`: a Minecraft EULA elfogadása
- `plugins/*.jar`: az IceMine plugin (`IceMine.jar`, benne minden saját rendszer és játékmód) és a külső pluginok
  (ViaVersion, ViaBackwards, ViaRewind, AuthMe, LuckPerms, WorldEdit, EssentialsX, TAB, Citizens 2.0.35 b3500,
  GrimAC 2.3.75 Bukkit build, LibertyBans 1.1.4 Bukkit/Paper build)
- `forras/`: az IceMine plugin forráskódja (a szerver nem használja)
- `proxy/`: a Velocity proxyhoz tartozó IceMineProxy plugin és a beállításai (lásd lent)
- `kezdo/`: amit csak egyszer kell a helyére tenni (server.properties, config/paper-global.yml,
  AuthMe config és magyar szövegek, ComboFly pálya). Ezt az IceMine az első indításkor
  magától a helyére másolja, aztán leállítja a szervert. A második indítástól minden a helyén van.

Frissítéshez nem kell Git Pull (lásd lent: Frissítés), az IceMine magától letölti az új jarokat.

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

**Játékmód választó** (iránytű a hotbaron): OneBlock (fűblokk), Gens (gyémánt kapa),
Duels (netherit kard), BedWars (piros ágy), Kingdoms (aranyblokk), ComboFly és Creative.

A kozmetikák (nyomvonalak, ölés effektek) a 3.46.0-tól nincsenek, a menüből és a rangokból is kikerültek.

### Chat napló

Minden chat üzenet (a királysági és szövetségi is), parancs (a `/msg` is), be- és kilépés naponta egy fájlba kerül:
`plugins/IceMine/chatlog/ÉÉÉÉ-HH-NN.log`. A jelszavas parancsoknál (`/login`, `/register`...) csillag van a jelszó helyén.
Játékban (OP): `/icem chatlog` (legfrissebb 15 sor), `/icem chatlog <játékos>`, `/icem chatlog [játékos] <oldal>`.
A 30 napnál régebbi naplókat magától törli (`config.yml` -> `chatlog`).

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
Minden a `plugins/IceMine/ranks.yml`-ben van: mit kap a rang az IceMine-ban (pénz szorzó, jogok) és
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

### OneBlock (régi nevén Fyland)

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

### Creative

Telkes építés kreatív módban. A világot a plugin rajzolja (végtelen telekrács, 48x48-as telkek, köztük utak), nem kell
felépíteni. Telek: alapból 1, Bronz ranggal (és felette) 2, ennél több senkinek (`creative.yml` -> `limits`).

- Központ: a telkek között, a középső telken (szökőkút, utak, fák, lámpák). Ide érkezik mindenki, itt lehet telket kérni
  (kattints a "Telket kérek" pontra) vagy a telkeidet megnyitni. `/plot hub` visz vissza, `/plot admin hub` újraépíti.
- A főlobbyban a Creative szobra a régi "Hamarosan" szobor helyén áll.

- Csak a saját telkeden (vagy ahova felvettek) építhetsz, az út és a többiek telke védett.
- Barátok felvétele (`/plot trust`), kitiltás (`/plot deny`), leküldés (`/plot kick`), név (`/plot name`).
- `/plot clear`: a telek kiürítése, `/plot delete`: a telek feladása (mindkettőt kétszer kell beírni).
- Robbanás, tűz, mobok magától nem születnek, a víz és a láva nem folyik ki a telekről. Telkenként legfeljebb 40 entitás.
  Tiltott tárgyak (TNT, parancsblokk, spawner, wither...): `creative.yml` -> `blocked-items`. Enderláda itt nem használható.
- Saját táska: amit a Creative-ban elővesz, az nem jön át a többi módba.

Parancsok: `/plot` (menü, vagy `/p`, `/telek`), `/plot auto`, `/plot claim`, `/plot home [szám]`, `/plot visit <játékos>`,
`/plot info`, `/plot list`, `/plot help`. Admin (`icemine.admin.creative`, bárhol építhet): `/plot admin clear|delete`.
Korlátlan telek: `icemine.creative.unlimited`.

WorldEdit a telkeken: aki már legalább 2 órát játszott a szerveren, a Creative-ban megkapja a biztonságos WorldEdit
parancsokat (`//wand`, `//set`, `//replace`, `//walls`, `//copy`, `//paste`, `//rotate`, `//undo`, `//sphere`...).
Csak a saját (vagy oda felvett) telkén hat, a bedrockot nem bántja, egyszerre legfeljebb 20 000 blokkot cserél,
a gömb sugara legfeljebb 20. A laggos parancsok (`//regen`, `//smooth`, ecsetek, schematic...) nem használhatók.
Beállítás: `creative.yml` -> `worldedit`.

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

## Belépés a weboldalas jelszóval

A játékosok a szerveren is a weboldalas jelszavukkal lépnek be (`/login <jelszó>`). A jelszót a weboldal
ellenőrzi (`/api/mc/login`), és ha jó, az AuthMe beléptet, a szerveren tárolt jelszó pedig a weboldaléra
változik. Regisztrálni csak a weboldalon lehet, ugyanazzal a névvel, mint a játékban. Ha a weboldalon
megváltozik a jelszó, a következő belépéstől az új jelszó jó. Bedrockon a név előtti pontot nem kell beírni a
weboldalon. Akinek még nincs weboldalas fiókja, de régen a szerveren regisztrált, a régi jelszavával még
beléphet. Ha a weboldal nem elérhető, a szerveren tárolt jelszó működik. 8 rossz jelszó után az adott
névvel 15 percig nem lehet próbálkozni. Beállítás: `config.yml` -> `weboldal-belepes` (a cím és a kulcs az
`icecoin` résznél van; ha a weboldal `.env` fájljában van `MC_API_KEY`, ugyanaz kell az `icecoin.api-key`-be).

A kulcs a `plugins/IceMine/kulcs.txt` fájlba is mehet (egy sor, csak a kulcs). Ha a `config.yml`-ben van, a
plugin induláskor ide is elmenti, és ha a `config.yml`-ből valamiért eltűnik, innen olvassa. Ez a fájl nincs a
repóban. Ha a `config.yml` hibás (rossz YAML), a plugin nem írja felül, csak szól.

## Weboldalon vett rangok

Ha valaki a weboldalon rangot vesz (vagy a szerencsekeréken nyer), a szerver legfeljebb fél percen belül
megadja neki a hozzá tartozó LuckPerms csoportot (Bronz, Wizard, IceKing, Yeti), és szól neki. Ha a rang
lejár, le is veszi. Csak azt veszi le, amit ő adott: a kézzel adott rangokhoz (`lp user ... parent add ...`) nem
nyúl. Beállítás: `config.yml` -> `weboldal-rangok` (melyik weboldalas rang melyik csoport). Hogy kinek mit adott,
a `plugins/IceMine/weboldal-rangok.yml` fájlban látszik.


## Weboldalas skin

Amit a játékos a weboldalon a Karakter oldalon feltölt skinnek, az a szerveren magától rákerül, eredeti és
nem eredeti (tört) klienssel is. Ehhez a **SkinsRestorer** kell (`plugins/SkinsRestorer.jar`, 15.12.6, a
Modrinthos univerzális jar, ez a Paper/Purpur szerveren fut). Az IceMine az IceCoin lekérdezéssel együtt
(30 másodpercenként) megnézi, kinek van skinje a weboldalon, és ha új vagy megváltozott, a SkinsRestorerrel
ráteszi. A képet a SkinsRestorer a MineSkinnel hitelesíti, ezért kell, hogy a weboldal (`icecoin.url`)
kívülről elérhető legyen. Ha a játékos a weboldalon visszaállítja az alap skint, a szerveren is lekerül.
Akinek nincs weboldalas skinje, annál az IceMine nem nyúl semmihez, a `/skin` parancs ugyanúgy működik.
Bedrock játékosoknál (ponttal kezdődő név) a saját Bedrock skinjük marad.
A SkinsRestorer csak ezen a szerveren van, a proxyn nincs. Velocity mögött magától „proxy módba” kapcsolna, és akkor
egy skint sem tenne fel, ezért az IceMine induláskor a `plugins/SkinsRestorer/config.yml`-ben a
`proxyMode.detection` értékét `DISABLED`-re írja (`config.yml` → `skins.backend-only: true`). Ha a proxyra is
felkerül a SkinsRestorer, ezt írd `false`-ra. A skinhez az IceCoin kulcs (`icecoin.api-key`) is kell.

## Csendes indítás

Indításkor a konzolon csak a figyelmeztetések, a hibák, a Paper „Done” sora és a végén egy összefoglaló látszik
(„✔ A szerver elindult. Indításkor N sort nem írtam ki…”). A többi plugin betöltési üzenetei is rejtve maradnak.
A `logs/latest.log` fájlba ilyenkor is minden bekerül, ha valamit keresni kell. Indítás után minden üzenet újra
látszik a konzolon. Kikapcsolás: `config.yml` → `quiet-startup: false`.
A csend a Paper „Done” sora után még 15 másodpercig tart, mert a LuckPerms beállítás és a pályaépítés ekkor fut.
A LuckPerms parancsok válaszai (`[LP] ...`) már nem kerülnek a konzolra, a BedWars és ComboFly pályaépítés
üzenetei pedig csak a logban vannak (FINE szinten).

## GrimAC és LibertyBans

- **GrimAC** (csalásvédelem, `plugins/GrimAC.jar`, Bukkit build): a staff a `grim.alerts` joggal kapja a
  riasztásokat (Jn.Moderátortól felfelé), a `/grim alerts` parancs ki-be kapcsolja. Ha a ComboFly lökése
  miatt téves riasztás jönne, írd meg, mert a Grimben modulonként állítható.
- **LibertyBans** (kitiltás, némítás, kirúgás, figyelmeztetés, `plugins/LibertyBans.jar`, Bukkit/Paper build):
  az első indításkor letölti a saját könyvtárait, ehhez internet kell. Magyar nyelven fut (`lang-file: 'hu'`).
  A `/ban`, `/tempban`, `/mute`, `/tempmute`, `/kick`, `/unban`, `/unbanip` parancsot az Essentials is lefoglalja,
  ezért ezeket az IceMine átirányítja a LibertyBansra, és kiveszi a LibertyBans configjából, hogy ne panaszkodjon
  minden indításkor (ez a második indítástól érvényes). A többi (`/warn`, `/unmute`, `/history`, `/banlist`...)
  eleve a LibertyBansé. Példa: `/ban Jatekos 7d csalás`, `/mute Jatekos 2h káromkodás`.
  Jogok: Jn.Moderátor némít, kirúg, figyelmeztet; Moderátor kitilt; Sr.Moderátortól felfelé minden.
  Ezeket az IceMine egyszer beírja a LuckPermsbe (a már kész csoportokba is).
- Az IceMine percenként elküldi a weboldalnak az aktív kitiltásokat és némításokat, így a weboldal
  Kitiltáscsökkentés oldalán mindenki látja a sajátját (lejárattal). Ehhez a `MC_API_KEY` kell, ugyanaz, mint a
  rangoknál.

## Velocity kulcs

A proxy `forwarding.secret` fájljának tartalma a szerveren a `config/paper-global.yml`
fájlba kell (`proxies.velocity.secret`). A GitHubon lévő `kezdo/config/paper-global.yml`-be soha ne írd bele.

## Lobby: tér, dupla ugrás, karácsony

- **Tér** (lobby 8-as elrendezés): nagyobb tér, a játékmód szobrok körben a tér
  szélén, köztük virágágyások. A tér közepén hópehely minta van (a 12-es elrendezéstől nincs szökőkút), a spawn előtte. Két ugratópad a téren le a faluba, kettő a faluból
  fel a térre. Az új elrendezésnél a lobby egyszer újraépül (az NPC-k és hologramok a helyükre kerülnek).
- **Nagyobb sziget** (lobby 9-es elrendezés): a sziget kb. másfélszer olyan széles, két új szárnnyal.
  Nyugaton a **Jégtó** stéggel, horgászkunyhóval, náddal és tavirózsákkal. Keleten a **kilátó** (22 blokk magas,
  létrával, fent korláttal) és a **PvP aréna**. A falu főutcájáról erdei ösvények vezetnek mindkét szárnyba.
- **Kidolgozottabb vár** (lobby 11-es elrendezés): a falakon pillérek, lőrések, alul terméskő lábazat, fent
  párkány és lámpások. A kapun csúcsív, csapórács és kiemelt kaputorony címerrel, a hídon lámpák, az árokban
  tavirózsák. A tornyokon kőövek és kék-fehér zászlók, a lakótorony nyeregtetőt kapott kéménnyel, kék
  üvegablakokat, lépcsős bejáratot szőnyeggel és erkélyt. Az udvaron keresztút, lámpaoszlopok, három piaci stand,
  szénabálák, hordók és virágok a fák körül.
- **Láthatatlan fal** (barrier) a sziget széle körül, a felszíntől 50 blokk magasig: nem lehet leugrani vagy
  kimászni. A parkour kijáratánál (a spawn mögött) rés van, a parkour ugyanott indul, mint eddig.
- **PvP aréna**: aki belép, felszerelést kap (vaskard, íj, vaspáncél, aranyalma), és csak az arénában lévők
  üthetik egymást. Aki kiesne, kirepül a kapu elé, és visszakapja a lobby tárgyait (nincs halál, nincs
  tárgyvesztés). Kilépés: sétálj ki. A kiütések száma: `lobby.pvp_kills` statisztika.
- **Dupla ugrás**: ugrás közben még egy szóköz előrelök és felemel (parkour közben és VIP repülésnél nincs).
  `lobby.yml` -> `double-jump` (enabled, power, height).
- **IceMine felirat** (lobby 13-as elrendezés): közelebb került a spawnhoz, a vár elé (z = -47), hogy
  jobban látszódjon. A Játékmódok iránytű neve kiemelt (fénylik, saját leírással).
- **Saját módosítások a lobbyban**: ha adminként kreatívban építesz vagy bontasz a lobbyban (vagy WorldEdittel
  módosítasz), az IceMine ezt megjegyzi (`lobby.yml` -> `keep-build: true`), és a frissítései többé nem építik újra
  a lobbyt, így a módosításod megmarad. Az új IceMine lobbyhoz: `/icemine rebuild lobby confirm` (ekkor elvész).
- **Karácsony**: december 1. és január 6. között nagy karácsonyfa nő a tér közepén csillaggal és díszekkel,
  a házak gerendáin színes fényfüzér világít, havazik, és felirat van a fa fölött. Utána magától visszaáll.
  `lobby.yml` -> `christmas: auto` (vagy `on` / `off`).

## Party követés

Ha a party vezetője belép egy játékmódba (vagy annak a lobbyjába), a szabad tagok automatikusan vele mennek.
Szabad az, aki a lobbyban vagy egy mód lobbyjában van, és nincs meccsben, harcban. A vezető kikapcsolhatja:
`/party kovetes` (be/ki).

## Kihívás pályaválasztással (Duels és ComboFly)

`/duel <játékos>` -> menü: mód, utána **pálya** (Erdő, Sivatag, Jég, Nether vagy Véletlen), és megy a kihívás.
A ComboFly kihívás saját **ComboFly kupolákban** megy (Jég, Nether, Sivatag, Erdő): a két játékos a kupola két
végén, középen kezd, egymással szemben. A ComboFlyban (és a ComboFly lobbyjában) a `/duel <játékos>` egyből a pályaválasztót nyitja ComboFly módra,
és a meccs után mindketten visszakerülnek a ComboFlyba. Ha a választott pályán nincs szabad aréna, egy másikon indul.
A `duels.yml` -> `built` most a pálya témáját is megjegyzi: ha egy aréna nem a megfelelő témával épült, újraépül
(a frissítés utáni első indításkor egyszer az összes Duels aréna újraépül).

### Saját pálya hozzáadása a pályaválasztóhoz

1. Menj a Duels világba (op): `/execute in minecraft:duels run tp @s 0 120 3000` (messze a gyári arénáktól), és
   kreatívban építsd meg a pályát.
2. `/duels palya uj <azonosító> [típus]` - típus: `combofly` (ComboFly pálya), `classic` (Classic, NoDebuff, OP, Axe, Sword...) vagy `sumo`.
3. Állj az egyik kezdőpontra, nézz az ellenfél felé: `/duels palya piros`. A másikra: `/duels palya kek`.
4. A pálya alsó sarkába: `/duels palya sarok1`, az átellenes felső sarkába: `/duels palya sarok2`
   (ez a doboz a pálya határa; ha valaki az alja alá esik, kiesett).
5. Opcionális: `/duels palya nev &6Arany vár`, `/duels palya ikon` (a kezedben lévő tárgy lesz az ikon).
6. `/duels palya kesz` - azonnal megjelenik a pályaválasztóban. Lista: `/duels palya lista`, törlés: `/duels palya torol <id>`.
Saját pálya építős módokhoz (BuildUHC, Bridge) nem használható, mert azokat a meccs után újra kell építeni.
A pályák a `duels.yml` -> `custom-arenas` részben vannak.

## Duels: edzés bot ellen

`/duels bot` (vagy a Duels menüben a zombifej) - gépi ellenfél egy szabad arénában, három szinten:
- **Könnyű**: 10 szív, lassú, egyenesen jön.
- **Közepes**: 13 szív, átlagos, oldalra kitér.
- **Nehéz**: 16 szív, gyors, kitér és ugrálva (kritikusan) üt.
A bot ugyanazt a páncélt és fegyvert kapja, mint te. `/duels bot nehez nodebuff` - másik mód (csak sima PvP módok).
Kilépés: `/duels kilep`. Legyőzések: `duels.bot.<szint>` statisztika (XP és Kristály nem jár érte, hogy ne lehessen farmolni).

## OneBlock: közös sziget

`/fy meghiv <név>` -> a másik a `/fy elfogad` (vagy a chatben az [ELFOGADOM]) paranccsal csatlakozik, és ha a lobbyban
van, egyből átkerül a szigetre. `/fy tagok`, `/fy kirug`, `/fy atad`, `/fy kilep`.

## Szintek, rejtett ajándékok, hóvihar

- **Szintek**: minden játékból XP jár (győzelem, ölés, ágytörés, kibányászott blokk, Gens aratás, parkour, hópehely...).
  A szint a név előtt látszik a chatben, a TAB-ban és a fej fölött (`[12✦]`, a színe a szinttel változik). Szintlépéskor
  Kristály jár (szint × 10, minden 10. szintnél +250, és mindenki látja). `/szint [játékos]` mutatja a haladást.
  Toplista: `szint`. Beállítás: `config.yml` -> `levels` (XP szabályok, jutalmak, ki/be).
- **Rejtett ajándékok**: 10 ajándékdoboz-fej van elrejtve a lobbyban (házakban, a várban, a kilátó tetején, a tónál...).
  Kattints rá: 25 Kristály és 20 XP darabonként, mind a 10-ért 1000 Kristály. A helyek a `lobby.yml` -> `secret-heads.places`
  részben vannak, átírhatók.
- **Hóvihar**: 40 percenként 3 percre hóvihar jön a lobbyban, varázs hópelyhek lebegnek a föld fölött (csak
  szilárd talaj fölött, kerítésen, lámpán nem). Odasétálsz, felveszed, 5 Kristály és 2 XP. A vihar végén a fel nem
  szedett pelyhek eltűnnek. A végén kiírja, ki gyűjtötte a legtöbbet. Kézzel: `/icem admin hovihar`.
  Beállítás: `lobby.yml` -> `snowstorm`.
- **Játékmód prefix**: játékmódban az üzenetek előtt a mód neve áll (pl. `BedWars • Ehhez nincs jogod`),
  a lobbyban marad az `[IceMine]`. Kikapcsolás: `messages_hu.yml` -> `mode-prefix: false`.

## SkyWars

Szigetekről indultok, a ládákból szerelkeztek fel, és az utolsó túlélő (Duóban az utolsó csapat) nyer.

- Belépés: a lobbyban a SkyWars szobor vagy a játékmód választó (iránytű) a SkyWars saját lobbyjába visz
  (`lobby_skywars`). Ott a portál vagy a Játék hologram nyitja meg a mód választót (Solo vagy Duó). `/sw` is ezt nyitja.
  Meccs után is ide kerülsz vissza. `/sw play solo`, `/sw leave`, `/sw nez <játékos>`, `/sw stats`, `/sw top`.
- A pályákat a plugin építi a saját világában (`ice_skywars`): 8 sziget (kb. 15 blokk széles) fával, kőkupaccal
  és 3 ládával, középen nagyobb (kb. 23 blokk)
  sziget jobb ládákkal (gyémánt, varázsolt kard, gyöngy, aranyalma). Minden meccs után újraépül.
- Menet: várakozás a ketrecben, visszaszámlálás (30 mp, tele pályánál 10 mp), a ládák a 3. és a 6. percben
  újratöltődnek, a 9. perctől mindenki világít (végső harc), 12 perc után döntetlen.
- Jutalom: ölésért 10, győzelemért 50 IceCoin (a rang szorzójával). Statisztika a profilban és két toplista.
- Party: a vezető lép be, a tagok vele jönnek; Duóban egy szigetre kerülnek.
- Beállítás: `plugins/IceMine/skywars.yml` (módok, pályák száma, témák: jég, erdő, sivatag, jutalmak).
  Admin: `/sw list`, `/sw start|stop|rebuild <aréna>` (`icemine.admin.skywars`).


ComboFlyban (a pályán és a ComboFly lobbyban) a `/duels` nem használható, párbajra hívni `/duel <név>` paranccsal lehet.
A ComboFlyból indított párbaj után a ComboFly pálya spawnjára kerülsz vissza (ahonnan leugrasz), nem a lobbyba.

SkyWarsban a ketrecben (várakozás, visszaszámlálás) nem lehet az inventoryban a tárgyakat arrébb tenni.

**Ketrecek**: a meccs elején mindenki a saját választott ketrecében vár. Az alap ketrec mindenkinek megvan, a többi 17-et
feladatokkal lehet feloldani: Jégkocka (20 meccs), Hóvihar (30 ölés), Smaragd (10 győzelem), Tökfej (50 meccs), Zselé
(60 ölés), Arany (100 ölés), Óceán (25 győzelem), TNT (150 ölés), Láva (200 ölés), Ametiszt (40 győzelem), Nether
(150 meccs), End (60 győzelem), Szivárvány (5 győzelem egymás után), Redstone (8 győzelem egymás után), Gyémánt
(100 győzelem), Obszidián (400 ölés), Jégkirály (200 győzelem). Választás: a ketrecben várakozva a hotbar közepén lévő
Ketrec választóval (a ketrec azonnal átépül), a SkyWars lobby hotbarján vagy a SkyWars menüben. A menü mutatja a
haladást. Jog: `icemine.skywars.cage.<id>` egy ketrechez, `icemine.skywars.cage.all` mindhez.

## Barátok és party

- `/barat <játékos>`: barátkérés (5 percig érvényes, kattintással elfogadható). `/barat lista`: ki van fent és
  hol, a többiek mikor voltak fent utoljára. `/barat kerelmek`, `/barat torol <játékos>`. Ha egy barátod fellép
  vagy kilép, szól. Legfeljebb 50 barát, az adatbázisban tárolva (`im_friends`).
- `/party <játékos>`: meghívás (60 mp). Legfeljebb 8 fő. `/party lista`, `/party warp` (a tagok a vezetőhöz
  mennek), `/pc <üzenet>` party chat, `/party kirug`, `/party atad`, `/party feloszlat`, `/party kilep`.
- BedWarsba a vezető lép be (NPC, menü vagy `/bw play`), a fent lévő, épp nem játszó tagok vele mennek
  ugyanarra a pályára, és ha a mód engedi (duo, trio, squad), egy csapatba kerülnek. Tagként egyedül nem lehet
  belépni, amíg partyban vagy.

## Napi mentés

Minden nap 4:00-kor (magyar idő) az IceMine elmenti az összes világot és a pluginok adatait (LuckPerms, AuthMe,
LibertyBans, IceMine adatbázis, configok; a jar fájlok nélkül) egy zip fájlba:

- itt a szerveren: `plugins/IceMine/mentesek/` (az utolsó 3 marad),
- és feltölti a weboldal szerverére (VPS): `/var/lib/icemine/mentesek/` (az utolsó 7 marad). Így akkor is megvan,
  ha a szolgáltatónál történik baj.

Azonnali mentés: `/mentes most` (konzolból is), állapot: `/mentes`. Beállítás: `config.yml` → `mentes`.
Ha a mentés vagy a feltöltés nem sikerül, a ⚠️・szerver-hibák csatornába jön üzenet.

**Visszaállítás:** állítsd le a szervert, a fájlkezelőben töltsd fel és csomagold ki a zip-et a szerver
főmappájába (felülírva a régit), majd indítsd el.

## Büntetések és csalásgyanú a Discordon

- Minden LibertyBans kitiltás, némítás, kirúgás, figyelmeztetés és feloldás kártyaként megjelenik a
  **🔨・büntetések** csatornában (ki, kit, miért, meddig).
- Ha a GrimAC valakit 5 percen belül 20-szor jelez, kártya megy a **🕵️・csalásgyanú** csatornába az
  ellenőrzésekkel, „Megnézem” gombbal. Ugyanarról a játékosról 10 percenként legfeljebb egy.
  Beállítás: `config.yml` → `csalasgyanu` (küszöb, idő, kihagyott ellenőrzések).
- Mindkét csatornát a bot hozza létre az első kártyánál, vagy: `/beallitas buntetesek`, `/beallitas csalasgyanu`.

## Hibafigyelő (Discord)

A lobby és a proxy minden figyelmeztetése és hibája (bármelyik pluginé) a weboldalon keresztül a Discordra kerül, a
staff **⚠️・szerver-hibák** csatornába (ha még nincs, a bot az első hibánál létrehozza a jelentések csatorna
mellett, vagy: `/beallitas szerverhibak`). Ugyanaz a hiba 30 percenként legfeljebb egyszer jön, a többit csak
számolja („🔁 5× ugyanez”). A szokásos, ártalmatlan figyelmeztetések (offline mód, root, Vault...) ki vannak hagyva.

- Lobby: `plugins/IceMine/config.yml` → `hiba-figyelo` (név, csak hibák, saját kihagyási lista). Az
  `icecoin.url` és `icecoin.api-key` beállítást használja, külön nem kell semmi.
- Proxy: `plugins/icemineproxy/hibak.properties` → `kulcs=` ide ugyanazt kell írni, mint a weboldal
  `MC_API_KEY`-e (a lobbyn az `icecoin.api-key`). Utána proxy újraindítás.

## /jelent és /segitseg

- `/jelent <játékos> <indok>`: a jelentés a weboldalon át a Discord 🚨・jelentések
  csatornájába kerül. Benne van, ki jelentett kit, melyik játékmódban (szerveren), és miért. Játékosonként
  percenként egyszer lehet.
- `/segitseg <üzenet>`: a Discord 🆘・segítségkérések csatornájába megy, és pingeli a
  staff rangot. Játékosonként 5 percenként egyszer lehet.
- Aki játékban is látni akarja ezeket, annak `icemine.mod.reports` jog kell (benne van az `icemine.mod.*`-ban).
- A küldéshez a `config.yml` `icecoin.url` és `icecoin.api-key` beállítása kell, és a weboldalon is be kell
  legyen állítva az `MC_API_KEY`, ugyanazzal az értékkel. Discordon a csatornákat a `/beallitas reportok`
  és `/beallitas adminhivasok` paranccsal adod meg.

## Lobby parkour

A start kapu mögötti nyomólapra lépve indul. A pálya 56 színes gyapjú kockából áll, de mindig csak a
következő kocka látszik: akkor jelenik meg, amikor ráugrottál az előzőre. Minden 14. kocka zöld, az
ellenőrzőpont, ha leesel, onnan folytatod. Az utolsó kocka aranyblokk, az a cél. Egyszerre többen is
futhatnak, mindenki a saját kockáit látja előtűnni. A 3.17.0-val a lobby egyszer magától újraépül
(a régi parkour helyére ez kerül), és a parkour rekordlista újraindul. Beállítás: `parkour.yml`.

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

Különleges tárgyak a boltban (Hasznos cuccok):
- **Hídtojás** (1 smaragd): eldobva egyenes, 3 széles gyapjúhidat húz maga után a csapatod színében.
- **Felpattanó torony** (24 vas): rakd le a ládát, és pár pillanat alatt felhúz egy tornyot létrával, fent lőréssel.
- **Őrző vasgólem** (120 vas): 4 percig védi a szigetet, és megtámadja a közelbe jövő ellenséget. A neve fölött látszik, meddig marad.
- **Ezüstmoly** (24 vas): hógolyó, ahova becsapódik, ott kikel egy ezüstmoly, ami 15 mp-ig harcol érted.
- **Mentőplatform** (1 smaragd): jobb klikkre nyálkaplatform jön alád, ha leesnél. 10 mp után eltűnik, 15 mp-enként használható.
- **Varázstej** (4 arany): megiszod, és 30 mp-ig nem kapnak el a csapdák.
Meccs közben a játékosok neve előtt a csapat színű ⚑ zászló áll (fejük fölött és a TAB-ban is), a nevük is ilyen színű, a bőrpáncéljuk a csapat színében van.
A gólem és az ezüstmoly által okozott ölés a gazdájáé. Admin: `/bw give <játékos> <tárgy>` (meccsben lévő játékosnak).

Pályák (4-es elrendezés): a szigetek alja mélyebb és csipkés, a peremük körül alacsonyabb párkány van. A csapat szigeteken
a generátor fölött tető áll a csapat színével, a boltoknál lámpások, az ösvény mellett lámpaoszlopok, a szabad helyeken
kövek, bokrok, hókupacok (a pálya témájától függően). A gyémánt szigeteken négy lámpás oszlop, a közép nagyobb és díszesebb.
Az új elrendezésnél a pályák egyszer újraépülnek az indításkor (pályánként pár másodperc).

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


**Eladó ládák IceCoinért**: `/k eladolada` vagy a királyság menüben az Eladó ládák gomb. Sima (590 IC), 1,5x (990 IC),
2x (1450 IC) és 5x (2100 IC) szorzós láda: ennyiszer annyit fizet az eladott tárgyakért. Az árak a `kingdoms.yml`-ben
(`sellchest-shop`). Az IceCoint a weboldal vonja le (`/api/mc/ic-koltes`, ehhez kell az `icecoin.api-key`), a láda csak
sikeres levonás után kerül a játékoshoz. Admin: `/market give <játékos> sellchest_15|sellchest_2|sellchest_5`.

A Kingdomsban mindenki túlélő módban van (az adminok is, ha kalandor módban jönnének), így a vadonban és a saját
földjeiken ki tudják ütni a blokkokat.
## Proxy (Velocity) és Bedrock

A Bedrock játékosok a proxyn lévő Geyser és Floodgate pluginon keresztül jönnek be. Ezeket és a beállításaikat
az **IceMineProxy** plugin tartja rendben, így a proxyn sem kell kézzel semmit frissíteni:

- minden induláskor beírja a `proxy/beallitasok.txt` sorait a pluginok configjába (most: a Geyser a proxy
  portját használja, NetherNet kapcsolattal, külső signalinggal, mert az SRKHost csak egy portot ad),
- 30 percenként megnézi, van-e újabb Geyser és Floodgate (Velocity build), és ha igen, letölti. A proxy
  következő újraindításakor kerül a helyére,
- telepíti és frissíti a **Sonar** botvédelmet (Velocity build a Modrinthról). Velocity 3.6 vagy újabb alatt a
  legújabbat, régebbi Velocity alatt a 2.1.49-et, mert az újabbak már 3.6-ot kérnek. A Bedrock játékosokat a
  Sonar nem ellenőrzi (`check-geyser-players: false`),
- beállítja a szerverlista szövegét (`velocity.toml` → `motd`, a `beallitasok.txt` utolsó sora) és az ikont
  (`proxy/server-icon.png`). Ha a proxyn kézzel másik ikont tettél be, azt nem írja felül,
- magát is frissíti innen (`proxy/version.txt`).

Telepítés (csak egyszer): a proxy `plugins` mappájában Letöltés URL-ről:
`https://raw.githubusercontent.com/ingyen-200-koveto/Szerver/main/proxy/IceMineProxy.jar`
Utána indítsd újra a proxyt, várj 2 percet, és indítsd újra még egyszer.

Bedrockon a csatlakozási címet a Geyser írja ki a proxy konzoljára indításkor
("Players can join at ..."). Forráskód: `proxy/forras`.

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

A 3.42.0 óta a többi plugint is így frissíti: ha a `plugins` mappában valamelyik jar megváltozik vagy új jar
kerül ide, letölti, és újraindításkor felkerül (`auto-update.plugins`). Ami a GitHubon nincs fent, ahhoz nem nyúl.

**A panel Git Pull gombját ne használd.** Az SRKHoston a Git Pull törli mindazt, ami nincs a repóban: a világokat,
a pluginok adatait és a `config.yml`-t is. Újraindítás után a világok üresen generálódnak újra.
