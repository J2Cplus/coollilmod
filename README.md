# LunaBuilder — Minecraft Java 26.3 / Fabric

**Download the compiled mod:** open this repository's **Releases** section and choose the LunaBuilder preview. Use the JAR if Fabric API is already installed, or the Windows ZIP for both mods and setup notes. Launching Minecraft starts the embedded companion and local Ollama automatically.

This is a compiled development preview with a native Minecraft screen, local companion, declarative build compiler, and an actual Fabric-client builder controller. **It has not passed a live gameplay test and is not a fully verified implementation of the entire requested specification.** Use a disposable creative test world first. See `VALIDATION.md` for exact evidence and limitations.

## Windows 11 first run

1. Extract the complete Windows ZIP to a local folder. Keep its `companion`, `runtime`, and `mods` folders together. Paths containing spaces are supported.
2. Install Fabric Loader **0.20.1 for Minecraft 26.3** using the installer from https://fabricmc.net/use/installer/. Select that profile in Minecraft Launcher. Minecraft 26.3 needs **Java 25**; the official launcher normally supplies its game runtime.
3. Put both JARs from `mods` into your host instance's mods folder. Alternatively run PowerShell in the extracted folder:
   ```powershell
   .\install-mod.ps1
   ```
   For a custom profile:
   ```powershell
   .\install-mod.ps1 -ModsFolder 'D:\Minecraft Profiles\Creative\mods'
   ```
   Use the same Minecraft, Fabric, and mod versions in the builder instance. Do not install two Fabric API versions in one instance.
4. **Launch Minecraft normally. No companion script is required.** The mod JAR now includes the companion code and a checksum-verified portable Windows x64 Node runtime. After the game starts, LunaBuilder extracts its versioned runtime under the Minecraft instance's `config/lunabuilder/runtime`, starts the companion in the background, reuses your running Ollama or starts your installed `ollama serve`, and discovers an installed local chat model. It never downloads a model or falls back to cloud inference.
5. Open your own **creative singleplayer** world. Press **B**, then **Start local builder**. Controls → Key Binds → LunaBuilder lets you change B. Open Preview to see the pairing code.
6. Launch a **second, separate Minecraft 26.3 Fabric instance with a different authenticated Minecraft account** and a different game directory. The mod in that instance is the real bot runtime. From its title screen, click **LunaBuilder** in the top-left corner and enter the host's code. It connects to the host's actual Minecraft port over **127.0.0.1**. Keep both instances running. The host gives creative mode only to the paired name after verifying the authenticated player's connection is loopback; it does not grant operator permissions or change server authentication.
7. On the host, choose **Offline demo** to exercise the pipeline without an API key. This is a labeled 332-block garden pavilion fixture, not AI generation. Move to a broad, flat, empty area so loaded chunks contain its 11×11 footprint. Review Preview, rotate or move the footprint, then **Start**. Pause, Resume, Cancel, and Undo are on Preview.
8. Local inference uses **`http://127.0.0.1:11434/api/chat`**. The actual installed model name is detected automatically from Ollama's tags and completion capabilities. You can change it in Settings. No API key or per-request inference fee is needed. Your Ollama installation and local model files must already exist; the mod starts services, not installers or model downloads.

**Automatic lifecycle:** each running Minecraft instance holds a private heartbeat lease. Instances share the loopback companion, so closing one does not stop another instance's services. Closing the last instance releases its lease and shuts down the managed companion and any Ollama daemon it started. Abrupt crashes expire the leases; cleanup takes up to about 30 seconds. An Ollama app/service that was already running is left alone. A manually started companion is also left running. Background state and failures appear in the LunaBuilder screen. Logs are in `config/lunabuilder/logs`; settings and job data are outside the versioned runtime cache, in `config/lunabuilder/companion.json` and `config/lunabuilder/data`.

**Upgrading:** replace the old LunaBuilder JAR with the new one. If an older manually launched companion still occupies the control port, stop it once; the mod then manages the current service automatically. New auto-managed settings live in the Minecraft instance folder rather than the old extracted companion folder. Old journals are not silently copied between these locations. Keep the old data folder if you need its journals.

