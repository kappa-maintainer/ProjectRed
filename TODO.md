# TODO

> Compiled from `TODO` and `FIXME` comments in the project source (scan scope: `src/`; duplicate comments for the same feature have been consolidated).
> Line numbers refer to the current source. Priority: `P0` = functional/correctness risk, `P1` = should be addressed soon, `P2` = optimization or cleanup.

### Todo

#### P0 / Functionality and Correctness

- [ ] Rewrite the block placer interaction flow to support the new interaction system ~3d #bug #expansion
  - [ ] Keep the fake player's inventory, held item, and item consumption state synchronized so implementations receive the correct `ItemStack` — `src/main/scala/mrtjp/projectred/expansion/TileBlockPlacer.scala:116-128`
  - [ ] Correct block right-click and entity interaction handling, including main-hand/off-hand behavior, return values, and item state — `src/main/scala/mrtjp/projectred/expansion/TileBlockPlacer.scala:128-173`
- [ ] Implement implicit wire-net construction for Fabrication buttons and levers; `buildImplicitWireNet` currently returns `null` ~2d #feat #fabrication
  - [ ] `src/main/scala/mrtjp/projectred/fabrication/buttonpart.scala:91`
  - [ ] `src/main/scala/mrtjp/projectred/fabrication/leverpart.scala:70`
- [ ] Fix or implement lamp particle effects; `randomDisplayTick` currently contains only commented-out code — `src/main/scala/mrtjp/projectred/illumination/blocks.scala:251` ~1d #bug #illumination
- [ ] Fix the non-functional line drawing in the chip configuration GUI — `src/main/scala/mrtjp/projectred/transportation/chipconfiggui.scala:214` ~1d #bug #transportation
- [ ] Implement proper ingredient matching for the IC Printer instead of selecting only the first candidate stack for each ingredient — `src/main/scala/mrtjp/projectred/fabrication/tileicprinter.scala:349` ~2d #bug #fabrication
- [ ] Verify whether the integrated-circuit comparator calculations are still accurate — `src/main/scala/mrtjp/projectred/integration/gatepartseq.scala:618` ~1d #bug #integration
- [ ] Complete neighbor updates after moving blocks and determine whether the source block also needs to be updated — `src/main/scala/mrtjp/projectred/relocation/movement.scala:169` ~1d #bug #relocation
- [ ] Improve entity movement during block relocation and replace the temporary `d.y * 4 max 0` implementation — `src/main/scala/mrtjp/projectred/relocation/movingblock.scala:93` ~1d #bug #relocation
- [ ] Verify inventory capability comparison in the logistics system; some mods return a new capability object on every access — `src/main/scala/mrtjp/projectred/transportation/netpathfinders.scala:168` ~1d #bug #transportation
- [ ] Improve craft-item marking in crafting request packets; the current implementation forces a quantity of 1 when the quantity is 0 — `src/main/scala/mrtjp/projectred/transportation/packethandlers.scala:110` ~1d #bug #transportation
- [ ] Move payload-arrival event handling into the router instead of publishing it directly from the pipe trait — `src/main/scala/mrtjp/projectred/transportation/netpipetraits.scala:288` ~1d #refactor #transportation
- [ ] Move the client-side `MovementManager` tick call to a more appropriate event so blocks do not move while the client is paused — `src/main/scala/mrtjp/projectred/relocation/events.scala:66` ~1d #bug #relocation

#### P1 / Feature Improvements

