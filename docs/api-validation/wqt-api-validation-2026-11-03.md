# WQT API diagnostics follow-up

## Scope

2026-11-03. Based on main `4354cbae` and WQT's `single-pass-2026-11-02.zip`.
WQT's recorded Brain hash matches `fb05bb5a` with CRLF line endings, but its
catalog contains newer commands. The complete checkout and dependency versions
are unknown; the supplied archive is not a verified latest-main benchmark.

No Brain scheduling changes, fixed system prompt changes, task-specific planning,
automatic tool selection, dependency updates, commits or pushes were made.

## Changes

- Mineflayer 4.39.0 / prismarine-item 1.18.0 can pass an enchantment component
  object where prismarine-block expects an array. A bot-local compatibility
  adapter normalizes that known shape for dig timing, including helmet Aqua
  Affinity. Ordinary arrays still use upstream digTime. Tool, water, ground,
  effects and creative rules remain unchanged. End restores the original method.
- `moveslot` rejects output destinations before picking up the source, and
  directs output extraction to `slotclick`. `slotclick` rejects placing an
  incompatible cursor into output slots; taking onto a compatible cursor stack
  remains allowed. These checks do not allocate a target slot or plan a recipe.
- `inventory` and `inventory slots` report a nonempty cursor separately, omit it
  when empty, and keep its contents out of the 36-slot free-space count.
- `inventory slots` includes `slotLayout`; `window` identifies its take-only
  result slot when present. Catalog descriptions explain the same mapping.
- `look block` on loaded air returns `TARGET_IS_AIR`, not a generic unsupported
  shape error. Placement aims at existing support; no automatic support search
  or view adjustment was added.

## Stable slot contract

| Reference | Meaning |
| --- | --- |
| `inventory:0` | Player 2x2 crafting output, take only |
| `inventory:1..4` | Player crafting inputs |
| `inventory:5..8` | Head, torso, legs, feet |
| `inventory:9..35` | Backpack |
| `inventory:36..44` | Hotbar, unchanged with any container open |
| `inventory:45` | Off-hand |
| `window:n` | Current container/workstation slot only |
| `hotbar 0..8` | Selection index, corresponding to inventory 36..44; not a slot reference |

Example: `moveslot inventory:36 window:0 2` moves two items from the first
hotbar slot to a chest's first slot. The same destination in a furnace is its
input, not a player slot. Output locations depend on window type and are
reported by `window`. Native server NBT indices are a separate representation;
they are not exposed as replacement API indices.

## Automated checks

Final full suite: **592/592**, no failures, skips or cancellations.
New checks cover player/crafting/furnace output destination rejection without
pickup; explicit output extraction; compatible cursor extraction; cursor and
layout feedback; air aiming; component enchantment timing including water,
helmet, creative and cleanup. Existing open-window mapping tests remain passing.

## Real Minecraft and DeepSeek

Isolated local vanilla Java **1.21.11**, one actual Mineflayer player, no Fabric,
BlueMap or GUI client. Private DPAPI key was supplied through the process
environment only. Production Brain planner/proposal APIs and command handlers
were used with the complete default command catalog, without tool narrowing.

Each scenario used a new Brain, the unchanged production system prompt and the
same public task in both runs. At most 12 calls for glass/cursor, 16 for
chest/furnace, 180 seconds per scenario, 60 seconds per model request. API
descriptions were clarified between runs; this is NOT a controlled A/B study.

| Scenario | Run 1 calls | Run 2 calls | Environment result |
| --- | --- | --- | --- |
| Enchanted pickaxe breaks specified glass | 5 | 2 | Both reached air; no enchantment exception |
| Cursor sticks back into first hotbar slot | 3 | 2 | Both restored five sticks and cleared cursor |
| Chest: transfer 2, retain 3 in first hotbar | 7 | 10 | Both correct; first used moveslot, second used four slotclick operations |
| Furnace: input 1 ore, retain 1 in first hotbar | 6 | 7 | Both correct with fixed inventory:36 mapping |

**8/8 scenario attempts reached the checked item/block state, 42 model requests**
to the official DeepSeek endpoint, requested model `deepseek-flash`, 197,860
reported total tokens. This small diagnostic is not a general success-rate claim.
The external harness stops after observing the target state; autonomous task
completion/stopping is NOT measured. It does not fix the old loop or output
protocol rejection issues from WQT's run.

Per run, direct-handler checks also verified invalid inventory:0 destination
leaves the source/cursor unchanged, cursor appears/disappears in inventory,
air targeting errors, and real Silk Touch item decoding and digging. Chest and
furnace retained/source counts were additionally checked with server-console
`data get entity ... Inventory` and `data get block ... Items`.

Raw local-only evidence (ignored by Git):

- `runs/wqt-api-20261103/results-first.json`
- `runs/wqt-api-20261103/results.json`
- `runs/wqt-api-20261103/server.log`
- `runs/wqt-api-20261103-tests.tap`

Both runs saved and stopped the server. No personal client, existing test world,
or private config was changed. A pre-existing end-listener threshold warning
still appeared at startup; it is not evidence of a growing listener leak and
was not suppressed. Real water/Aqua Affinity, all workstation result types,
low TPS and cross-version behavior remain outside this live coverage.