Windows x64 needs no separately installed Node runtime. Source/Linux testing can use Node 24+ on PATH or `-Dlunabuilder.node=/full/path/to/node`. If Ollama isn't on PATH, the runtime checks its normal Windows installation locations; `LUNA_OLLAMA_EXECUTABLE` can point to another installation. The optional legacy startup scripts remain in the full Windows bundle for manual diagnostics, but normal play does not use them.

**Builder account:** automatic services do not sign into or supply a second Minecraft account. The visible builder remains a separate authenticated Fabric client/profile. Its launcher can be configured as before; authentication and pairing requirements still apply.

Local generation shares RAM/VRAM with Minecraft. A large model may be slow or exhaust memory. The cloud build cannot access your PC's model files. The lifecycle is tested with a real Node companion and local Ollama-protocol fixtures; Windows gameplay and actual model quality remain unverified here.

## Using the screen

- **Build:** multiline prompt, style, detail, size, explicit maximum dimensions, conservative terrain replacement permission, interior preference, Generate, and Offline demo. Changing the prompt and generating again creates a new plan; earlier plans are archived.
- **Preview:** top-down material-colored plan, relative orientation, world origin, actual phase, confirmed positions, paired account, and job controls. It is a diagram, not a photorealistic render. **Aim site** uses the ground block you were looking at when opening the screen. Move adjusts the survey area by eight blocks; a safe site must still pass inspection.
- **Library:** saved plans from this world. Loading one validates it against the current registry and surveys a fresh site; it does not immediately build. Original journals remain in archived job files.
- **Settings:** local Ollama endpoint, installed model ID, API-key requirement (none for Ollama), and placement rate. Optional **Launch builder** runs your configured executable plus argument array with `shell:false`. It does not install Minecraft or authenticate a second account. Set it to your own legitimate launcher/profile if you want launcher supervision; manual launch is the default.

AI first writes a brief, then structured JSON. The compiler supports fill, floor, wall, hollow box, pillar, stairs, connected gable roof, path, and explicit placements. Windows, doorways, arches, furniture, and repeated components can be composed from those operations. It validates actual vanilla IDs and state values, dimensions, operation/expansion limits, contradictory overlaps, dependency cycles, and unsupported block entities. AI output is data and is never executed. Up to three structured design attempts are allowed with validation feedback.

Before building, the host inspects all target positions in batches of at most 128 and journals original block states. A deterministic scheduler grows components from real neighboring supports. The second client flies, selects creative items, predicts vanilla placement state from its click/rotation, and sends normal interactions. The host advances progress only after observing authoritative state and runs a final verification pass. No setBlock, /fill, WorldEdit, schematic paste, or fake player entity is used for construction or undo.

## Network and persistence

The companion bridge is loopback-only and rejects browser origins. Host and bot use different session secrets. The displayed pairing code expires when the host session ends. The bridge port is **not** the Minecraft LAN port. Vanilla 26.3 LAN publishing binds all interfaces, not just loopback; LunaBuilder accurately uses that standard LAN behavior and connects the bot through loopback. It does not change router settings, firewall settings, or global authentication.

For automatic startup, plans, prompts, compiled tasks, site, dimension, status, confirmations, failures and journals are stored under the host Minecraft instance's `config/lunabuilder/data`. Legacy manual scripts still use their configured companion data directory. World identity combines a UUID in the save with a fingerprint of the save directory. A copied save at another path cannot automatically resume the original job. A changed dimension ends the live session. On host exit the paired builder's previous mode is restored and it is disconnected. Bridge loss expires the host interaction permit and the bot heartbeat.

Undo restores only journaled, dispatched/confirmed positions whose current state still matches the job's expected result. Later differing player edits are skipped. Block entities and inventories are excluded; their NBT is not restored. Undo uses the real client incrementally and may fail for states vanilla interaction cannot recreate. Keep the relevant data folder: the JAR alone does not contain journals. If a recovered job is paused, inspect the site before Resume; cancellation leaves existing confirmed changes for Undo.

## Current limits

