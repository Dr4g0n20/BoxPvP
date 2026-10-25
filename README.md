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

Permission: `boxpvp.admin` (all admin commands, default op).
**Creative-mode players ignore all region flags** (build, PvP, drops, etc.) and their blocks never decay,
so admins can always build and repair - switch to survival to test your flags.

---------------------------------------------------------------------------------------
## 3. Set up your map (suggested order)

**Spawn**
1. Stand in the spawn room: `/setspawn` (everyone respawns here, instantly, keeping their inventory).
2. `/region wand`, left-click one corner and right-click the opposite corner of the spawn area (include floor to ceiling).
3. `/region create spawn 10`  (the number is priority; higher wins where regions overlap)
4. `/region flag spawn pvp deny` / `build deny` / `fall-damage deny` / `item-drop deny` (optional)

**Crafting area** - select it, `/region create crafting 10`, then `pvp deny` and `build deny`.

**Disappearing-block area (blocks vanish after 20s)** - select the area, `/region create bridges 5`, then
`/region decay bridges 20`. Any block a survival player places inside it disappears after 20 seconds (no drops) and
whatever it replaced is put back (air, water, grass...). Blocks that were already there **cannot be broken or blown up**
inside the area, so the original build is always safe - only blocks placed by players can be broken. Creative-mode
players can build and break anything and their blocks stay. Leave `build` on `allow`/unset so players can place.
Turn it off with `/region decay bridges off`. Remaining decay blocks are cleared on a clean shutdown.

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
* **Seller** - the NPC you **buy from**. Each item has a green button under it; click it to buy.
* **Buyer** - the NPC you **sell to**. Click the button under a resource to sell 1 (right/shift-click sells all),
  or use **Sell All**.
Prices and items are in `config.yml` under `shop:`.

**Ranks and clans (cosmetic labels, admin-only)**
Chat, the tab list and the name above a player's head all read **`[rank] username [clan]`**.
* `/rank create vip &a[VIP]` makes a rank; `/clan create wolves &b[WOLF]` makes a clan.
* `/rank give <player> <rank>` / `/clan give <player> <clan>` assign them; `take <player>` removes them.
* `setprefix <name> <display>`, `delete <name>`, `list`, `check <player>` work the same for both.
* The display text can be at most 16 characters including `&` colour codes (a Minecraft 1.12 limit on name tags).
* Only `boxpvp.admin` can run these, they give no permissions, and players can't change their own.
* Turn above-head/tab-list tags off with `nametags.enabled: false`; change chat layout with `chat.format`.

**Achievement titles (automatic ranks)**
Cosmetic titles earned from your stats - for example **1,000 ores mined = [Master Grinder], 10,000 = [Elite Grinder],
100,000 = [Ultimate Grinder]**. There are also tracks for kills (Slayer), assists (Wingman), best killstreak (Streaker)
and items sold (Trader); add or change tracks and tiers under `achievements:` in the config.
* A new title is announced, selected automatically, and (from tier 3 up) broadcast to the server.
* `/title` lists yours with progress, `/title <name>` shows one, `/title off` hides it.
* The title shows in chat, above the head and in the tab list in the rank slot - but only for players who have no
  admin-given `/rank`, so staff ranks always win.
* Titles are recalculated from stats, so `/resetstats` (a new season) also resets them.

**Upgrader (pickaxes, swords and axes)**
`/npc create upgrader` spawns a villager. Hold a **pickaxe, sword or axe** in your main hand and right-click it: pay
coins to add enchantments. The menu changes with the item you hold (levels and prices are in the `upgrader:` config):
* **Pickaxe** - Efficiency, Unbreaking, Fortune, Mending
* **Sword** - Sharpness, Fire Aspect, Knockback, Unbreaking, Mending
* **Axe** - Sharpness, Efficiency, Unbreaking, Mending
The item stays in your hand the whole time, so nothing can be lost. Fortune multiplies mine drops (including
auto-smelted ingots). Sharpness, Fire Aspect and Knockback affect PvP balance, so adjust their prices to taste.

