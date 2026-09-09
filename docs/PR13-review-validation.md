# PR13 Review Validation

Scope: research/visual-world-model, based on PR #13 at aaf48ad.
The runtime remains a single Mod connection prototype. Multi-agent coordination
is outside this change.

## Automated Checks

Verified on 2026-09-09: all six Node regression tests passed; the frontend
module parsed successfully; both Fabric projects completed build successfully
with JDK 24 (Java release target 21). The Fabric projects currently have no Java
test sources, so their build results are compilation/packaging checks.

Run from the repository root:

```powershell
node --test test/mod_batch.test.js
```

The suite verifies execution-slot ownership during cleanup, retry after failed
explicit release, no redundant successful release, interruptible stop, and
reporting of failed or throwing cleanup requests.

Build each Fabric project with its wrapper:

```powershell
cd vendor/stevex-template-1.21.11
.\gradlew.bat build --no-daemon
cd ../stevex-test-template-1.21.11
.\gradlew.bat build --no-daemon
```

## In-Game Acceptance

These scenarios require both clients and have not been executed during this
review fix. Compilation does not validate game rendering or interaction.

1. Cross-dimension biomes: capture the overworld, enter the Nether and capture
   once. Stop capturing while the memory client follows into the Nether.
   Its recorded biome cells should restore without another snapshot or file
   change. Repeat after restarting the memory client with a Nether snapshot.
2. Container replacement: open a chest containing diamonds, close it and capture.
   Replace that chest with a furnace at the same coordinates, then capture
   without opening the furnace. The memory furnace must not receive the old
   chest items. Reopen and record an unchanged chest to confirm normal filling
   still works.
3. Colored shulker boxes: open and close an uncolored box and several colored
   boxes with distinct contents. Confirm containers.nbt has records with each
   actual colored block ID and that the memory client restores their contents.
4. Batch cleanup: run a held movement key followed by a wait, then Stop.
   The UI remains running until release completes. A second batch submitted
   during cleanup receives HTTP 409. Release failures are shown in releaseErrors
   and the final status is failed rather than done.

## Review Fixes

- Publish batch terminal status only after cleanup settles.
- Keep unsuccessful explicit releases tracked for cleanup retry.
- Return and render releaseErrors; release failures produce failed status.
- Cache all biome dimension buckets until each dimension processes them.
- Check block identity before applying remembered contents to any container.
- Recognize colored shulker boxes through ShulkerBoxBlock.
