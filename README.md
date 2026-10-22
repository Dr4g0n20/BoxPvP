# BoxPvP (Spigot 1.12.2)

A box-PvP gamemode plugin: spawn area, drop-in launch pads, regenerating auto-smelt mines,
enchanting area with falling XP bottles, seller/buyer NPCs, coins, killstreaks, stats and leaderboards.
Works on Spigot / Paper / most forks of **1.12.2**. Everything is saved in the plugin's folder.

---------------------------------------------------------------------------------------
## 1. Build the jar  (you only need to do this once)

I couldn't compile the jar for you (my sandbox has no internet or Java compiler), so build it yourself.
Pick ONE of these:

### Option A - GitHub (no installs)
1. Make a free account at github.com and create a new empty repository.
2. Upload everything in this folder (keep the folder structure; the hidden `.github` folder matters).
3. Open the **Actions** tab, wait for "Build BoxPvP" to go green (about 1-2 minutes), open the run,
   and download the **BoxPvP-jar** artifact. Unzip it - inside is `BoxPvP.jar`.

### Option B - On your own PC
1. Install a JDK (17 or 21 is fine - the jar is built to run on Java 8+): https://adoptium.net
2. Install Apache Maven: https://maven.apache.org/download.cgi
3. In this folder run:  `mvn clean package`
4. Your plugin is at `target/BoxPvP.jar`

If Maven can't download `bungeecord-chat`, tell me the error and I'll adjust the pom.

---------------------------------------------------------------------------------------
## 2. Install
1. Upload `BoxPvP.jar` into the server's **plugins** folder (drag and drop in the file manager on the host).
2. Restart the server. A `plugins/BoxPvP/` folder with `config.yml` appears.
3. Make yourself operator (or give the permission `boxpvp.admin`).

Permissions: `boxpvp.admin` (all admin commands, default op) and `boxpvp.bypass` (build/break anywhere
while in **creative**, default op).

---------------------------------------------------------------------------------------
## 3. Set up your map (suggested order)

**Spawn**
1. Stand in the spawn room: `/setspawn` (everyone respawns here, instantly, keeping their inventory).
2. `/region wand`, left-click one corner and right-click the opposite corner of the spawn area (include floor to ceiling).
3. `/region create spawn 10`  (the number is priority; higher wins where regions overlap)
4. `/region flag spawn pvp deny` / `build deny` / `fall-damage deny` / `item-drop deny` (optional)

**Crafting area** - select it, `/region create crafting 10`, then `pvp deny` and `build deny`.

**Disappearing-block area (blocks vanish after 20s)** - select the area, `/region create parkour 5`, then
`/region decay parkour 20`. Any block a player places inside it disappears after 20 seconds (no drops). Blocks
that were already there are never touched. Leave `build` on `allow`/unset so players can place. Turn it off with
`/region decay parkour off`. Water/lava from buckets is not tracked. Remaining decay blocks are cleared on a clean shutdown.

**Enchanting area** - select it from floor to the **air layer just under the ceiling**
(XP bottles spawn at the top layer of the region), `/region create enchanting 10`, then
`/region flag enchanting build deny` and `/region flag enchanting xp-rain allow`.
Place your enchanting tables normally; `build deny` stops people breaking them. PvP stays on there.

**Arena** - nothing needed: with no region, the defaults in `config.yml` apply (pvp on, build on).
(To be explicit, make an `arena` region with priority 1 and set `pvp allow`, `build allow`.)

Flags: `pvp`, `build`, `fall-damage`, `mob-spawn`, `item-drop`, `xp-rain`  - each `allow`, `deny` or `none`.
Use `/region info <name>`, `/region list`, `/region delete <name>`, `/region priority <name> <n>`, `/region decay <name> <seconds|off>`.

**Mines (auto-smelting, regenerating)**
1. Select the area with the wand, then `/mine create <name>` then `/mine fill <name>`.
2. Mined blocks drop already-smelted (iron ore -> iron ingot, gold ore -> gold ingot...), turn into
   bedrock, then regenerate a few seconds later as a random block from the mine's block list.
3. Tune with `/mine block <name> <MATERIAL> <weight>` (weight 0 removes), `/mine regen <name> <seconds>`.
   Ores need the right pickaxe (stone for iron, iron for gold/diamond/redstone/emerald) - toggle in config.
4. Only blocks inside a mine are affected. Mine blocks cannot be placed over or blown up.

**Shop NPCs**
Stand where you want them: `/npc create seller` and `/npc create buyer` (two separate villagers that
don't move or take damage). `/npc remove` deletes the nearest one. Right-click an NPC to open its menu:
* **Seller** - click an item to sell 1, shift-click to sell all, or use the **Sell All** button.
* **Buyer** - click to buy. Prices and items are in `config.yml` under `shop:`.

**Launch pads**
1. Place a **gold block** (type is configurable). Stand on it and run `/launchpad create <name>`.
2. Walk to where players should land and run `/launchpad setlanding <name>`.
3. Walking onto the gold block now flies the player along an arc to the landing spot with no fall damage.
   Optional: `/launchpad power <name> <0.1-5>` raises/lowers the arc.
4. Launched players are immune to damage while flying and for 4 seconds after landing, and the immunity
   ends instantly if they attack someone (so it can't be used to camp). Set `build deny` on the
   pad's region so nobody breaks the gold block (breaking it disables the pad).

---------------------------------------------------------------------------------------
## 4. Player commands
`/stats [player]` - `/top [kills|deaths|streak|coins|kdr]` - `/spawn` (blocked for 10s after PvP damage)

## Admin commands
`/setspawn` `/region` `/mine` `/npc` `/launchpad` `/setcoins <player> <amount>` `/boxpvp reload`

## Config highlights (`plugins/BoxPvP/config.yml`, then `/boxpvp reload`)
* Coins per kill, streak bonus, anti-farm timer, death penalty
* Killstreak rewards (coins, items, console commands, broadcasts) at any streak numbers
* Scoreboard title/lines, XP-rain speed, launch-pad physics, mine blocks / smelting table
* The whole shop: sell prices and buy items (including potions)

## Notes
* Mined blocks are bedrock while regenerating. They're restored on a clean shutdown; after a crash,
  run `/mine fill <name>` to reset a mine.
* Data files: `stats.yml`, `regions.yml`, `mines.yml`, `launchpads.yml`, `data.yml` (spawn).

---------------------------------------------------------------------------------------
## Flat layout note
In this version every file sits in one folder (no `src/main/java/...` folders), which makes uploading to
GitHub easy. The only file in a subfolder is `.github/workflows/build.yml`, which you create by hand on
GitHub (Add file > Create new file, name it `.github/workflows/build.yml`, paste the text from the chat).
