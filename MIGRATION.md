# ProjectRed 1.12.2 Migration Notes

Source: `ProjectRed-1.21.1.zip` (MC 1.21.1, ProjectRed 5.0), read-only, unpacked to temp dirs only.
Target: this repo (MC 1.12.2, ProjectRed 4.9.4).

Migration rule: only swap PNG bytes into existing 1.12 paths. Never copy high-version model/blockstate
JSON, registry code, or renderer code. Old namespace `projectred_*` -> target namespace `projectred`.
Old texture dirs are singular (`textures/block`, `textures/item`) -> target dirs are plural
(`textures/blocks`, `textures/items`).

The substantive ProjectRed changes are now committed by the user. This file remains a migration
record and may be committed separately after manual review. Do not push from the working tree.
Never combine `rm -rf` with any other command in the same tool call — issue it alone. Do not delete
`/tmp` extraction dirs from here; leave cleanup to the user.

Straightforward byte-for-byte texture swaps (Core/Exploration items, ore/metal blocks, backpacks,
gem gear, Expansion machines, Transmission wires/cables, Fabrication IC Workbench block textures,
`prefboard.png`) are complete. The sections below distinguish completed work from remaining
high-version features or compatibility gaps.

## Completed migrations

### Illumar Smart Lamp

The Smart Lamp backport is complete in `illumination/smartlamp.scala` and the illumination proxy.
It includes:

- Six placement orientations with the connectable bottom face attached to the clicked surface.
- Bottom-center, bottom-edge, side bottom-edge, and corner bundled-input topology matching the
  high-version behavior; the opposite/top face does not connect.
- 16-channel bundled input with unsigned-byte maximum per channel, `lightLevel = max / 17`,
  NBT/description synchronization, and dirty/light/render updates on signal changes.
- Stable two-state block rendering: off uses `top`/`side`, on uses `top_on`/`side_on`; the model
  is not dynamically darkened by signal level.
- Multi-colour world halo rendering, bright on-state inventory model, and animated 16-channel
  inventory halo based on the high-version item renderer behavior.
- Recipe, localization, and five migrated Smart Lamp PNG textures.
- CCL particle hooks for hit, destroy, and landing effects, using the bakery's actual sprites
  rather than the missing-model texture.

Current dependency versions are CCL `3.3.8` and CBMultipart/ForgeMultipart CBE `2.6.9`.
The CBMultipart particle fix lives in the adjacent `../CBMultipart` repository, not in ProjectRed:
multipart landing/running effects resolve the contacted part's own sprite instead of using a
ProjectRed or global `stone` texture.

### Frame and cable particles

The ProjectRed-side fixes are complete for the frame block, standalone frame part, framed wires,
and framed insulated red-alloy wires. Their hit/destroy sprites now resolve to the registered frame
or wire sprites, and the frame block's empty model has a valid particle texture.

The shared multipart blockstate/model and dynamic landing/running particle implementation belong
to `../CBMultipart`; ProjectRed must not add a duplicate `forgemultipartcbe` resource that could
reintroduce the `block/block/empty.json` lookup error.

## Blocked on code changes (texture exists, can't be swapped as-is)

### IC Workbench GUI background (`textures/gui/ic_workbench.png`)

Both old and new files are 512x512, so a size check doesn't catch the problem. The old renderer
(`guiicworkbench.scala`) reads fixed pixel regions out of this atlas —
`drawModalRectWithCustomSizedTexture(0, 0, 0, 0, ...)` for the panel background and a second
region at u=330 (`position.x, position.y, 330, 0, ...`) for another element — and the new
version's internal atlas layout puts different art in those exact pixel offsets. Swapping the PNG
without adapting those UV coordinates broke the GUI in testing; change was reverted.

**To unblock**: diff the old vs new atlas layout and remap the `u,v` offsets in
`guiicworkbench.scala` to the equivalent region in the new atlas, or keep the old GUI texture
until that adaptation is done. Small code task, not a texture copy.

## High-version features and remaining gaps

The sections below distinguish completed high-version migrations from features that still have no
compatible 1.12 implementation. For new blocks or machines, the feature must be implemented first
(registry, block/tile classes, models, recipes); textures are the last step, not the first.

### Pneumatic Tube transport system

The first transport slice is now implemented in
`transportation/pneumatics` and replaces the registered pressure/resistance tube entries.
The implementation is now functionally complete for the currently selected 1.12 scope. Full
high-version backstuff graph semantics and a dedicated tube model/texture adaptation remain
deliberately deferred.
The current slice includes:

