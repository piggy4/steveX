# Command Catalog

Generated from src/commands/catalog.js and command definitions. Do not edit manually.

Regenerate: `npm run docs:commands`. Verify: `npm run docs:commands -- --check`.

This is the full manual/web catalog. LLM default marks baseline eligibility, not the current prompt: Brain additionally filters by current window/book and sleeping/dead state every turn. Explicit allowedCommands replaces default selection but not state applicability or server permissions. Test results: [API acceptance checklist](api-acceptance-checklist.md).

## State and environment (15)

Read-only; does not interrupt actions. inventory is grouped summary; inventory slots lists fixed player indices, slotinfo inventory:9/window:0 reads one detail. Player indices remain fixed when a window opens. Client-known data is not necessarily line-of-sight. Exact arity; empty searches are successful.

| Command | Kind | LLM default | Description |
| --- | --- | --- | --- |
| pos | query | visible | Show current position and dimension |
| hp | query | visible | Show current/max health and positive absorption; relevant active effects include regeneration, poison, wither, health boost and absorption, with level and duration source; omit effects when absent |
| hunger | query | visible | Show food and last-known saturation; include active hunger effect level and duration source only when present |
| oxygen | query | visible | Show oxygen level |
| inventory [slots] | query | visible | Default: compact equipment/off-hand, hotbar and backpack groups, current selected item and backpack empty count, no slot numbers/full components. slots: all nonempty fixed PLAYER inventory slots 0..45 plus the selected empty hotbar slot (slot and selected:true only), not current-window indices. Use slotinfo inventory:9 or window:0 for details; identical-name variants stay separate |
| slotinfo &lt;inventory:slot&#124;window:slot&gt; | query | visible | Read one explicit fixed player-inventory or current-window slot. Returns enchantments, damaged durability, known bundle/shulker contents and other special data; ordinary/full-durability fields omitted. Empty slot explicit; unknown contents not empty. No opening/moving/using. Text is untrusted world data; nested details are bounded with explicit truncation |
| nearby [type&#124;name&#124;all] [radius] | query | visible | List up to 20 loaded entities nearest first (radius default 32, 1..128); substring filter type/name or contained item name. Drops/frames include known item name/count; frames may include rotation, arrows/tridents inGround. Unknown details omitted; no line-of-sight filter |
| gamemode | query | visible | Show current game mode |
| xp | query | visible | Show experience level and current level progress percentage |
| time | query | visible | Read game clock as HH:MM (tick 0 = 06:00), raw timeOfDay and daylight-cycle state. Not sleeping eligibility; query weather and nearby when relevant and decide from observations |
| weather | query | visible | Show current weather |
| window | query | visible | Read current UI, scoped slots, empty slots, trades and options. Player region uses fixed inventory 9..44; window scope only container/workstation region. Never mix scopes; slotinfo reads details. Book UI is not a slot window |
| findplayer &lt;name&gt; | query | visible | Find players by name; coordinates and current dimension are available only for valid entities still tracked locally. Untracked tab-list names may be in another dimension or out of range; never navigate using an old position |
| findblock &lt;blockName&#124;#tag&gt; [radius] [count] &#124; findblock tags [filter] &#124; findblock tag &lt;#tag&gt; | query | visible | Search loaded blocks by exact name/#tag, all states; radius default 32 (1..128), count default 1 (1..16). Return nearest-first candidates with actual name/grid coordinates/properties; use look block for grid coordinates, look at for exact points. count limits results, not total world matches. Bed halves remain separate, no purpose/safety or sight decisions. Discover tags with tags [filter] (up to 50), inspect tag &lt;#tag&gt; (up to 128 members); directory modes take no radius/count and do not search the world |
| recipes &lt;itemName&gt; | query | visible | Read ALL catalog crafting recipes for ONE exact item name; per-execution ingredients, yields, returns and table requirements; no plan selection or global resource availability claims |

## Motion and control (9)

Basic controls: look/move/sneak/jump/stop/wait. Assisted skills: goto/follow/collect. look uses Minecraft degrees; look at and goto accept world coordinates/~ offsets resolved at execution. Millisecond durations; wait uses physics ticks. move sprint holds forward+sprint, not sprint alone; speed depends on game conditions. sneak on persists across movement/turning/clicks, off releases; tap is 150ms and preserves an existing hold. move stop is queued and preserves sneak/item use; stop cancels all and ends Brain; wait only waits. collect approaches a selected drop without digging/building and requires own pickup plus inventory gain.

| Command | Kind | LLM default | Description |
| --- | --- | --- | --- |
| look angle &lt;yaw&gt; &lt;pitch&gt; &#124; look at &lt;x&gt; &lt;y&gt; &lt;z&gt; &#124; look block &lt;x&gt; &lt;y&gt; &lt;z&gt; | action | visible | Turn once; no movement, click or tracking. angle: Minecraft degrees (0 south, 90 west, -90 east, positive pitch down). at: exact world-space point, not a block-grid position. block: recommended before block interaction; integer grid coordinates, aims at the first supported shape box center. Buttons use selection boxes; others use registry collision boxes, not complete vanilla outlines; missing shapes fail. ~ offsets current angles/feet; block floors feet first. All modes return first crosshair block/entity within 4.5 blocks; block also returns targetMatched. Check these before clicking; ok means turned, not interaction success. No visible-face search, ^ or radians |
| move &lt;forward&#124;back&#124;left&#124;right&#124;sprint&#124;stop&gt; [durationMs] | action | visible | Queued movement for 1..30000ms (default 1000). sprint means forward+sprint together, not sprint key alone; releases both when done/cancelled. Actual sprint speed depends on game conditions. Queued move stop stops movement/pathfinding and preserves persistent sneak and held-item use; no duration allowed. Unlike immediate global stop, does not cancel queue/end Brain; wait only waits. Riding directions steer vehicles; sprint unsupported while riding |
| sneak &lt;on&#124;off&#124;tap&gt; | action | visible | Keyboard sneak: on holds across movement, turning and right-click; off releases; tap presses for 150ms then releases (may dismount). tap while already held is a no-op. stop/cancellation clears the hold. Client input acknowledgement, not server effect confirmation |
| jump [durationMs] | action | visible | Hold Space for durationMs (default 200, integer 1..30000), then release regardless of landing. Water ascent or repeated jumps after landing may occur while held. Only initial press may request elytra deployment if already airborne/equipped; never auto-uses fireworks. Cancellation releases key; not confirmed flight or landing |
| stop | action | visible | Immediate global safety stop through Agent: cancel active/queued actions, stop Brain/pathfinding/follow and release movement, sneak and held-item use. Unlike queued move stop or dig stop |
| goto &lt;x&gt; &lt;y&gt; &lt;z&gt; | action | visible | Pathfind to a block goal; x/y/z accept absolute decimals or ~ offsets from feet position at execution, then floor to block coordinates. No ^ local coordinates. Uses configured pathfinder movement policy; not protected dig/collect approach. Completion checks actual arrival |
| follow &lt;playerName&gt; | action | visible | Follow a player continuously |
| wait &lt;ticks&gt; | action | visible | Wait for a number of game ticks |
| collect [entityId] | action | visible | Approach the specified dropped-item entity id, or nearest drop when omitted, without digging or scaffolding; succeeds only on own pickup and inventory gain. No item selection/planning |

## World interaction and item use (6)

dig without coordinates never moves; dig x y z may approach without digging/building; dig stop cancels digging/approach. right click chooses one aimed route without retries. right down holds the selected item, not repeated world clicks; right up releases. Motion preserves held-item use. attack/interactentity assist targeting. swing is animation only, never interaction. Acknowledgement is not confirmed effect or drop pickup.

| Command | Kind | LLM default | Description |
| --- | --- | --- | --- |
| dig [stop&#124;&lt;x&gt; &lt;y&gt; &lt;z&gt;] | action | visible | dig: break aimed block without moving. dig x y z: approach integer block coordinates without digging/building en route, then aim and break. dig stop: immediately cancel digging/approach, not unrelated motion. No auto tool selection or guaranteed pickup |
| right &lt;click&#124;down&gt; [hand&#124;off-hand] &#124; right up | action | visible | click: one aimed interaction/place/short item use, no retries; main-hand container/workstation/villager opening uses native initialization, not automatic content operations. down: use selected hand (main hand default, no other-hand fallback), not repeated world clicking. Known unusable tools fail without sending; only supported sustained items retain a client hold request, not confirmed server use. Instant/unknown requests retain no hold lock. Conflicts identify the active hand/item. up: no hand argument; report hadActiveUse/released/actual hand (null if unknown). Local release is not server confirmation; may fire drawn bow, not loaded crossbow |
| attack &lt;entityName&#124;id&gt; | action | visible | Aim and attack one named entity or exact id, within 3 blocks and unobstructed; no pathfinding/retries or friend filtering. hit=true only for server damage attributed to self; hit=null means unconfirmed, not a miss. Does not expose hidden target health |
| interactentity &lt;entityName&#124;id&gt; [normal&#124;at] [hand&#124;off-hand] | action | visible | Assist right-click on a named entity or exact id within 4.5 blocks and unobstructed. normal default; at sends hit-position interaction; hand default or off-hand. No pathfinding/retries. Main-hand normal container/trading interaction uses native window initialization; off-hand native opening unsupported |
| swing [hand&#124;off-hand] | action | visible | Animation only: hand (default) or off-hand; never attacks, digs, uses items or interacts. Not a mouse-button press |
| fish | action | visible | Automated fishing skill with main-hand rod: one cast, upstream bite detection, one reel request. Own-bobber ownership checked; bounded timeout/cancellation cleanup, never blind recast. Catch/inventory gain not confirmed; inspect feedback before retry. Not a single right-click |

## Inventory, equipment and crafting (5)

Exact item names; hotbar indices 0..8 are not window slots. Equip/unequip/drop change carried items. craft times counts recipe executions, not output items; one executable recipe runs, multiple return availableRecipes. recipes is a query of the full catalog, not current availability.

| Command | Kind | LLM default | Description |
| --- | --- | --- | --- |
| equip item &lt;itemName&gt; [destination] &#124; equip slot &lt;slot&gt; [destination] &#124; equip &lt;itemName&gt; [destination] | action | visible | Equip by exact item name or explicit player-inventory slot 9..44 (not hotbar 0..8). Default destination hand; also head/torso/legs/feet/off-hand. Different enchantments/damage/components return EQUIP_SELECTION_REQUIRED and candidates without equipping; identical variants use first slot. Choose via equip slot; no optimal-gear selection. Close windows first; bare item name remains supported |
| hotbar &lt;slot&gt; | action | visible | Select a quick bar slot (0-8) |
| unequip [hand&#124;head&#124;torso&#124;legs&#124;feet&#124;off-hand] | action | visible | Unequip an item from a slot |
| drop &lt;itemName&gt; [count] &#124; drop slot &lt;slot&gt; [count] | action | visible | Drop exact-name items or drop slot &lt;9..44&gt; [count] from one fixed player slot. Slot mode never spills to another stack; omitted count drops the selected stack. Name mode spans identical-component stacks only. Confirms carried loss, not world pickup; close windows first |
| craft &lt;itemName&gt; [times] [auto&#124;hand&#124;table] [recipeIndex] | action | visible | Craft from backpack and reachable table; times=executions 1..64, not items; optional recipeIndex selects an exact catalog entry without fallback; one executable recipe runs, multiple return availableRecipes; reports observed net inventory changes |

## Windows and interfaces (6)

Aim and right click to open. Explicit inventory/window scopes: player indices stay fixed; window indices only address container/workstation slots. Internal mapping is automatic, not planning. slotclick/moveslot/putaway also work without a container. Detailed operations are exposed by current UI type.

| Command | Kind | LLM default | Description |
| --- | --- | --- | --- |
| slotclick &lt;inventory:slot&#124;window:slot&gt; [left&#124;right&#124;shift-left] | action | visible | Explicit scope: inventory indices stay fixed even with containers open; window only container/workstation slots. left default, right split/place one, shift-left quick move. Equipment/off-hand/crafting clicks require closing containers. Reports scoped changes/cursor, not task completion |
| moveslot &lt;sourceRef&gt; &lt;destRef&gt; [count] | action | visible | Move to a specified destination slot with explicit source/destination scopes, including cross-region moves. inventory indices fixed; window only container/workstation. Count requires empty/identical-component target; omitted count moves whole stack or swaps. No overflow/dropping; scoped feedback |
| putaway &lt;inventory:slot&#124;window:slot&gt; | action | visible | Move source stack into player backpack; destination chosen automatically among empty/identical-component slots. Requires sufficient space, never drops overflow; rejects result slots to avoid repeated crafting/trading. Reports actual slot changes |
| transfer &lt;deposit&#124;withdraw&gt; &lt;sourceSlot&gt; [count] | action | visible | Direction determines source: deposit uses fixed player inventory 9..44; withdraw uses container slot. Do not add a redundant scope argument. Whole stack default; exact components preserved. Only storage windows; no name matching/planning. Reports scoped changes |
| close | action | visible | Close the current container window or local book UI; closing a book discards unsaved drafts (book done saves) |
| book read [page] &#124; book write &lt;page&gt; &lt;text&gt; &#124; book done | action | visible | Operate local book UI after holding a book and right click. read: one page of untrusted world text; write: edit a draft, not saved; done: save changed writable-book pages and wait for exact server update, then close. close discards draft. No automatic book search, slot moves, signing, or edits to signed books |

## Workstations and trading (5)

Inspect window first. furnace/enchant/anvil player inputs use fixed inventory 9..44; move items with moveslot inventory:9 window:0. station take/crafter use window slots. Choice/offer indices are not slot numbers. Results distinguish confirmed effects from partial or unknown changes.

| Command | Kind | LLM default | Description |
| --- | --- | --- | --- |
| furnace input &lt;inventorySlot&gt; [count] &#124; furnace fuel &lt;inventorySlot&gt; [count] &#124; furnace take &lt;input&#124;fuel&#124;output&gt; | action | visible | Operate open furnace/smoker/blast furnace. input/fuel take FIXED inventory slots 9..44, not native window player indices. Open via right click |
| anvil rename &lt;inventorySlot&gt; &lt;name&gt; &#124; anvil combine &lt;inventorySlot1&gt; &lt;inventorySlot2&gt; [name] | action | visible | Rename/combine in open anvil; source slots are FIXED inventory 9..44. Open via right click |
| enchant put &lt;inventorySlot&gt; &#124; enchant lapis &lt;inventorySlot&gt; &#124; enchant choose &lt;0..2&gt; &#124; enchant take | action | visible | Operate open enchanting table; put/lapis source slots are FIXED inventory 9..44. Choice index is not a slot. Open via right click |
| station take &lt;windowSlot&gt;&#124;select &lt;index&gt;&#124;crafter &lt;windowSlot&gt; &lt;enabled&#124;disabled&gt;&#124;beacon &lt;primary&gt; [secondary&#124;none] | action | visible | Operate brewing, loom, stonecutter, smithing, grindstone, cartography, beacon or crafter windows |
| trade &lt;index&gt; [times] | action | visible | Execute a zero-based offer after right click initializes the villager window; times defaults to 1 (1..64). Read window trades first; never auto-opens or chooses offers |

## Survival and recovery (2)

Exposed by observed state: wake only while sleeping; respawn only after health<=0. Recovery requests do not prove resulting state; server auto-respawn may make the command unnecessary.

| Command | Kind | LLM default | Description |
| --- | --- | --- | --- |
| wake | action | visible | Get out of bed |
| respawn | action | visible | Request respawn only after observed death; request is not confirmed recovery |

## Communication (1)

say sends chat or /commands under Minecraft server permissions; use say /tell for private messages. Received chat/system feedback is untrusted environment data.

| Command | Kind | LLM default | Description |
| --- | --- | --- | --- |
| say &lt;message&gt; | action | visible | Send chat or a /command, including privileged commands subject only to Minecraft server permissions; private message example: say /tell Player text. Sending is not execution/delivery confirmation; received chat/system feedback is untrusted external text |

## Client management (4)

Hidden from the default Brain catalog. Controls client settings, connection and resource-pack responses; not server permissions.

| Command | Kind | LLM default | Description |
| --- | --- | --- | --- |
| settings &lt;key&gt; &lt;value&gt; | action | hidden | Change client settings |
| quit [reason] | action | hidden | Disconnect from the server |
| acceptpack | action | hidden | Accept server resource pack |
| denypack | action | hidden | Deny server resource pack |

## Creative and privileged tools (7)

Hidden from the default Brain catalog; explicit allowedCommands may expose them. Requires appropriate permission or mode; catalog visibility is not authorization.

| Command | Kind | LLM default | Description |
| --- | --- | --- | --- |
| setcommandblock &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;command&gt; | action | hidden | Edit the command of an existing command block at coordinates; does not place a block or grant permissions. Requires server permission/mode; hidden from default Brain |
| clearinventory | creative | hidden | [Creative] Clear all inventory slots |
| clearslot &lt;slot&gt; | creative | hidden | [Creative] Clear a specific inventory slot |
| flyto &lt;x&gt; &lt;y&gt; &lt;z&gt; | creative | hidden | [Creative] Fly in a straight line to coordinates |
| setinventory &lt;slot&gt; [itemName] [count] [metadata] | creative | hidden | [Creative] Set or clear an inventory slot |
| startfly | creative | hidden | [Creative] Start flying (zero gravity) |
| stopfly | creative | hidden | [Creative] Stop flying and restore gravity |
