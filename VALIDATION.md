# Validation and remaining work

This delivery is a **development preview**, not a claim that all requested gameplay behavior is finished or tested.

Verified in this environment:

- Compiles using actual Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.166.0+26.3, Loom 1.19.4, Gradle 9.8.2, and Java 25.
- Produces a mod JAR containing the native screen, host bridge, scoped interaction gate, and real Fabric-client builder controller. Gradle also creates a source JAR.
- Headless Minecraft bootstrap exports 1,286 real vanilla block IDs, state property names/values, and block-entity flags.
- Offline fixture compiles to 332 unique positions and passes support scheduling against the exported registry. Bounds: [-1,0,-1] through [9,10,9].
- Thirty-one Node tests cover actual-registry validation, compiler determinism, coordinate/state rotation, dependency cycles, operation limits, unsafe blocks, contradictory overlaps, invalid states, dimensions, support order, disconnected supports, and air removals.
- HTTP integration test starts the real companion process and exercises separate host/bot credentials, pairing, preview approval, preflight, task dispatch, pause/resume, authoritative-report handling, persistence, conditional undo filtering, cancellation, and stale-session rejection.
- That HTTP test supplies synthetic world observations. It is a bridge integration test, **not a Minecraft bot joining or placing blocks**.
- The Windows Node executable was checked against the official release SHA-256. PowerShell/CMD scripts were reviewed and packaged, but were not executed on Windows here.

Attempted gameplay validation:

`gradle runClient` resolved the game, Fabric and mod and downloaded assets, then exited at graphics initialization:

```
java.lang.IllegalStateException: Unable to initialize SDL: No available video device
at com.mojang.blaze3d.systems.RenderSystem.initBackendSystem
at net.minecraft.client.Minecraft.<init>
```

The selected cloud executor reported remote desktop enabled with state unknown, but no desktop JavaScript/control tool was exposed. The installed `cloud-agent-desktop --help` command returned `Orbit desktop is unavailable`. No usable display was available for a Minecraft world test. No Minecraft account credentials or AI provider credentials were configured.

Still unverified locally:

1. Native screen appearance, focus, layout and B key behavior in the running game.
2. Two-account authenticated LAN join and scoped creative grant.
3. Visible flight, facing, inventory selection and real placements.
4. Flat-site survey and all-position preflight on a real integrated world.
5. Server-confirmed completion and final verification after actual building.
6. In-game pause, cancellation, reconnect recovery, world exit and undo.
7. Actual local Ollama model inference and build quality on the user's PC.

Smallest local next actions:

- Follow README to install the two included JARs in both 26.3 Fabric profiles and start the companion.
- Use a disposable flat creative world and a second authenticated Minecraft account. Pair the builder and run **Offline demo**, then test Pause → Resume → Cancel → Undo before trying a larger plan.
- Open Ollama on the PC and run `start-ollama.cmd`. No API token or billing is needed for local models.

Known implementation gaps relative to the full brief are explicit in README: conservative flat sites instead of comprehensive terrain preparation, simple flight navigation instead of a complete pathfinder, no block-entity construction/NBT undo, no excluded-region editor, and an abbreviated preview for large plans. Bed/block-entity handling and robust end-to-end special-block behavior are not being claimed. The bot runtime reuses an installed, authenticated Minecraft client rather than shipping Mojang's game binaries.

Hugging Face update: the public chat-completion documentation and router model catalog were retrieved successfully. The configured model appears with live providers. Four additional tests verify default/legacy configuration migration, missing-token guidance, the two-stage HF request payload (`max_tokens`, model, bearer token), returned plan validation, and redacted permission errors. These use mocked inference responses; there was no authenticated HF generation or spend. Windows setup script changes were reviewed, but not run on Windows.

Local Ollama update: five additional tests check installed-model discovery, cloud/embedding exclusion, native chat requests without Authorization, brief versus JSON design format, real reported token counters, missing-model rejection without downloads, and loopback enforcement. These use an HTTP fixture rather than actual model execution. The Windows shortcut has not been executed on Windows here. Total: 24 tests passing. Minecraft gameplay remains unverified as described above.

Automatic lifecycle update: 31 Node tests pass. Seven managed-runtime tests cover manual-service preservation, two-client leasing, wrong-secret rejection, last-client shutdown, crash expiry, orphan startup, existing Ollama reuse, and launching/stopping an actual owned subprocess implementing the Ollama protocol. That subprocess is a test fixture, not a real model.

`gradle build runtimeSmoke` passes on Linux/Java 25. The Java launcher extracted the same embedded companion resources included in the mod JAR, launched a real Node companion, shared it between two simulated mod lifecycles in paths containing spaces, kept it alive after the first close, and observed exit after the final close. It used the installed Linux Node executable; the embedded Windows executable is checksum-verified but has not been run here. No Ollama executable is installed in this cloud environment, and the smoke test correctly displayed that actionable startup failure while still proving the companion lifecycle. Windows Minecraft startup, graphics, authentication, visible building and local model quality still require the user's PC.
