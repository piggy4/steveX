# PR #14 review fixes and validation

## Fixed review findings

- `vision/entity` now returns a persisted thin projection. Full restoration `item`, `nbt`, and `living` payloads remain private to the memory-world pipeline.
- Entity lookup is disabled until the current process completes a capture and is rejected when the latest capture dimension differs from the live client dimension.
- Living snapshots carry `effectsKnown=true` only when the integrated-server sampler is available. The memory side clears or replaces effects only when that marker is authoritative.

## Automated validation

Run from the repository root:

```powershell
node --test test/mod_batch.test.js test/pr14_observation_boundary.test.js
node --check src/mod/methods.js
cd vendor/stevex-template-1.21.11
.\gradlew.bat build --no-daemon --console=plain
cd ../stevex-test-template-1.21.11
.\gradlew.bat build --no-daemon --console=plain
```

## Manual game checks

1. Before the first `vision/snapshot`, call `vision/entity` with a UUID from an older session. It must return `ok:false`.
2. Capture in the overworld, change to the Nether, then call `vision/entity` before another capture. It must return `ok:false`.
3. Capture a visible equipped or enchanted entity. `vision/entity` may expose item id, count, and `enchanted`, but must not expose enchantment ids, levels, hidden effects, or raw restoration payloads.
4. In single-player, capture an entity first with an effect and then after the effect expires. The memory entity should gain and then lose the effect.
5. On a remote server, confirm `effectsSource` is `unavailable`; repeated captures must not treat the missing effect list as authoritative empty data.
6. Re-run villager trade selection, enchanting-table button, bookshelf count, and stonecutter recipe selection checks from the PR documentation.

Automated builds verify compilation and packaging. The manual checks above still require the two Minecraft clients and are not replaced by compilation.
