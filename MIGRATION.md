# ProjectRed 1.12.2 Migration Notes

Source: `ProjectRed-1.21.1.zip` (MC 1.21.1, ProjectRed 5.0), read-only, unpacked to temp dirs only.
Target: this repo (MC 1.12.2, ProjectRed 4.9.4).

Migration rule: only swap PNG bytes into existing 1.12 paths. Never copy high-version model/blockstate
JSON, registry code, or renderer code. Old namespace `projectred_*` -> target namespace `projectred`.
Old texture dirs are singular (`textures/block`, `textures/item`) -> target dirs are plural
(`textures/blocks`, `textures/items`).

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

Implemented (`c577fc26`, `b55ecfbd`, `9022369f`, calibrated in `fabab71c`). This adds a
three-machine chain alongside the pre-existing single-step IC crafting.

**The single-step route is deliberately kept.** The high version removed it outright, together
with the IC chip item and the IC Printer block: it registers no recipe producing the fabricated
gate at all, so the packaging table is its only source. That removal does not carry over. Deleting
an item in 1.12 leaves existing stacks unresolved -- there is no `MissingMappings` handling -- and
the chip and printer are pre-existing working features rather than part of this migration. Both
routes also converge on the same output item (`GateDefinition.ICGate`, filled by
`ItemICBlueprint.copyToGate`), so keeping both carries no interoperability risk.

The two routes differ in cost, which is what makes coexistence reasonable. Per IC gate, the
single-step route spends 10 circuit plates, 7 gold nuggets and one silicon, while the chain spends
8 plates and 1/N silicon, where one 16x16 wafer yields roughly 139 valid dies. The chain is the
bulk route; the single-step recipe stays the low-infrastructure one.

- **Plotting Table** (`fabrication_machine:0`) prints a blueprint design onto a blank photomask,
  producing a photomask set. 10s at 100W.
- **Lithography Table** (`fabrication_machine:1`) etches a rough silicon wafer through the photomask
  set and cuts it into dies, yielding valid and invalid dies separately. 60s at 100W.
- **Packaging Table** (`fabrication_machine:2`) combines a valid die with a ring of core plates into
  a finished IC gate. 20s at 100W.

Design data travels as item NBT (`ICData`) using exactly the keys the existing IC Workbench and IC
gate already read and write, so a design is usable by every stage and by the pre-existing
single-step flow. Rough silicon wafer is produced by smelting ProjectRed silicon, matching the high
version's `minecraft:smelting` recipe.

**Item scope.** The high version registers six fabrication items; the port covers all five that
were new here -- `rough_silicon_wafer`, `blank_photomask`, `photomask_set`, `valid_die` and
`invalid_die`, the sixth being the pre-existing `ic_blueprint`. Three further names that an earlier
draft of this document listed are not items in the high version either and were deliberately not
created: `etched_silicon_wafer` (its item class is commented out at `FabricationItems:24,35`),
`base_silicon_wafer` (a base class that is never registered, `BaseSiliconWaferItem`) and
`valid_die_template` (does not exist anywhere in the high-version tree).

**Pipeline scope.** Only `PROCESS_64NM`, `ROUGH_WAFER` and single-layer designs are reachable -- in
this port and in the high version alike. `LithographyPipeline.ADVANCED` is referenced nowhere
outside its own declaration, its diagram is an empty stub (`PipelineDiagramNode:37`), the
production path never consults layer count, and `Lithographer` is three empty methods. The
pipeline's compatibility matrix is therefore omitted: with one node and one wafer it could never
reject anything. The remaining wafer types (`PURIFIED_WAFER`, `POLISHED_WAFER`) and the 32/16/8nm
nodes stay unimplemented.

**Yield calibration.** The defect model is calibrated against the largest board, not an average
one. 1.12's board size is `selectedBoardSize * 16`, so designs actually span 16x16 .. 64x64 tiles
-- not the 1x1..4x4 the IC Workbench sizer grid suggests. Calibrating against a 64x64 die keeps the
defect rate meaningful across the whole range: 3.125% at 16x16 rising to 50% at 64x64. The high
version instead hard-codes 16x16 (it carries an unresolved `TODO Source from photomask itemstack
instead of assuming 16x16`); this port reads the real design size, so a small board is not
evaluated as if it were a large die.

**Remaining gap: working-state appearance.** The three tables show no visual change while charging
or working; the high version swaps to `front_1`/`front_2` via `CHARGED`/`WORKING` block states.
Progress and power are visible in the GUI, so this is cosmetic and not a functional gap.

#### IC simulation engine options considered

High-version Fabrication compiles circuits via the external `io.github.mrtjp:fabrication-engine`
library (source: github.com/MrTJP/fabrication-engine), jarJar'd into the mod rather than vendored.
Evaluated it and one third-party alternative as potential replacements for 1.12's own
`SimEngine`/`SimInterface`/`SimLinker`.

