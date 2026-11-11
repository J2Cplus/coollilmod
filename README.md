# LunaBuilder — Java-only security-review build

**Previous Windows ZIP and Node-containing JAR builds are withdrawn after Defender reported `Trojan:Script/Wacatac.B!ml`. Do not restore those files or add antivirus exclusions.** Earlier copies may remain in Git history; they are not endorsed.

The replacement is `LunaBuilder-Java-review.jar`, version `1.1.0-review`. This is a **review build**, not a Defender-cleared or gameplay-verified release. Read [SECURITY-REVIEW.md](SECURITY-REVIEW.md) before using it. Scan the replacement with Windows Defender before loading it. If Defender flags it, leave it blocked.

## What changed

The companion was rewritten in Java and runs inside the Minecraft JVM. The mod no longer bundles Node, JavaScript, PowerShell, CMD helpers, native executables, or a runtime downloader. There is no subprocess launcher or script engine in the application's bytecode. Fabric API remains bundled using Fabric's normal nested-JAR mechanism.

The backend accepts loopback HTTP only. Both control and model requests bypass proxies and refuse redirects. Only local Ollama endpoints are allowed: there is no Hugging Face/OpenAI fallback and no API key. AI responses are bounded JSON data compiled into a block plan, never executable commands or code.

## Install and run — after Defender clears the file

1. Use Minecraft Java **26.3**, Fabric Loader **0.20.1**, Java **25**. Replace old LunaBuilder JARs with the one review JAR; do not keep both versions.
2. Your installed **Ollama app must already be running**, with a local chat model. The mod deliberately does not launch external programs or download models. Installed local models are discovered when generating; cloud and embedding models are excluded.
3. Start the host Minecraft instance first and keep it open. The Java backend starts inside that instance on `127.0.0.1:28763`; no standalone companion process is installed.
4. In your own creative singleplayer world, press **B**. **Start local builder** explicitly publishes/uses the integrated LAN session. Vanilla LAN may also listen on other interfaces; bot/control connections use loopback.
5. The visible builder still needs a separate authenticated 26.3 Fabric instance and account using this same mod. From its title screen, click LunaBuilder and enter the host's pairing code. Authentication is not bypassed.
6. Begin in a disposable flat creative world with the clearly labeled **Offline demo**. Review the footprint, then Start. The fixture has 332 planned positions; it is not AI generation. Pause, Resume, Cancel, and conditional Undo remain available.

Settings permits only a local Ollama `/api/chat` endpoint and an installed local model name. Default endpoint: `http://127.0.0.1:11434/api/chat`. No token is required. If a prior external/Node companion occupies the port, stop it before using this build. The backend refuses to reuse it.

## Behavior and limits

The native screen, actual Fabric-client builder controller, registry/terrain queries and scoped interaction gate are retained. The Java backend includes the plan compiler, support scheduler, local Ollama brief/design/repair pipeline, preflight, job controls, saved plans, world identity, confirmations, final verification, journals and conditional undo. External launcher execution was removed; open the authenticated builder profile manually.

Plans support fill, floor, wall, hollow box, pillar, stairs, connected roof, path and explicit placements. Validation checks vanilla registry IDs/state values, dependencies, dimensions, operation/expansion limits, overlaps, dangerous blocks and unsupported block entities. Semantic labels and IDs are length-bounded to prevent oversized expansion.

The backend is owned by the Minecraft instance that started it. Closing that instance ends the service; start the host first. Settings/jobs are under `config/lunabuilder-java`, separate from old Node data. Old jobs are not automatically migrated. Do not copy untrusted journal files into that folder. Closing the host/ending its world invalidates sessions and stops activity.

Site selection remains conservative and flat-ground focused. Flight navigation is simple; block entities, fluids and comprehensive terrain leveling remain unsupported. Windows UI, authenticated bot joining, actual movement/placement and real model quality are **not verified** in this Linux environment. Headless tests use synthetic world observations and model responses, and are not gameplay tests.

## Build and verify

```powershell
.\gradlew.bat clean build javaBackendCheck runtimeSmoke singleJarCheck
python .\tools\audit_artifact.py .\build\libs\lunabuilder-1.1.0-review.jar
```

The Gradle wrapper/dependencies are pinned; Fabric API is included automatically. No Node installer or Node binary is downloaded by the replacement build. The official Gradle build wrappers are source-build tooling, not payloads inside the mod JAR.

The Java regression check passed 50 assertions against actual 26.3 registry data plus local HTTP fixtures. Fabric Loader resolved 49 mod/builtin IDs from exactly one external mod JAR. These checks do not establish antivirus safety. The separate ClamAV scan and exact artifact inventory are documented in SECURITY-REVIEW.md.
