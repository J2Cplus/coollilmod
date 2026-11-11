# Security review — unresolved Defender report

## Report and containment

Defender reported **Trojan:Script/Wacatac.B!ml** for both previous delivery formats. No exact triggering member or Microsoft analysis verdict is available. The user reported downloading only; the report alone does not show execution or infection. Personal file paths/process metadata are intentionally omitted here.

Old binary download paths were removed from the current branch and old releases made drafts. Their bytes remain in Git history and local forensic copies; those copies are not endorsed. Re-sharing the historical JAR after withdrawal was a mistake. No allow/exclusion instruction is part of this remediation.

## What was found in the old design

The old all-in-one JAR embedded `node.exe`, seven companion `.mjs` modules, and Java code that extracted and launched the backend. The old Windows ZIP additionally contained PowerShell/CMD helpers, including per-process `ExecutionPolicy Bypass` and hidden-window launch options. These were intended as startup automation; that intent and a passing build do not establish that Defender's finding is false.

The bundled Node executable SHA-256 matched both the pinned and freshly retrieved official Node manifest. This verifies that one component's provenance, not the whole bundle. No Windows Defender scan or multi-engine clearance was performed before the old build was distributed.

## Remediation implemented

- Replaced the Node backend with an in-process Java HTTP service.
- Removed bundled Node/native executables, runtime download/extraction, JavaScript/Powershell/CMD helper payloads, process launchers and script engines from the artifact.
- Removed those obsolete helper sources/launchers from the current source tree; preserved forensic copies outside the release build.
- Removed remote-provider/token integration and external Minecraft launcher execution.
- Enforced loopback-only HTTP, proxy bypass, no redirects, host/bot credential separation, Origin rejection, request/response limits, scoped persistence paths, bounded schema expansion, dependency nesting/label limits, and data-only AI compilation.
- Preserved a functional planning/scheduling/persistence/undo pipeline with 50 Java regression assertions. This is not an attempt to rename, hide, encrypt or obfuscate the old payload to get around antivirus.

## Antivirus scans

ClamAV **1.5.4**, downloaded from the official Cisco-Talos release. Freshclam downloaded/tested official main.cvd 63, daily.cvd 28179 and bytecode.cvd 339 using the official CVD trust certificates. Scanner reported 3,628,258 known-virus signatures.

| Scope | Files | Data scanned | Result |
|---|---:|---:|---|
| Withdrawn ZIP/JAR/source delivery formats and source snapshot | 73 | 337.36 MiB | 0 detections |
| Exact Java-only replacement JAR and replacement source snapshot | 31 | 24.99 MiB | 0 detections |

Archive/PE scanning was enabled recursively with limits raised to 512 MiB/file, 2 GiB/scan and 32 nesting levels, with limit exceedances configured as alerts. No infected-file or limit alerts were reported. See the scanner logs in `evidence/security-review/`.

**These results do not clear Windows Defender, prove a false positive, or certify safety.** Defender is not available in this Linux executor. The exact triggering script/signature remains unestablished. No claim that Wacatac is resolved is made.

## Exact replacement artifact

- Version: `1.1.0-review`
- Size: 1,872,077 bytes
- SHA-256: `6ed80f851005ed0dc58290c707fe3167f1113649da3ea19b46da967569e8becf`
- Inventory: 3,029 recursively checked archive entries; 12 application classes.
- No bundled PE/executable/script payloads; no subprocess/script-engine references in application bytecode. The inventory check is not an antivirus engine.
- Clean build, actual Fabric Loader dependency resolution, in-process lifecycle checks and 50 compiler/backend/protocol/security assertions pass.

The replacement is a security-review artifact. Before any Minecraft execution, scan it with Defender. If Defender flags it, leave it blocked; do not add exclusions or disable protection. Gameplay and actual local-model execution remain unverified.
