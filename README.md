# CS Case (`cscase`)

Counter-Strike style loot cases for NeoForge 1.21.1. Cases and their contents/weights are defined in
datapacks, opened through a CS-like GUI (rarity colours, scrolling roulette, reveal), and are designed to be
handed out as quest rewards.

Package: `com.boomerzap.CSCase` - Mod id: `cscase`

## Using a case

Right-click a case item to preview its contents (with drop chances on hover), then press **Open Case**.
The roulette spins and lands on a result that the server rolled; the reward is given when the animation ends
(or immediately if you log out). `Esc` / **Skip** jumps to the result. If you still hold another of the same
case, **Open Another** appears.

## Defining cases

Put JSON files at `data/<namespace>/cscase/<name>.json`; the case id is `<namespace>:<name>`.
`/reload` picks changes up, and clients are synced automatically. See `data/cscase/cscase/example.json`.

```json
{
  "name": {"text": "Fracture Case", "color": "gold"},
  "rarity_weights": {"mil_spec": 70, "covert": 1},
  "entries": [
    {"item": {"id": "minecraft:iron_ingot", "count": 6}, "rarity": "mil_spec"},
    {"item": {"id": "minecraft:diamond", "count": 2}, "rarity": "restricted"},
    {"item": {"id": "minecraft:nether_star"}, "rarity": "exceedingly_rare", "weight": 0.1}
  ]
}
```

| Field | Meaning |
|-------|---------|
| `name` | Optional text component. Defaults to the prettified file name. |
| `rarity_weights` | Optional per-case override of the tier weights below. |
| `entries[].item` | Item stack: `id`, optional `count`, optional `components` (enchantments, custom names, ...). |
| `entries[].rarity` | `consumer`, `industrial`, `mil_spec` (default), `restricted`, `classified`, `covert`, `exceedingly_rare`. Sets the colour. |
| `entries[].weight` | Optional absolute weight. |

### How weights work

Weights are relative, they do not have to add up to 100.

- An entry **with** `weight` uses exactly that value.
- An entry **without** `weight` gets `tier weight / number of weightless entries in that tier`, so adding more
  items to a tier dilutes that tier's items, not the whole case.
- Default tier weights follow CS odds: consumer 100, industrial 60, mil_spec 79.92, restricted 15.98,
  classified 3.2, covert 0.64, exceedingly_rare 0.26. Override per case with `rarity_weights`.

Use `/cscase info <case>` to see the resulting percentage for every entry.

## Handing cases out (quest rewards)

The case item is `cscase:case` with the component `cscase:case_id`:

```
/give @s cscase:case[cscase:case_id="cscase:example"]
```

Commands (permission level 2, so quest mods that run commands as the server can use them):

| Command | Effect |
|---------|--------|
| `/cscase give <players> <case> [count]` | Give sealed case items. |
| `/cscase open <players> <case>` | Play the opening animation right now; reward on completion. No item needed. |
| `/cscase instant <players> <case>` | Roll and hand over the reward immediately, no GUI. |
| `/cscase list` | List loaded cases. |
| `/cscase info <case>` | Show contents and chances. |

Typical quest setups: reward command `cscase give @p cscase:example`, or a vanilla loot table entry using
`minecraft:set_components` with `{"cscase:case_id": "cscase:example"}`.

## Config (`serverconfig/cscase-server.toml`)

- `openTicks` - animation length in ticks (default 140).
- `announceDrops` / `announceMinRarity` - chat broadcast for rare pulls (default: Covert and above).

## Building

Requires JDK 21. `./gradlew build` produces `build/libs/cscase-<version>.jar`; `./gradlew runClient` launches a dev client.
