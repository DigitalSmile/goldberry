# The 8K picture cap: the guide's piece

Parked until the release, as `docs/snapshot/README.md` says. One piece; the
record is ADR-0658. It says what is, and cites no record.

## `book/src/components/media.md`, `### Bringing a codec`, after its first paragraph

> Nothing larger than 8K is decoded: no side longer than 8192 and no more
> pixels than 8192 × 4352, the largest picture AV1 and VP9 define. A source
> whose video states more, or whose decoder hands over more, fails with
> `InvalidData` naming the size. `PictureLimit` holds the two numbers, and a
> provider an application brings can hold its decoder to them too.