- [ ] Implement a backpack blacklist and apply it during item validation — `src/main/scala/mrtjp/projectred/exploration/items.scala:128` ~1d #feat #exploration
- [ ] Check both hands when opening the backpack to support dual wielding instead of checking only the currently held stack — `src/main/scala/mrtjp/projectred/exploration/items.scala:114` ~1d #feat #exploration
- [ ] Pass the Gem Sickle's attack damage and speed from `ToolDef` correctly and remove the hard-coded `3, 0` — `src/main/scala/mrtjp/projectred/exploration/items.scala:228` ~1d #bug #exploration
- [ ] Support hand passthrough for the Barrel and define main-hand/off-hand behavior when inserting items — `src/main/scala/mrtjp/projectred/exploration/TileBarrel.scala:137` ~1d #feat #exploration
- [ ] Redesign the temporary plant-growth algorithm, replacing the current simple 50% random check — `src/main/scala/mrtjp/projectred/exploration/blocks.scala:378` ~1d #gameplay #exploration
- [ ] Confirm whether machine condition updates should run only on the server or on both client and server — `src/main/scala/mrtjp/projectred/expansion/machineabstracts.scala:170` ~2h #bug #expansion
- [ ] Review the Project Bench JEI `canHandle` implementation, which currently always returns `true` — `src/main/scala/mrtjp/projectred/expansion/ExpansionJEIPlugin.scala:40` ~2h #bug #expansion
- [ ] Confirm whether the filtered importer's color should use wool metadata or dye IDs, and standardize the color mapping — `src/main/scala/mrtjp/projectred/expansion/TileFilteredImporter.scala:122` ~2h #bug #expansion
- [ ] Preserve the original entity age when copying dropped items in the Teleposer, or confirm that preserving it is unnecessary — `src/main/scala/mrtjp/projectred/expansion/TileTeleposer.scala:133` ~2h #bug #expansion
- [ ] Return the correct block face shape for the IC Printer and remove the `UNDEFINED` placeholder implementation — `src/main/scala/mrtjp/projectred/fabrication/tileicprinter.scala:144` ~2h #bug #fabrication
- [ ] Optimize the new IC editor initialization protocol to avoid sending a complete blank IC description — `src/main/scala/mrtjp/projectred/fabrication/guiicworkbench.scala:639` ~1d #perf #fabrication
- [ ] Remove or complete the IC editor input/output update messages (protocol cases 6/7 and the sending methods are currently marked as unused) — `src/main/scala/mrtjp/projectred/fabrication/ictileeditor.scala:209-210,239-242` ~1d #refactor #fabrication
- [ ] Store IO side modes at map level and then remove the temporary IO conflict check — `src/main/scala/mrtjp/projectred/fabrication/SimInterface.scala:87` ~2d #refactor #fabrication
- [ ] Determine whether `pullOutputRegisters(mask)` still needs a mask; simplify it to always pull all outputs if not — `src/main/scala/mrtjp/projectred/fabrication/SimInterface.scala:62` ~2h #refactor #fabrication
- [ ] Optimize register-change detection to avoid scanning the complete IO register range on every change — `src/main/scala/mrtjp/projectred/fabrication/SimInterface.scala:75` ~1d #perf #fabrication
- [ ] Handle `parseBlockMeta` failures in mover configuration with an explicit error instead of silently ignoring them — `src/main/scala/mrtjp/projectred/relocation/moveregistry.scala:62` ~2h #bug #relocation
- [ ] Confirm the semantic difference between `World#blockExists` and `World#isBlockLoaded`, then use the correct pre-movement check — `src/main/scala/mrtjp/projectred/relocation/moveregistry.scala:81` ~2h #bug #relocation
- [ ] Determine whether `latchOps` is obsolete; remove the field and related API if it is unused — `src/main/scala/mrtjp/projectred/relocation/stickregistry.scala:26` ~2h #refactor #relocation
- [ ] Review the `else?` branch in the redwire internal-signal logic and add the correct connection condition or documentation — `src/main/scala/mrtjp/projectred/transmission/redwires.scala:125` ~2h #bug #transmission
- [ ] Split request, broadcast, and related functionality from the logistics pipe trait into dedicated services — `src/main/scala/mrtjp/projectred/transportation/netpipetraits.scala:33` ~2d #refactor #transportation

#### P2 / Optimization, Compatibility, and Cleanup

- [ ] Use the existing 1.12 `writeVector` method for bundled-wire packets instead of manually serializing coordinates — `src/main/scala/mrtjp/projectred/transmission/bundledwires.scala:152` ~2h #cleanup #transmission
- [ ] Remove `discoverStraightOverride` and use `discoverStraightCenterOverride` consistently — `src/main/scala/mrtjp/projectred/core/connectabletiles.scala:132` ~2h #cleanup #core
- [ ] Optimize `LampRenderer` by evaluating the 1.12 CCL model-wrapping approach — `src/main/scala/mrtjp/projectred/illumination/blocks.scala:123` ~1d #perf #illumination
- [ ] Add Illumar light block/entity registration or remove the obsolete registration placeholder — `src/main/scala/mrtjp/projectred/illumination/proxies.scala:41` ~1d #feat #illumination
- [ ] Review the commented-out `isEmpty` TODO in the Barrel and either remove the obsolete marker or provide an explicit implementation — `src/main/scala/mrtjp/projectred/exploration/TileBarrel.scala:130` ~2h #cleanup #exploration

### In Progress

- [ ] No items are explicitly marked as in progress

### Done ✓

- [ ] No items can be confirmed as completed based on the current source TODO markers
