# Java-only replacement validation

Version: `1.1.0-review`. This is not the old withdrawn JAR under a different name: its backend was rewritten in Java and the bundled execution chain removed.

Verified:

- Clean compile against actual Minecraft 26.3, Loader 0.20.1, Fabric API 0.166.0+26.3, Loom 1.19.4, Gradle 9.8.2 and Java 25.
- 50 Java compiler/backend/security assertions: real registry validation, fixture parity/determinism, coordinate/state transforms, cycles and bounds, unsafe blocks/block entities, unknown executable primitives, oversized labels, local-only endpoint validation, no API credentials, Ollama brief then JSON design with reported usage, separate host/bot auth, preview approval, support/preflight, server-observation progress, pause/resume reinspection, cancellation, persistence, full completion/final verification, conditional undo preserving a later edit, external-launch rejection, Origin rejection, stale-session invalidation and world isolation.
- These model/world tests use HTTP fixtures and synthetic observations; no live gameplay is claimed.
- In-process service lifecycle check: startup, reuse, nonowner close preserves owner service, owner shutdown stops the backend; no helper process is launched.
- Actual Fabric Loader scanner/resolver discovers 49 mod/builtin IDs from one external mod JAR, including nested Fabric API.
- Recursive artifact audit checked 3,029 entries, 12 application classes, no PE/executable/script payloads, and no subprocess/script-engine references in application bytecode.
- ClamAV 1.5.4 with freshly downloaded official databases scanned the exact replacement JAR and source snapshot: 31 files, 0 detections. The older delivery formats/source were scanned separately: 73 files, 0 detections.

Not verified:

- Windows Defender scan of the replacement. Linux ClamAV results cannot clear Defender's Wacatac detection or prove the old build was a false positive.
- Windows Minecraft startup/UI and Java runtime module availability, authenticated two-account bot joining, actual placements/undo, real terrain or local model quality on the user's PC.

The old downloads/releases remain withdrawn. Do not allow or exclude them. The new artifact is available for independent review and Defender scanning, not as a promise that the unresolved detection is fixed.