- **`fabrication-engine`** (Maven: `io.github.mrtjp`, confirmed live on
  `proxy-maven.covers1624.net`, latest `0.1.0-alpha-19`, no sources jar ever published). Same
  author, same core propagation algorithm as 1.12's `SimEngine` (identical changeQueue-driven
  incremental recompute, identical 32-cycle overflow threshold) — likely a common Scala ancestor,
  later rewritten to Java. Advantages: type-safe byte registers (vs 1.12's runtime-polymorphic
  `asInstanceOf` casts), and a real six-phase assembly pipeline with register remapping that
  supports nested sub-circuits (`addTileMap`/`addFlatMap`) — a capability 1.12 has no equivalent
  for. Disadvantages: byte-only registers make multi-byte values (e.g. a 64-bit timer) clunkier
  than 1.12's native `Long` registers; the step-through assembler needed for live editor feedback
  is the least-finished part of the library (18 `TODO: Return something useful` placeholders);
  project has been stuck in alpha for its entire history and saw no commits in over two years.
- **`RedstoneControl`/`rscpl`** (github.com/CD4017BE/RedstoneControl, last commit 2021-02-08,
  unmaintained 4+ years). Fundamentally different approach: compiles circuit graphs to real ASM
  bytecode classes instead of interpreting a register model, giving native JIT-optimizable
  execution and a real static type system (int/long/float/double, not byte-only). Requires a
  bytecode allow-list security checker to safely load generated classes. Critically, its
  compile-time DAG ordering rejects combinational feedback loops outright (`CAUSAL_LOOP` compile
  error) — incompatible with 1.12's existing SR Latch/Toggle Latch/StateCell/Synchronizer gates,
  which rely on runtime convergence. Not evaluated further as a drop-in replacement.

**Decision**: keep 1.12's own `SimEngine`/`SimInterface`/`SimLinker`. Neither external engine is a
safe dependency (alpha-stage/unmaintained, in both cases without a compatible feedback-loop
semantics or a finished editor-feedback path), and 1.12's own implementation already covers the
gate set in active use. If nested-IC support is wanted later, borrow the six-phase/remap design
idea from `fabrication-engine` and implement it directly against `SimLinker` rather than adding
the external dependency.

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

## Migration log

- `fd83f325` Add empty model to disable exceptions in log — CBMultipart/FMP model resource.
- `683028da` Bump fmp — dependency update.
- `f2304c8e` Migrate to new prefboard texture.
- `0276c96d` Fix particle missing in cable and frame — frame block/part and framed-wire sprites,
  plus the CCL `3.3.8` dependency bump.
- `d8a6e06d` Port smart lamp from high version — Smart Lamp block/tile/renderer, recipe,
  textures, localization, multi-colour halo, and CCL particle hooks.
- `ecea812b` Backport new pneumatic pipe logic — native PneumaticQueue devices, cached graph
  routing, complete link-path smoke particles, pressurize/depressurize sounds, blocked-output
  bounce fixes, and world re-entry graph/client synchronization. The user validated this scope in
  game.
- `c2f8081e` Update migration info — documentation pass by the user.
- `5e2cfff9` Bump build toolchain and dependencies — Cleanroom/Forge and dependency updates, kept
  separate from the fabrication framework that follows.
- `c577fc26` Port high-version fabrication production framework — production chain scaffolding.
- `b55ecfbd` Add fabrication production chain foundations — the `ICData` contract and the
  lithography data model.
- `9022369f` Implement fabrication production chain machines — Plotting/Lithography/Packaging
  tables with slots, work timers, recipes, containers and GUIs.
- `fabab71c` Fix fabrication table calibration, bounds and models — yield calibration moved to the
  largest board, 10/16 collision and selection bounds, and per-variant baked-model cache keys.
- `ec3787ec` Localize fabrication machine GUI labels — first `I18n` use in the project; titles
  reuse the existing block name keys so a title and its item name cannot drift apart.
- `4a72d4da` Localize GUI labels across all modules — 26 panel titles and 12 button labels. Machine
  and pipe titles reuse the existing block/item name keys; the 24 new keys are the button labels and
  the router requester/extraction strings.
- `b2ec6626` Align scala_version with the compiler version — the tasty inspector was older than the
  compiler producing the tasty it had to read.
- `6f1456eb` Jappaify copper — copper ore and ingot texture replacement.
- `e1d93c4c` Localize routing chip tooltips — ~70 strings, one key per conditional branch, with the
  `Matching:` label list rebuilt rather than concatenated.
- `59943b8d` Localize the messages the server sends to players — `TextComponentTranslation`, plus a
  fix for embedded item names that resolved against a server-side language map holding only
  vanilla's `en_us`.

## Operational hazard log

- A subagent-run `rm -rf` once wiped the whole working tree; a separate incident combined `rm -rf`
  with a `cd ProjectRed && ...` in one multi-line bash call and the shell swallowed the newline,
  turning `cd`+path into extra `rm -rf` arguments and deleting `.git` too. Both times required
  restoring from `origin/1.12.x`.
  **Rule: never combine `rm -rf` with any other command in the same tool call. Always issue
  `rm -rf <target>` alone, with no trailing `&&`/`;` chained commands.**
