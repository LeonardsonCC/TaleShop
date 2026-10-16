# Stationary shop traders

Reported by `0_mika_00` in the [TaleShop comments](https://www.curseforge.com/hytale/mods/taleshop/comments): shop NPCs walk and shake. The supplied report has no game/mod version, recording, or reproduction steps. The visual symptom has **not** been reproduced in this environment.

## API evidence

Inspected the project's Maven `com.hypixel.hytale:Server:0.7.0` artifact and checked the frozen tick gate in 0.7.2 as well. This is inspection of the shipped server implementation, not an assumption based on method names:

- `NPCPlugin.spawnEntity` has a seven-argument overload with `preAddToWorld` and `postSpawn`. The former receives a holder before `Store.addEntity`; the latter runs synchronously after addition. There is no demonstrated scheduled-tick race between these callbacks.
- `RoleBuilderSystem` builds the role when an NPC is added, including on restoration, and assigns the default `Use` interaction. `RoleSystems.RoleActivateSystem` activates motion controllers; `NPCSystems.AddedSystem` calls `role.loaded`. `NPCSystems.AddedFromExternalSystem` runs after these and can remove `Frozen` for world-generated entities.
- `RoleSystems.BehaviourTickSystem` and `SteppableTickingSystem` skip frozen NPCs unless a `StepComponent` explicitly requests a step. Steering, velocity calculation and NPC movement-state updates use this gate. The old code's `Frozen` component is therefore a real supported mechanism; its presence alone is not evidence that the report was fixed.
- `Velocity.CODEC` persists the current velocity. Freezing skips `ComputeVelocitySystem` without resetting that value. A nonzero restored velocity can therefore remain stale indefinitely. The regression test demonstrates this persistence using the actual codec. The old implementation never cleared velocity or pending impulses.
- `MovementStatesComponent` retains separate current and sent states. Resetting locomotion must leave the sent state intact so the tracker can detect and deliver the change.

## Correction

Freeze new traders on the pre-add holder, then normalize their motion after initialization. For registered traders restored into the store, run after NPC initialization and use the provided `CommandBuffer` for structural changes. Both paths share typed API calls: set `Frozen`, remove a one-shot `StepComponent`, clear velocity/client velocity/pending instructions, and reset locomotion flags while preserving posture/environment and the last sent state. Keep the shop's `Use` interaction and hint. Ordinary NPCs are excluded by the shop UUID lookup.

This addresses concrete stale-state and lifecycle weaknesses. It does not establish which of them produced the reporter's visible shaking, nor rule out another mod or client behavior. No per-frame teleport or repeated freeze loop is added.

## Automated validation (2026-10-16)

- `CI=true ./gradlew build --no-configuration-cache`: passed on the default 0.7.0 target.
- `CI=true ./gradlew test --no-configuration-cache -Phytale_version=0.7.2 -Pserver_version==0.7.2`: passed against the Maven 0.7.2 API.
- The same test command with `-PhytaleServerJar=<local 0.7.2 Server/HytaleServer.jar>`: passed against the installed game API. All 18 tests pass, including four new tests for persisted velocity/pending impulses, movement state and network-delta preservation, repeated/missing-component handling, and restoration ordering.
- Headless 0.7.2 server smoke boot in an isolated temporary directory with matching installed assets and the test mod: TaleShop enabled, universe ready, then clean shutdown via `--boot-command stop`. This checks plugin/system registration and startup; it does **not** exercise trader spawning, restoration, shop UI, or visual animation.
- Existing deprecation warnings remain.

The local installed server reports `HytaleServer v0.7.2 (release)`. [Official hotfix notes](https://hytale.com/news/2026/10/hotfixes-update-7) date 0.7.1 to October 13 and 0.7.2 to October 14. Default builds in this PR still declare `ServerVersion =0.7.0`; they must not be advertised as installable on 0.7.2. The separate test build uses `=0.7.2`. Changing the release target is outside this NPC fix.

## Manual acceptance checklist

Close the game/server before switching JARs. Keep exactly one TaleShop JAR in the active mods directory; move the alternate build outside it. The NPC test build does not include the separate enchantment fix.

1. On a test world, spawn a default Klops merchant and at least one other selectable role. Observe standing still for a minute, both nearby and from a distance; approach from several directions and confirm no walking/shaking.
2. Open the shop, complete a trade, close and reopen it. Verify name/hint and interaction still work.
3. Leave far enough to unload the region, return, and repeat the visual/interaction checks with an existing trader.
4. Restart the world/server and repeat with the same trader. Confirm there is no duplicate NPC and that the UUID still resolves to its shop.
5. Check a normal non-shop NPC still walks and interacts normally. Despawn/respawn a shop trader and repeat.
6. If shaking remains, capture game/mod versions, role, singleplayer/server context, other mods, and a short recording, including whether the NPC actually changes server position or only animates.

Visual stability and real player interaction/trades remain pending until these in-game checks are completed.