- Live bot login, movement, building, UI appearance, pause behavior in game, and undo **still need verification on a machine with a graphics display and two authenticated accounts**. Compilation does not establish these behaviors.
- Site selection is deliberately conservative: bounded sampled searches of loaded, flat natural ground, followed by exact target inspection. It rejects uneven land rather than implementing full leveling, tree removal, or advanced terrain preparation. There is no excluded-region editor.
- Flight uses deterministic collision checks and a bounded timeout; it is not a general pathfinder through complex interiors. No automatic scaffolding is supplied for disconnected components. The scheduler rejects them before mutation.
- Vanilla blocks only. Containers, signs, beds and other block entities, fluids, waterlogging, and disruptive blocks are unsupported. Doors, slabs, stairs, and directional placement use vanilla prediction but have not received end-to-end gameplay validation. Upper/lower multiblock positions must both be present in the plan if their changes are to be journaled.
- Preview shows at most 6,000 planned non-air positions. Large plans can have an incomplete diagram. Exact inspection still covers every planned task. Progress counts confirmed planned positions, including positions that already matched; it is not an invented animation. Throughput estimates in the bridge are based on observed confirmations.
- Persistence currently writes JSON snapshots; very large jobs can cause significant companion disk traffic. Secrets are excluded, but saved prompts/plans are ordinary local files.
- This is not a one-JAR, zero-configuration product: the requested real client requires a second authenticated account and instance; local AI requires Ollama running on that same PC; no provider credential is needed.

## Troubleshooting

**Incompatible Java:** Minecraft 26.3 and source builds require Java 25. For a source build set `JAVA_HOME` to a JDK 25 directory. Java 21 cannot compile this project.

**Companion unavailable:** inspect the Minecraft instance's `config/lunabuilder/logs` and confirm port 28763 is free. Startup errors appear in the LunaBuilder screen. Stop any old manually started companion once before launching the new mod. Do not confuse it with the Minecraft port shown in the host session. If you change `controlPort`, add `-Dlunabuilder.controlPort=YOUR_PORT` to both Minecraft instances' JVM arguments. Restart the host session after a companion restart; pressing Start local builder can reconnect after an error.

**Bot login fails:** use two separately entitled accounts, the correct Fabric 26.3 profile, separate game directories, and a current launcher sign-in. Read the actual Minecraft disconnect reason. This package does not support offline identity impersonation or public servers.

**No site:** stand near empty, flat ground with enough loaded area, choose Small, or move the preview. Inventory-containing blocks and liquids are protected. Terrain prep does not enable bulldozing existing builds.

**Placement fails:** read Preview's error. Unsupported states, missing supports, obstructions, stale world state, and unloaded chunks stop the job rather than pretending progress. The small offline fixture is the intended first local smoke test.

**Ollama unavailable:** verify your installed app and `ollama list` work. Automatic startup reuses a running app or launches the installed service. If its executable is in a nonstandard folder, set `LUNA_OLLAMA_EXECUTABLE` before launching Minecraft. Cloud models and embedding-only models are deliberately excluded. If a selected model was removed, clear the model field or choose another installed chat model. If generation runs out of memory, use a smaller installed model; model quality and hardware capacity cannot be verified from the cloud.

**Optional remote provider fails:** 401 means check your Hugging Face token; 402 inference credits/billing; 403 Inference Providers token permission or model access; 404 endpoint/model availability; 429 rate limit. Invalid JSON receives bounded repair attempts. Provider-reported usage is retained in the job. No token costs are invented. Endpoints must be HTTPS, except an explicitly configured localhost service.

## Source build and tests

```powershell
.\gradlew.bat clean build exportRegistry
Set-Location '.\companion'
node --test 'test/*.test.mjs'
```

The Gradle wrapper pins 9.8.2 and verifies its distribution SHA-256. The source ZIP has no Minecraft binaries or secret configuration. The companion has no third-party npm runtime dependencies. `exportRegistry` bootstraps actual 26.3 registries without a graphics window; the exported fixture is also included for standalone compiler tests. See `COMPATIBILITY.md` and `VALIDATION.md`.

`gradlew runtimeSmoke` separately exercises embedded runtime extraction, a real companion process, two client leases, path names containing spaces, and last-client shutdown without a Minecraft window. Source builds automatically fetch the pinned Windows Node runtime if missing, validate its official SHA-256, and embed its license. The source JAR excludes the binary; the distributable mod JAR includes it.