- **`PneumaticTubePayload`**: a plain data object (ItemStack + progress 0-255 + speed + in/out
  side) — no per-tick entity, just a progress counter that drives visual position.
- **`PneumaticTransport`**: per-tube-segment payload ticking, midpoint output selection,
  next-tube/device handoff, blocked-output retry, NBT persistence, description sync, and
  add/update/remove packet callbacks.
- **`PneumaticTransportDevice`** / **`PneumaticTransportContainer`**: the internal contract between
  tube segments and endpoint machines (`canConnectTube`, `canAcceptPayload`, `insertPayload`).
  Item importers and block breakers now use a native `PneumaticQueue` device adapter in
  `deviceabstracts.scala`; their pneumatic payloads no longer pass through the old pressure
  payload state machine. The migrated devices include the normal and filtered importers plus
  both block-breaker tiers.
- **`PneumaticTubePart`**: registered as metadata 64, rendered through the existing multipart pipe
  renderer, with dynamic item flow, round-robin exits, blocked-output retry, item drops when
  the part is removed, separate transit/external-insert payload paths, and full-container bounce
  that swaps input/output directions and excludes the failed endpoint from re-selection.

The graph slice includes `GraphContainer`, `GraphNode`, weighted `GraphRouteTable`,
`GraphRoutePathfinder`, `GraphLinkPathfinder`, and `PneumaticExitPathfinder` (files:
`pneumatics/graph.scala`, `pneumatics/pathfinder.scala`). Active nodes retain graph links while
straight redundant tube segments are traversed and compressed into complete `GraphLinkSegment`
paths. Tube nodes invalidate topology on multipart/neighbor changes and rebuild after placement,
chunk loading, and world re-entry. Output selection uses shortest reachable endpoint routes, with
round-robin selection among equal first-hop directions.

Device entry separates `PASSIVE_NORMAL` (normal input face) from `PASSIVE_BACKSTUFF` (backlog
face only), and the tube entry gate requires at least one reachable normal exit before accepting
a new payload. Full-container bounce, transit handoff, blocked-output retry, and backstuff queue
behavior have been validated in game without the previous one-segment loop or visual oscillation.
The tube discovers and injects into ordinary `IInventory`, `ISidedInventory`, and Forge ItemHandler
endpoints using the existing 1.12 side convention.

The client link cache and `PneumaticSmokeParticle` now render smoke along added/removed graph links,
using the existing `projectred:textures/particles/smoke.png` (identical to the high-version smoke
texture). `pressurize` and `depressurize` SoundEvents, six high-version OGG files, sounds.json,
and client-local playback are implemented. Initial graph synchronization and client callback setup
cover placed parts, chunk/world re-entry, and old tubes reconnecting to newly placed tubes.

The original pressure-tube appearance is intentionally retained: the high-version
`pneumatic_tube.png` is an atlas mapped by the high-version OBJ model and does not match the shared
1.12 `PipeModelGenerator` UV layout. The high-version OBJ model is not imported because it is shared
by the general tube renderer and would affect unrelated pipe types. Full mode-aware backstuff graph
routing and a dedicated pneumatic model/texture adaptation remain deferred.

The high-version `TransposerBlockEntity` was reviewed and is functionally equivalent to the
1.12 `TileItemImporter`: both are redstone-triggered directional inventory importers backed by a
PneumaticQueue, exporting to a pneumatic tube or item entity. The current 1.12 importer also retains
entity-suction behavior that is empty in the reviewed high-version Transposer, so no duplicate
`TileTransposer` is planned. `TileFilteredImporter` provides the corresponding filtered variant.

The old pressure-tube implementation has been removed now that the pneumatic replacement is
validated in game: `pressurepipetraits.scala` (`TPressureSubsystem`, `TPressureTube`,
`TPressureDevice`, `PressureTube`, `ResistanceTube`) and `pressurepathfinders.scala`
(`PressurePathFinder`, `PressurePriority`) are deleted, and the unused `PressurePayload` class and
its dead rendering branch were removed from `payload.scala`/`renders.scala`. `PipeDefs` and
`createPart` no longer reference either old tube type; only `PNEUMATICTUBE` (metadata 64) remains
for the pneumatic slice.

### Illumar Smart Lamp

