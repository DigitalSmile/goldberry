# Goldberry

**A fast and modern UI toolkit for Java.** Part of [Scarlet Macaw OS](https://scarletmacaw.org),
usable standalone on Linux, Windows, and macOS from day 1.

This book is the project's decision log and developer documentation. It is not the
design document — that lives at [`docs/ARCHITECTURE.md`](https://github.com/digitalsmile/goldberry/blob/main/docs/ARCHITECTURE.md)
in the repository root and describes the whole system as intended. The book records
*why* the design is the way it is, one decision at a time, and picks up the
questions the design document leaves open.

## Where things are

| Document | Answers |
|---|---|
| `docs/ARCHITECTURE.md` | What the system is, layer by layer |
| `book/src/adr/` | Why each significant choice was made, and what it costs |
| `README.md` | How to build it, and what currently works |

## Status

Pre-M0. The Gradle multi-module skeleton, the JPMS module graph, and this book
exist; nothing renders yet. The milestone ladder is in `docs/ARCHITECTURE.md` §16,
and the README tracks progress against it.

## Reading order

If you are new to the codebase, read `docs/ARCHITECTURE.md` §1–§5 first — positioning,
the layer map, the native core, the backend SPI, and the rendering pipeline. Then
read [ADR-0002](adr/0002-cpu-rasterization-with-blend2d.md) through
[ADR-0005](adr/0005-css-subset-and-kdl-as-the-contracts.md), which cover the four
choices that shape everything else: CPU rasterization, SDL3-only windowing, the
three-tree widget model, and CSS + KDL as the public contracts.
