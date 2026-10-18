# IceMine lobby szerver

Ebben a repóban az IceMine saját pluginjai vannak, a `plugins` mappa szerkezetében.

- `plugins/IceMineCore-*.jar`: lobby, játékmódok (SkyPvP, KitPvP, ComboFly, Gens), scoreboard, pénz, statisztika
- `plugins/IceMineKingdoms-*.jar`: Kingdoms mód

## Frissítés az SRKHost panelen

1. Állítsd le a szervert.
2. Git Pull.
3. Indítsd el.

A régi jart a pull magától törli, az újat beteszi. A configok, a világok és a többi plugin nincsenek a repóban, ezekhez a pull nem nyúl.

A `config/paper-global.yml` (Velocity kulcs) szándékosan nincs itt, mert a repó nyilvános.
