# ADR-0032: Shaping is UTF-16 in, glyphs out

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §6, [ADR-0010](0010-hand-written-ffm-bindings.md), [ADR-0017](0017-proving-the-struct-by-value-upcall.md), [ADR-0029](0029-yogas-node-api-and-who-owns-a-node.md), [ADR-0031](0031-blend2d-and-the-borrowed-buffer.md)

## Context

Yoga's measure callback is bound and driven by real layout passes
([ADR-0029](0029-yogas-node-api-and-who-owns-a-node.md)), and Blend2D rasterizes
frames ([ADR-0031](0031-blend2d-and-the-borrowed-buffer.md)). What sits between
them is missing: something that turns a string and a width into a set of glyphs
and positions. That is HarfBuzz, and it is the last binding M1's vertical slice
needs.

Three things shape the decision.

**HarfBuzz speaks UTF-16 natively.** `hb_buffer_add_utf16` exists alongside the
UTF-8 and UTF-32 entry points, and a Java `String` already *is* UTF-16. Choosing
any other entry point means transcoding on the hottest path in the toolkit — a
paragraph is reshaped at every width Yoga proposes — and, worse, means the
cluster indices HarfBuzz reports index a re-encoded copy rather than the string
the application handed over.

**Shaping returns two parallel arrays, read in place.** `hb_buffer_get_glyph_infos`
and `hb_buffer_get_glyph_positions` hand back pointers into memory the buffer
owns. Both structs carry members HarfBuzz marks private, which are part of the
stride and must never be read.

**HarfBuzz reports almost nothing.** Most of its functions return void, and the
ones that can fail return an object that is *empty* rather than null:
`hb_face_create` over nonsense bytes gives a face with no glyphs, not an error.

And one practical constraint: **Goldberry bundles no font yet.** `licenses/` is
still placeholders, so there is nothing in the repository to shape with, and
depending on whatever fonts happen to be installed would make the tests pass or
fail according to the machine.

## Decision

**UTF-16 in.** `ShapingBuffer.addText` takes a `CharSequence` and hands the code
units over unchanged. The cluster values that come back are therefore indices
into the caller's own string, which is what makes a caret position and a
selection range meaningful without a mapping table.

**The full text is offered as context; a range of it is shaped.**
`addText(text, start, end)` passes the whole string with an item range, because
that is the distinction `hb_buffer_add_utf16` draws and it is not cosmetic: in
Arabic a letter's form depends on its neighbours, so shaping a fragment without
the characters either side of it is visibly wrong.

**Glyphs come back as parallel `int[]`s, copied.** `GlyphRun` holds six arrays
rather than an array of glyph objects: a paragraph is thousands of glyphs
reshaped several times per layout pass, and one object per glyph would be an
allocation storm exactly where it hurts. They are copies because the buffer
reuses its memory — holding the native arrays across a `reset` would read the
next run's glyphs.

**The buffer is a reusable object.** `ShapingBuffer` is created once and
`reset()` between runs, so the measure callback does not allocate and free
native memory on every width Yoga tries.

**`ShapedFont` owns all three HarfBuzz objects** — blob, face, font — because
nothing above this module has a reason to hold one without the others. Font
bytes are always copied (`HB_MEMORY_MODE_DUPLICATE`): the alternative is
promising that a Java array outlives the face, which nothing here can promise.

**`ShapedFont.empty()` is part of the public surface, not a test fixture.** It
wraps HarfBuzz's immortal empty face, and it is what makes the shaping tests run
on a machine with no fonts installed — glyph counts, cluster mapping, direction
handling and the UTF-16 crossing are all exercised by it. It is tracked as
*borrowed*, because that face is a singleton HarfBuzz keeps forever and
destroying it would decrement a reference count that was never ours.

**The struct strides are registered in the layout table** like every other
hand-written layout ([ADR-0010](0010-hand-written-ffm-bindings.md)), with the
private members modelled as padding. This is where that discipline earns the
most: a stride wrong by four bytes gives a perfect first glyph and garbage for
every one after it, which on short text looks like nothing at all.

## Alternatives considered

**UTF-8, with the string encoded on the way in.** The entry point most examples
use. It costs an encode per shaping call, and it makes every cluster index refer
to a byte offset in a temporary array — so mapping a click back to a character
would need the encoding kept alive alongside the glyphs.

**Return an array of glyph records.** `List<Glyph>` reads far better at the call
site. It is also one allocation per glyph on the path that runs most often, and
the first thing a profiler would point at.

**Let callers hold HarfBuzz's arrays directly**, as segments, and avoid the
copy. Faster, and it makes the lifetime of the result depend on the buffer not
being touched — a rule that would be broken by the first person to reuse a
buffer, which is the thing buffers exist for.

**A font object per shaping call.** Simpler lifetimes. Creating an
`hb_font_t` parses tables, so doing it per call would put font loading inside
the measure callback.

**Skip the shaping tests until a font is bundled.** The honest-looking option,
and it would have left the UTF-16 crossing, the array strides and the cluster
mapping unchecked for as long as the licence work takes. The empty face checks
all of them.

**Bind `hb_shape_full` instead of `hb_shape`.** It reports whether the requested
shaper list was used, which is a real signal — but the shaper list is not
something Goldberry chooses yet, so there is nothing to report about.

## Consequences

**The M1 vertical slice has all three of its pieces bound.** Yoga measures,
HarfBuzz shapes, Blend2D draws. What is missing is the thing that joins them: a
paragraph type whose measure function shapes at the width Yoga proposes and
reports the height, and a paint step that turns a `GlyphRun` into Blend2D glyph
runs. Neither exists yet.

**The ABI version is now 7**, and the export list has 136 entries.

**HarfBuzz needed the same visibility fix Blend2D did**, for a blunter reason:
`HB_EXTERN` is defined as bare `extern`, with no visibility attribute at all, so
the superbuild's global `hidden` preset applied to every HarfBuzz function. All
24 symbols linked in and arrived local. The fix in
[ADR-0031](0031-blend2d-and-the-borrowed-buffer.md) is now a loop over both
targets — which is the right shape, because the next static upstream will
probably need it too.

**Shaping itself is not tested.** Ligatures, kerning, contextual forms and
right-to-left glyph reordering all need a real font, and the empty face's
fallback path does not reorder — so a test asserting that it did would be
asserting a property of the fallback rather than of shaping. There is a test
that says so out loud rather than leaving the absence silent. This lands the
moment a font is bundled, which the licence work
([ADR-0015](0015-licensing-and-third-party-disclosure.md)) gates.

**"The font failed to load" and "the text is all boxes" look identical from
Java.** HarfBuzz answers unparseable bytes with an empty face rather than an
error, and there is no way to tell that from a font that genuinely lacks the
characters. A test pins the behaviour down so it is at least documented; a real
font loader will need its own validation before it gets here.

**Deliberately not bound.** Font features (`hb_feature_t`) — the CSS layer has no
`font-feature-settings` to compile into them yet; variable-font axes; the
callback-based font funcs, which is how a font's metrics can be supplied by
something other than the file; `hb_shape_full` and shaper selection; and the
face-enumeration API for collections beyond an index.
