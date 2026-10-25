# HDR video: the guide's piece

Parked until the release, as `docs/snapshot/README.md` says. One piece; the
record is ADR-0659. It says what is, and cites no record.

## `book/src/components/media.md`, `### A player`, after the paragraph on pictures

> **HDR and wide gamut.** A picture in HDR (PQ, as HDR10 is, or HLG) or in
> BT.2020's or P3's primaries is tone-mapped to the window's SDR as it is
> drawn, on the GPU and on the CPU alike: its light from the stream's own
> transfer function, its colours into BT.709's, its highlights rolled off from
> the brightness it was mastered to (1000 nits when it does not say) down to
> SDR's white, with hue kept. What a stream says of its colour comes from the
> codec and the container both; `VideoPlanes.colour()` hands it to a view that
> converts planes itself, and `ColorDescription` is what a decoder an
> application brings reports.
