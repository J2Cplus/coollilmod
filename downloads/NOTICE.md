# Previous downloads withdrawn

The Node-containing Windows ZIP and JAR builds remain withdrawn after Defender reported Trojan:Script/Wacatac.B!ml. Do not run, restore or exclude those builds. They may remain in Git history.

A distinct Java-only `1.1.0-review` artifact has been rebuilt and scanned with ClamAV, not Windows Defender. It contains no Node, scripts, native executable payload or process launcher. This is a review file for independent inspection and Defender scanning, not a security clearance. See ../SECURITY-REVIEW.md. If Defender flags it, leave it blocked.
