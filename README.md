# IceMine lobby szerver

Purpur 1.20.6, Java 21. Ebben a repóban van minden, amit az IceMine-hoz én csináltam:

- `plugins/IceMineCore-*.jar`: lobby, játékmódok (SkyPvP, KitPvP, ComboFly, Gens), scoreboard, pénz, statisztika
- `plugins/IceMineKingdoms-*.jar`: Kingdoms mód
- `plugins/AuthMe/config.yml` és `plugins/AuthMe/messages/messages_hu.yml`: magyar AuthMe
- `combofly_map/`: a ComboFly pálya
- `server.properties`: port 25574, offline mód a Velocity miatt
- `config/paper-global.yml`: a Velocity kapcsolat a kulccsal
- `eula.txt`: a Minecraft EULA elfogadása

Külső pluginok, amik szintén benne vannak (a `plugins` mappában):

- ViaVersion 5.12.1, ViaBackwards 5.12.1, ViaRewind 4.2.0
- AuthMe 5.6.0, LuckPerms 5.5.87 (Bukkit), WorldEdit 7.3.7 (Bukkit), EssentialsX 2.22.0
- TAB 6.2.1: a Modrinthos univerzális jarból ki vannak véve a Java 25-tel fordított részek (Fabric, Forge, NeoForge és a 26.2-es Paper modul), mert ezek miatt az 1.20.6-os szerver nem tudta betölteni. A Bukkit/Paper rész érintetlen.

Ami nincs benne, és külön kell feltenni:

- `server.jar` (Purpur 1.20.6)
- Citizens (opcionális, élő NPC-khez): olyan build kell, ami támogatja az 1.20.6-ot

## Frissítés az SRKHost panelen

1. Állítsd le a szervert.
2. Git Pull.
3. Indítsd el.

A régi jart a pull magától törli, az újat beteszi. A configokhoz, a világokhoz és a többi pluginhoz nem nyúl.