Completed — see the Smart Lamp entry under [Completed migrations](#completed-migrations).

### New Fabrication production chain (Plotting/Lithography/Packaging Tables)

A replacement for the old single-step IC crafting: Plotting Table prints a photomask design onto
a blank photomask, Lithography Table etches a silicon wafer using photomasks through a multi-stage
pipeline (`LithographyPipeline`/`ProcessNode`/`YieldCalculator`, wafer types, yield/defect
modelling), Packaging Table turns an etched wafer + housing into a finished die/IC. Adds several
new items (`base_silicon_wafer`, `rough_silicon_wafer`, `etched_silicon_wafer`,
`blank_photomask`/`photomask_set`, `valid_die`/`invalid_die`/`valid_die_template`). 1.12's IC
Workbench crafting flow is single-step and has none of these intermediate items or machines.
Porting means designing and implementing the whole pipeline, not just adding blocks. Not started.

### IC internal IO pin components (`io_buffer`, `io_bundled_buffer`, `io_bundled_bus`, `io_redstone_connector`, `io_bundled_connector`, `io_potentiometer`)

These are components used *inside* the Fabrication IC editor/engine (`RedstoneIOGateTile`,
`BundledColorIOGateTile`, `BundledBusIOGateTile`, `AnalogIOGateTile`, `SingleBitIOGateTile`) to
represent an IC's external pins, not standalone in-world gate blocks. They only make sense once
the underlying IC engine/editor (`mrtjp.fengine`, `ICWorkbenchEditor`) is ported — which it hasn't
been. No standalone migration path for just the textures.

### Counter / Timer gate GUI backgrounds (`counter_gate.png`, `timer_gate.png`)

Not a compatibility gap in the gate logic itself — 1.12 already has `Counter`/`Timer` gates
(`GuiCounter`/`GuiTimer` in `guis.scala`). The difference is purely how the GUI is drawn: the old
GUIs are procedurally rendered via `mrtjp.core.gui.GuiLib.drawGuiBox` (no background texture at
all), while the new version draws a fixed background image. Adopting these textures would require
rewriting `GuiCounter`/`GuiTimer` to draw a textured background instead of the procedural box —
a GUI code change, not a texture-only swap. Low priority since the procedural GUI already works.

### Deepslate ore variants, raw metal stage — deliberately deferred

These track newer vanilla Minecraft (1.17+ deepslate, 1.20 raw-metal smelting stage) features that
don't exist in 1.12.2's base game at all. Out of scope for this project regardless of ProjectRed's
own code; not revisited until/unless the project itself targets a newer Minecraft version.

## Validation checklist (for any future batch)

1. Extract high-version PNGs from the ZIP to a temp dir; never unzip into the working tree.
2. Copy mapped high-version PNGs onto their target 1.12 paths.
3. Run `git status --short` / `git diff --stat` for the actual change list — files that don't show
   up were already byte-identical to the high-version source (alignment, not a real change); only
   dig further into files that *do* show a diff.
4. `identify`/ImageMagick-check every PNG that shows up in the diff.
5. `./gradlew processResources compileScala` (add `--offline` if remapping hangs on network).
6. For the pneumatic slice, test tube-to-tube transfer, graph route selection, device insertion/export,
   blocked outputs, part removal drops, client payload rendering, link smoke/sounds, world re-entry,
   and multiple payloads in one segment.
7. The user commits substantive changes manually; this document may be committed separately after
review. Do not push from the working tree.

## Migration log

- `fd83f325` Add empty model to disable exceptions in log — CBMultipart/FMP model resource.
- `683028da` Bump fmp — dependency update.
- `f2304c8e` Migrate to new prefboard texture.
- `0276c96d` Fix particle missing in cable and frame — frame block/part and framed-wire sprites,
  plus the CCL `3.3.8` dependency bump.
- `d8a6e06d` Port smart lamp from high version — Smart Lamp block/tile/renderer, recipe,
  textures, localization, multi-colour halo, and CCL particle hooks.
- Current pneumatic migration — native PneumaticQueue devices, cached graph routing, complete
  link-path smoke particles, pressurize/depressurize sounds, blocked-output bounce fixes, and
  world re-entry graph/client synchronization. The user validated the current scope in game.

## Operational hazard log

- A subagent-run `rm -rf` once wiped the whole working tree; a separate incident combined `rm -rf`
  with a `cd ProjectRed && ...` in one multi-line bash call and the shell swallowed the newline,
  turning `cd`+path into extra `rm -rf` arguments and deleting `.git` too. Both times required
  restoring from `origin/1.12.x`.
  **Rule: never combine `rm -rf` with any other command in the same tool call. Always issue
  `rm -rf <target>` alone, with no trailing `&&`/`;` chained commands.**
