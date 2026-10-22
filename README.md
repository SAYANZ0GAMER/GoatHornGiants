# GoatHornGiants

Play the **Call** goat horn and, 4 seconds later, every plain, un-named zombie in your chunk is struck by
harmless lightning and replaced by a Giant that hunts every player except you, until you hit it.

## Supported versions
* **Paper 26.3** (compiled against `paper-api 26.3.build.178-stable`, `api-version: 26.3`), Java 25. **Not yet build-verified or runtime-tested** — see below.
* Purpur and other Paper forks should work. **Spigot / CraftBukkit / Folia are not supported**: the plugin needs
  Paper's `Pathfinder` API and `EntityAddToWorldEvent`, and refuses to enable without them.

## Build
Requires JDK 25 and Maven 3.9+.
```
mvn clean package
```
The jar is `target/GoatHornGiants-1.0.0.jar`.

## Install
1. Stop the server, copy the jar into `plugins/`, start the server.
2. `plugins/GoatHornGiants/config.yml` is generated. Edit it and run `/goathorngiants reload`.

## Commands / permissions
| Item | Purpose | Default |
|---|---|---|
| `/goathorngiants reload` (alias `/hgiants`) | reload config | op (`goathorngiants.admin`) |
| `goathorngiants.use` | may trigger the ability with the Call horn | everyone |

## Configuration
See `config.yml` (delay = 80 ticks, damage, reach, cooldown, detection range, speed, persistence, ...).

## How the Call horn is detected
`PlayerInteractEvent` (right-click air/block) -> `HornUtil.isCallGoatHorn(item)`:
the item must be `Material.GOAT_HORN`, its meta must be a `MusicInstrumentMeta`, and
`getInstrument().getKey()` must equal `minecraft:call_goat_horn`. Comparing the registry key (not an enum or
the item's name/lore) is intended to work on 26.3 and cannot be spoofed by renaming. Ponder, Sing, Seek, Feel, Admire,
Yearn, Dream, custom instruments and horns with no instrument are all ignored. Clicks that would not play the
horn (item use denied by another plugin, interacting with a chest/door without sneaking, horn on cooldown)
are ignored too.

## How Giants know their creator
Each Giant stores in its Persistent Data Container (saved with the entity, survives restarts and chunk unloads):
* `creator`: the summoner's UUID (a UUID, never a Player object, so a disconnect cannot break anything),
* `unprotected`: a byte, `0` at spawn.

`GiantManager.isProtected(giant, player)` is true only if the player is that Giant's creator **and** `unprotected == 0`.
It is applied in three places: the AI target search skips protected players; `EntityTargetLivingEntityEvent` and
`EntityDamageByEntityEvent` (Giant -> Player) are cancelled for protected players as a safety net.
When the creator damages the Giant (`EntityDamageByEntityEvent`, MONITOR, only if not cancelled and final damage > 0;
optionally also their projectiles) the flag for **that one entity** is set to `1`. It is never reset, and other
Giants by the same player keep their own flag. Each Giant has its own creator, so different players' Giants are independent.

## Giant AI (limitation + solution)
A vanilla Giant has no AI goals, so it will not move or attack by itself. Instead of faking it with another mob,
the real Giant entity is kept and one central repeating task (every 5 ticks, not one task per Giant) does:
nearest eligible player in range -> `setTarget` -> `Pathfinder.moveTo` (straight-line velocity fallback if no
path exists) -> melee `player.damage(amount, giant)` with a cooldown when within reach. It switches to a nearer
player automatically. Like all mobs, Giants only act while their chunk is loaded.

Other deliberate choices:
* Only `EntityType.ZOMBIE` is transformed (not husks, drowned, zombie villagers, zombified piglins).
* Lightning is `World#strikeLightningEffect`: sound and visuals only, no damage, no fire.
* Creative/spectator players are ignored by default (they cannot be hurt); toggle in config.
* Suffocation damage is cancelled for these Giants (they are 12 blocks tall and would die in any cave); toggle in config.
* Batching: all zombies transform exactly 80 ticks after the horn; if more than `batch-size` (8) are queued the
  remainder spill into the next ticks to avoid a lag spike.
* The `damage` value replaces the vanilla Giant's very high 50 attack damage.

## Testing checklist
1. [ ] `/give @s goat_horn[instrument="minecraft:call_goat_horn"]`, stand near zombies, play it: after ~4 s lightning + Giants appear.
2. [ ] Play Ponder/Sing/Seek/Feel/Admire/Yearn/Dream horns: nothing happens.
3. [ ] Zombies in a neighbouring chunk (F3+G shows borders) are untouched.
4. [ ] Name-tagged zombie in your chunk: no lightning, still a zombie, name intact.
5. [ ] Husk / drowned / zombie villager in the chunk: untouched.
6. [ ] Kill a selected zombie within the 4 s: no Giant, no error in console.
7. [ ] Name-tag a selected zombie within the 4 s: it is skipped.
8. [ ] Play the horn twice quickly: no zombie becomes two Giants.
9. [ ] Lightning: no fire, no damage to you, items on the ground, or nearby mobs.
10. [ ] Giant spawns at the zombie's position, facing the same way.
11. [ ] Giant chases and hits a second player (not the creator); creator standing next to it is ignored.
12. [ ] With two players, the Giant goes for the nearest eligible one and switches when that changes.
13. [ ] Creator hits Giant A: chat warning, A now attacks the creator. Giant B (same creator) still ignores them.
14. [ ] Creator leaves the area / A loses its target: protection does NOT come back.
15. [ ] Player Y summons their own Giant: it ignores Y, attacks the first player.
16. [ ] Creator disconnects and rejoins: no errors, still protected (or unprotected if they hit it earlier); same after a server restart.
17. [ ] Giant inside a cave does not die from suffocation.
18. [ ] 30+ zombies in a chunk: no noticeable lag spike.
