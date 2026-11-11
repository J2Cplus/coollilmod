# Java-only compatibility

Minecraft Java 26.3, Java 25, Fabric Loader 0.20.1, Fabric API 0.166.0+26.3, Loom 1.19.4 and Gradle 9.8.2 are pinned. The build compiles against the actual unobfuscated 26.3 dependencies; no downgrade or guessed old mappings are used. Fabric API is nested into the single mod JAR with its original metadata and license.

The native Minecraft screen/builder APIs remain the 26.3 signatures previously inspected. The companion is now standard Java using `java.net.http` and `jdk.httpserver` inside Minecraft, with Gson supplied by Minecraft. There is no Node dependency or native executable extraction. Windows gameplay and the official launcher runtime's module set still require local verification.

Ollama uses the existing local app: `/api/tags`, `/api/show` and `/api/chat`. Only loopback HTTP is supported; redirects/proxies/cloud inference are not used. The installed model is discovered rather than invented. Source evidence retains the original game/dependency metadata for review. Historical Node/Hugging Face evidence is not a current runtime requirement.

The registry export contains 1,286 actual vanilla block definitions. The 332-position offline fixture compiles/schedules against them. A real Fabric Loader metadata-only test resolves 49 IDs with one external JAR. These are dependency/data tests, not a gameplay or security certification.
