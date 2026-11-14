# ADR-0690: PDF stays parked until PDFium's provenance is decided

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** ADR-0030 (natives pinned by commit and built from source)

## Context

A messenger asked for PDF page rendering, for documents on a whiteboard and
for attachments (Tessera's G-23, here T-23). `ARCHITECTURE.md` names a
`goldberry-pdf` module on PDFium, and `docs/TODO.md` already notes the catch:
PDFium's own build wants gn and depot_tools, so the plan was to consume
pinned, checksum-verified community binaries. Every other native in the
toolkit is compiled by the superbuild from a pinned commit (ADR-0030).

## Decision

**Parked, not declined.** PDF rendering is in scope, but no `goldberry-pdf`
is built until a record decides PDFium's provenance: a prebuilt binary with
its checksum and the trust that implies, or a source build with gn in CI.
That record comes first, and this one is closed by it.

## Consequences

- Tessera keeps first-page thumbnails from its media service, as its entry
  plans.
- The question is listed in `docs/TODO.md`, where it was already.
