# Compatibility evidence

Checked during the November 9, 2026 (Pacific time) build session. No Minecraft downgrade was made.

| Component | Pinned version / evidence |
|---|---|
| Minecraft Java | **26.3**. Mojang's version manifest lists it as the latest release. The version JSON specifies `java-runtime-epsilon`, major Java **25**. Actual client/common JARs were downloaded by Loom and compiled against. Protocol number reported in minecraft-data's version list: **777**. |
| Java build runtime | Oracle JDK **25.0.5.0.3**, Linux x64. User game/source requirement: Java **25**. |
| Fabric Loader | **0.20.1**, listed stable by Fabric metadata and resolved by Gradle. |
| Fabric API | **0.166.0+26.3**, from Fabric's Maven metadata; dependency resolution and compilation succeed. The all-in-one mod nests this dependency JAR using Loom include, preserving its module metadata and license. |
| Loom | **1.19.4**, released Maven artifact. The plugin ID is `net.fabricmc.fabric-loom`. |
| Gradle | **9.8.2**, official Fabric 26.3 example wrapper version; distribution SHA-256 is pinned in the wrapper properties. |
| Names / mappings | The actual unobfuscated 26.3 named game artifacts are used directly by Loom. No old Yarn mappings or intermediary method names are guessed. |
| Companion | Node **24.22.1** Windows x64 portable runtime, official `win-x64/node.exe` hash verified against official SHASUMS256.txt. Source requires Node 24+. No npm runtime dependencies. |
| Mineflayer investigation | Mineflayer **4.39.0**, minecraft-data **3.119.0**, installed and inspected. The resolved minecraft-protocol supported list ends at **26.1**. `minecraft-data('26.3')` returns **null**, despite 26.3 appearing in its version-number list. This does not establish actual 26.3 support; Mineflayer was therefore rejected. These packages are not runtime dependencies of LunaBuilder. |
| Builder alternative | Separate ordinary **26.3 Fabric client** running this same mod. It uses actual game movement, creative inventory and block-placement APIs, plus server confirmations. Runtime login and gameplay remain unverified here. Requires a separate authenticated account; no custom offline authentication bypass. |
| AI | Updated at user request to **local Ollama**, native endpoint `http://127.0.0.1:11434/api/chat`. Model discovery uses `/api/tags` and `/api/show`; only an actual installed local completion model is selected. No API key, cloud fallback, or model download is used. Ollama requests use `stream:false`, JSON format for the design, and bounded `num_predict` / `num_ctx`. The user's PC and installed model execution cannot be reached from this cloud workspace. |

Official sources:

- Mojang manifest: https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
- The exact 26.3 version document URL is captured in the manifest entry in `evidence/mojang.json`; `evidence/26.3.json` preserves its downloaded Java/download metadata.
- Fabric game versions: https://meta.fabricmc.net/v2/versions/game
- Fabric loader versions: https://meta.fabricmc.net/v2/versions/loader
- Fabric API metadata: https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml
- Loom metadata: https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml
- Fabric 26.3 example: https://github.com/FabricMC/fabric-example-mod/tree/26.3
- Node Windows checksums: https://nodejs.org/dist/v24.22.1/SHASUMS256.txt
- Bot library metadata: https://registry.npmjs.org/mineflayer/4.39.0 and https://registry.npmjs.org/minecraft-data/3.119.0

Actual signature inspection caught changes to `GuiGraphicsExtractor`, `Screen.extractRenderState`, `KeyMapping.Category`, `Inventory.setSelectedSlot`, `Vec3.atCenterOf`, swing animation arguments, and `IntegratedServer.publishServer(MultiplayerScope, boolean, int)`. Compiled source uses these 26.3 signatures. Source inspection showed publishing with port zero records zero as the advertised port, so LunaBuilder obtains an available port first and passes that value to vanilla LAN publishing. Publishing uses `startTcpServerListener(null, port)`, so it is not loopback-only. Bot connections and the companion bridge use loopback explicitly.

The headless registry export bootstrapped **1,286 actual vanilla block definitions**. The 332-position offline pavilion compiles and schedules against them. This is stronger than a library installation check, but does not prove gameplay behavior.

Hugging Face update checked November 10, 2026 (Pacific time):

- Chat completion specification: https://huggingface.co/docs/inference-providers/tasks/chat-completion
- Live router model catalog: https://router.huggingface.co/v1/models
- Token setup: https://huggingface.co/settings/tokens
- Required token permission: Inference Providers. Hosted requests draw on account credits/billing; no promise of unlimited free inference is made.
- Default model has a live DeepInfra route with a reported 262,144-token context window in the checked catalog. Provider routing is left to Hugging Face; no provider-specific authentication is invented.

Local Ollama update: official chat and model-list documentation retrieved successfully at https://docs.ollama.com/api/chat and https://docs.ollama.com/api/tags. Native `/api/show` is used for capability checks. Tests validate requests against a real loopback HTTP fixture; actual Ollama and the user's model inventory have not been accessed. Prior Hugging Face evidence above is historical and that provider is no longer the new-install default.

Automatic startup update: the distributable mod now embeds companion source plus Node 24.22.1 Windows x64 and its upstream license. Runtime files are hash-checked before use and cached in a versioned Minecraft-instance directory. Java process startup uses an argument list (no shell) off the render thread. Global per-user/port startup locking prevents duplicate launches; per-client secret leases coordinate shared-service cleanup. Installed Ollama is started only if its local service is unavailable; no model is pulled. Windows executable discovery is implemented but was not exercised on Windows.

Single-JAR update: Fabric API 0.166.0+26.3 is now included as a nested dependency in LunaBuilder, retaining all nested modules and Apache license. Actual Fabric Loader discovery/resolution passed with only one external JAR and resolved 49 mod/builtin IDs. The companion and Node remain embedded resources. Fabric Loader and Minecraft are installed via the launcher rather than bundled game binaries.
