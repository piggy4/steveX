# Typed Slot References

Use the typed slot references below for command arguments.

| Region | References |
| --- | --- |
| Backpack, hotbar | inventory:1..27, hotbar:1..9 |
| Equipment | equipment:head, equipment:torso, equipment:legs, equipment:feet, equipment:offhand |
| Personal crafting | crafting:1..4, crafting:result |
| Workbench | workbench:1..9, workbench:result |
| Storage | chest:1..27/54, barrel:1..27, hopper:1..5, shulker:1..27; storage_9x3:1..27 and other layout names when the source is unknown |
| Furnace family | furnace:input, furnace:fuel, furnace:output |
| Enchanting, anvil | enchanting:item, enchanting:lapis; anvil:left, anvil:right, anvil:result |
| Stonecutter, grindstone | stonecutter:input, stonecutter:result; grindstone:top, grindstone:bottom, grindstone:result |
| Smithing, cartography | smithing:template/base/addition/result; cartography:map/material/result |
| Loom, brewing | loom:banner/dye/pattern/result; brewing:bottle1/bottle2/bottle3/ingredient/fuel |
| Beacon, crafter, merchant | beacon:payment; crafter:1..9, crafter:preview; merchant:input1/input2/result |

Copy refs from `inventory slots` or `window`. Player refs remain stable
when a container opens. Container refs address only the current interface;
they do not open one. The crafter preview is read-only. Output slots are
take-only. Cursor contents are separate from slots.

```text
hotbar 1
slotinfo hotbar:1
moveslot hotbar:1 chest:1 2
transfer withdraw chest:1 2
furnace input inventory:3 4
furnace take furnace:output
slotclick workbench:result
```

`hotbar 1..9` is the keyboard selection index. Recipe, trade and choice
indices and item counts are not slots and retain their numeric meanings.

The Minecraft 1.21.11 live smoke checks used a disposable loopback server.
Backpack and hotbar endpoints stayed stable across the crafter, chest and
furnace; transfers were checked against server block data. A stonecutter
option discovered from the server recipe list was selected by index and
the output slot was verified. No paid model was called.

## Workstation choices and item details

`window` reports loom choices in synchronized banner-tag order as
`{index, pattern, color}`, directly usable with `station select <index>`.
`null` means choices could not be resolved; `[]` means no usable choices.
`slotinfo` and Web item tooltips expose potion type and effects, banner
layers, map ID and packet-known scale/lock, and armor trim identifiers.
Pending map post-processing is distinct from applied state. Potion durations
are in ticks in API results and shown in seconds in Web. The vanilla potion
defaults are scoped to 1.21.11. Inventory summary does not include these details.

Live 1.21.11 follow-up: verified red cross and red circle banner results
against loom choices, Speed II (1800 ticks), gold/sentry trim, unsynced map
ID and an actual newly initialized map with scale 0 and locked=false.
Ordinary DeepSeek requests selected the red circle using the reported index
and deposited 2 from `hotbar:1` into an already open chest, retaining 3;
both tasks reached the checked state in two calls each. Server NBT verified
the exact transfer. An earlier attempt that included opening the chest timed
out with an obstructed target and a missing navigation context in the harness;
it does not count as a passing open-to-transfer task. The visible Edge smoke
used the production inventory renderer and live snapshot in an isolated
preview page; this was not a full dashboard navigation test.

## Window identity and final-result acceptance

Brain decisions for window mutations are checked against the actual window
instance at request and execution, and against the last successful Brain
`window` observation. Reusing the same window type or protocol ID is not
sufficient. Loom and stonecutter selection also compares the observed inputs.
After a stale rejection, inspect the current window before issuing a new action.
Manual commands without a Brain observation still refer to the live window.

`window` and the successful opening response include `openedFrom`: the block
name, integer coordinates, and dimension recorded when the native opening
completes, or `null` when no block source is known. This identifies the block
used to open the window, not the current crosshair target or both halves of a
double chest. The record is bound to that window instance, not its protocol ID.

Live follow-up: a same-type chest swap was rejected before mutation; taking
loom and stonecutter output verified final items and consumed inputs. An
unobstructed, full SteveX Agent run with the official DeepSeek API opened a
chest, inspected it, and moved two sticks in six requests. Server NBT
confirmed two in the chest and three in the first hotbar slot.

An insufficient destination rejects the entire quantity before clicking. On a
failed or cancelled slot operation, `observedEffect` distinguishes `unchanged`
(no changes observed in the confirmed snapshot), `changed` (inspect the slot
and cursor deltas before retrying), and `unknown` (no confirmed final snapshot).
Closing a window mid-action can yield `unknown` even if the server applied a
click. Reopen and inspect before retrying.
