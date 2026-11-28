# ADR-0755: The search test names the first stale card

- **Status:** Accepted
- **Date:** 2026-11-28
- **Relates to:** ADR-0625

## Context

The Linux x64, macOS and Windows jobs in the Snapshot run all failed
`SearchIndexTest`. The ray-traced room card changed its summary and the
Showcase still shipped the previous search entry. The equality assertion
printed two copies of the index, and GitHub truncated the annotation before
the changed card. [Snapshot run](https://github.com/DigitalSmile/goldberry/actions/runs/46083635099)

## Decision

Regenerate the shipped index from the gallery. Compare the ordered card
entries individually so a failure identifies the first mismatch and prints
both versions of that entry. The test still checks the entire gallery.

To regenerate the index after a gallery change, run the test with a built
native library available.

```sh
./gradlew :example:test --tests '*SearchIndexTest*' -Dgoldberry.golden.update=true
```

## Verification

The local reproduction named `gpu-room-card`. Regeneration updated that one
row. The Linux `:example:build` passed with update mode off and the native
library required. The build-logic tests passed in an isolated checkout.
