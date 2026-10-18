# IceMine lobby szerver

Purpur 1.20.6, Java 21. Ebben a repóban van minden, amit az IceMine-hoz én csináltam:

- `plugins/IceMineCore-*.jar`: lobby, játékmódok (SkyPvP, KitPvP, ComboFly, Gens), scoreboard, pénz, statisztika
- `plugins/IceMineKingdoms-*.jar`: Kingdoms mód
- `plugins/AuthMe/config.yml` és `plugins/AuthMe/messages/messages_hu.yml`: magyar AuthMe
- `combofly_map/`: a ComboFly pálya
- `server.properties`: port 25574, offline mód a Velocity miatt
- `config/paper-global.yml`: a Velocity kapcsolat a kulccsal
- `eula.txt`: a Minecraft EULA elfogadása

Ami nincs benne, és külön kell feltenni:

- `server.jar` (Purpur 1.20.6)
- a külső pluginok: ViaVersion, ViaBackwards, ViaRewind, AuthMe, LuckPerms, TAB, WorldEdit, EssentialsX, Citizens

## Frissítés az SRKHost panelen

1. Állítsd le a szervert.
2. Git Pull.
3. Indítsd el.

A régi jart a pull magától törli, az újat beteszi. A configokhoz, a világokhoz és a többi pluginhoz nem nyúl.
