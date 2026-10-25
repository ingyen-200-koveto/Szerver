# IceMine lobby szerver

Purpur 1.20.6, Java 21.

## Mi van a repóban

- `server.jar`: Purpur 1.20.6 (build 2233)
- `eula.txt`: a Minecraft EULA elfogadása
- `plugins/*.jar`: az IceMine pluginok (IceMineCore, IceMineKingdoms) és a külső pluginok
  (ViaVersion, ViaBackwards, ViaRewind, AuthMe, LuckPerms, WorldEdit, EssentialsX, TAB, Citizens 2.0.35 b3500)
- `kezdo/`: amit csak egyszer kell a helyére tenni (server.properties, config/paper-global.yml,
  AuthMe config és magyar szövegek, ComboFly pálya). Ezt az IceMineCore az első indításkor
  magától a helyére másolja, aztán leállítja a szervert. A második indítástól minden a helyén van.

A repóban csak olyan fájlok vannak, amikhez a szerver futás közben nem nyúl, ezért a Git Pull
mindig működik.

TAB 6.2.1: a Modrinthos univerzális jarból ki vannak véve a Java 25-tel fordított részek
(Fabric, Forge, NeoForge és a 26.2-es Paper modul), mert ezek miatt az 1.20.6-os szerver nem
tudta betölteni. A Bukkit/Paper rész érintetlen.

Citizens 2.0.35 build 3500: ez a build még támogatja az 1.20.6-ot (v1_20_R4). Az újabb buildekből
ez már kikerült, a régebbiekben még nincs benne, ezért ezt ne cseréld le.

## Világok

Ezeket az IceMineCore hozza létre és építi fel magától:

- a lobby (a fő világ), `ice_kitpvp`, `ice_skypvp`, `combofly_map`, gens, `kingdoms`
- minden játékmódnak külön lobby világ: `lobby_kitpvp`, `lobby_skypvp`, `lobby_combofly`,
  `lobby_gens`, `lobby_kingdoms`, `lobby_bedwars` (beállítás: `plugins/IceMineCore/modelobbies.yml`)
- `bedwars`: a Bedwars pályák (Tölgyes 4x2 és Sziklavár 4x1), az IceMineBedwars építi fel

## Bedwars

Saját plugin: `IceMineBedwars` (az IceMineCore kell hozzá). Négy csapat, vas és arany a bázison,
gyémánt a kis szigeteken, smaragd középen, bolt és csapatfejlesztések, magyar szövegek.
Pályák: Tölgyes (füves, fás szigetek, csapatonként 2 fő) és Sziklavár (kővár, solo).
Parancsok: `/bw play`, `/bw join csapat`, `/bw join solo`, `/bw leave`, `/bw stats`.
Adminnak: `/bw start`, `/bw stop`, `/bw rebuild <pálya>`, `/bw reload`.
