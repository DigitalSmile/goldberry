# ADR-0754: The native library check closes its SVG

- **Status:** Accepted
- **Date:** 2026-11-28
- **Relates to:** ADR-0341, ADR-0548, ADR-0750

## Decision

The Qodana run for `b7a89c5e` failed its quality gate. A local scan with
Qodana for JVM 2026.1.4 reproduced one new High finding. The Showcase library
check parsed a test SVG and left cleanup to the cleaner. The check now closes
the SVG in a try-with-resources block and verifies that it has a size. Use
space-separated arguments for the Qodana action and the Node 24 artifact action.
[Qodana run](https://github.com/DigitalSmile/goldberry/actions/runs/46083635080)

CodeQL's public annotations flagged the v3 action and the missing push trigger.
The workflow now uses v4 and scans pushes to the default branch so fixes can
reach the Security tab between scheduled runs.
[CodeQL job](https://github.com/DigitalSmile/goldberry/actions/runs/45196490722/job/137768202159)

The local CodeQL suite flagged the default single-instance claim directory in
shared temporary storage. The default now uses `XDG_RUNTIME_DIR` or
`~/.goldberry/instances`. Unix sockets for long paths use a short, owner-only
directory on POSIX. Windows uses the account's temporary directory. Failed
claims close their channels. The lock and socket are transient, so an
application using the previous fallback directory should be restarted when
upgrading.

The same scan led to overflow-safe bounds checks for BVH input and Matroska
metadata, a `long` loop counter for synthetic audio, and fixes to process and
stream ownership in tests. The CMake export test uses the absolute executable
resolved by the Gradle build.

## Verification

The full Qodana scan passed locally with the checked-in baseline. Its 78 new
findings were all Moderate. They comprise 76 unused declarations and two loop
style suggestions. Qodana's incremental scan of the final permission and bounds
changes reported zero findings.

GitHub requires authenticated access to the alert list. The repository's
configured Java suite was run locally with CodeQL 2.27.6 to review the
findings. The full rerun removed 11 findings. A follow-up scan of the affected
modules with the two relevant queries cleared the remaining three findings at
changed sites. The two MD5 findings refer to media test fixtures that compare
against FFmpeg frame digests. FLAC callback writes and the frame queue's
condition wait are also reported conservatively by the scanner.