**Custom enchants (Enchanter + slots)**
`/npc create enchanter` spawns the Enchanter. Pull enchants into your collection:
* **100 coins** - random tool, random enchant
* **1,000 coins** - you choose pickaxe/sword/axe, the enchant is random
* **10,000 coins** - you choose the exact enchant
Every pull is level 1 and is kept on your account (not as an item). Then, at the **Upgrader -> Custom Enchants**:
* **APPLY** a pulled enchant to the tool in your hand (uses up one copy). Each tool has a limited number of
  **slots** (1 to start; **UNLOCK SLOT** costs coins, up to 4). A tool can't carry the same enchant twice.
* **UPGRADE** an applied enchant with coins (left-click); **right-click** removes it back to your collection at level 1.
The enchants show as lore on the tool (e.g. `Explosion III`). All numbers/prices are under `custom-enchants:`.
* **Pickaxe:** Explosion (cosmetic blast that mines a sphere), Vein Miner, Jackpot, Midas, Momentum, Quick Regen
* **Sword:** Lifesteal, Execute, Frenzy, Streak Surge, Frostbite, Thunderstrike, Hunter's Mark
* **Axe:** Cleave, Shield Breaker, Berserk, Hamstring, Timber
Pickaxe enchants work on blocks in mines. Blocks mined by Explosion/Vein Miner don't count toward the ores stat by default.

**Combat**
* While you're combat-tagged (10s after PvP damage) a countdown shows above your hotbar.
* You can't walk (or launch) into a no-PvP region while tagged: you're knocked back and told
  "Cannot enter safezone while in combat!". `/spawn` and pearls into safe zones are blocked too.
* **Kill assists:** anyone who dealt at least 2 hearts to the victim in the last 15 seconds (and didn't land the kill)
  gets an assist (shown in `/stats`, `/top assists`) and 5 coins. Tune it under `assists:` in the config.

**Coins**
* `/pay <player> <amount>` sends coins to an online player (3s cooldown, every payment is logged to the console).
* `/resetstats <player> [stats|coins|all]` resets one player. `/resetstats all [stats|coins|all] confirm` wipes everyone.
  `stats` = kills, deaths, streaks, assists; `coins` = the balance.

**Exploit protection**
* Combat logging: quitting within 10s of PvP counts as a death for you and a kill for your opponent.
* Ender pearls: 10s cooldown, and you can't pearl (or chorus-fruit) from a PvP area into a no-PvP region - the pearl is refunded.
* Hunger: **off everywhere by default** - nobody loses food. Turn it on for a region with `/region flag <name> hunger allow`, or everywhere with `default-flags.hunger: true`.

**Launch pads**
1. Place a **gold block** (type is configurable). Stand on it and run `/launchpad create <name>`.
2. Walk to where players should land and run `/launchpad setlanding <name>`.
3. Walking onto the gold block seats the player on an invisible armour stand that the server glides smoothly
   to the landing point (gentle start and stop, no fall damage), then puts them exactly on the landing spot.
   If a rider presses sneak to hop off, they are put straight back on, so there's no drifting, overshooting or missing the target.
4. By default the flight is a **straight line**. If a wall or roof is in the way, add height with
   `/launchpad arc <name> <blocks>` (e.g. `/launchpad arc <name> 6` rises 6 blocks in the middle;
   `/launchpad arc <name> default` resets it). `launchpad.default-arc` in the config sets the default.
5. Riders are immune to damage during the flight and for 4 seconds after landing, and the immunity ends
   instantly if they attack someone (so it can't be used to camp). Set `build deny` on the pad's region so
   nobody breaks the gold block (breaking it disables the pad).
6. If a flight ever gets stuck on a block, the plugin gives up after about a third of a second and places the
   player at the landing point anyway.

---------------------------------------------------------------------------------------
## 4. Player commands
`/stats [player]` - `/top [kills|deaths|assists|ores|streak|coins|kdr]` - `/spawn` (blocked for 10s after PvP damage)

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
