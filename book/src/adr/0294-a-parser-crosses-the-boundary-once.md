# 294. A parser crosses the boundary once

Date: 2026-09-13

## Status

Accepted. Opens `docs/gaps.md` G8's Markdown half; departs from
[ADR-0190](0190-a-content-module-brings-its-own-natives.md) in one measured way.
[ADR-0295](0295-a-document-is-a-value-and-a-paragraph-is-a-row-of-words.md) is
the other half: what happens to the events once they are here.

## Context

G8 asks for *"`goldberry-html`: Markdown through md4c"*, and names two consumers
in brd: a note's live preview, and `GET /docs/{id}/body.html`. The entry's own
argument for why it is the toolkit's is that *"a Markdown parser vendored into
brd would be a second text stack"*.

Two questions had to be answered before any of it could be written, and the
second one is the interesting one.

**Where does md4c's C get compiled?** ADR-0190's rule is that a content module
brings its own natives: its own CMake superbuild, its own classifier jars, its
own `THIRD-PARTY-NOTICES`, quarantined so that `goldberry-core` stays lean and
licence-flat. That rule was written with litehtml, PDFium and libVLC in view —
engines measured in megabytes, some with attribution or copyleft obligations.

md4c is one C file. 7,000 lines, MIT, no dependencies, and the object code is
tens of kilobytes beside `libgoldberry`'s twenty-odd megabytes. Standing up a
second native artifact for it means a second superbuild, four classifier jars,
four CI legs, a publishing story and a second library for `NativeLibrary` to
find — *before one line of Markdown renders*.

**How is a SAX parser bound?** md4c calls back for every block, every span and
every run of text. A thousand-word note is several thousand callbacks, each of
which would be an FFM upcall, and six of its seven callbacks hand over a
`void*` into one of seven `MD_BLOCK_*_DETAIL` structs — so the obvious binding
costs five upcall stubs *and* puts seven struct layouts in the table ADR-0010
maintains. A wrong offset in `MD_BLOCK_H_DETAIL` is a heading at level 218.

ADR-0190's own first rule is that **the hot path never crosses FFM**. It says so
about litehtml's thousands of draw calls. It is the same sentence about md4c's
thousands of events.

## Decision

### md4c is compiled into `libgoldberry`

One `FetchContent_Declare` pinned in `gradle/libs.versions.toml` like every other
upstream (ADR-0030, ADR-0035), with `SOURCE_SUBDIR` pointing at a directory that
does not exist — asmjit's trick — so md4c's own CMake project, which builds
`md4c-html`, the `md2html` executable and two pkg-config files, never runs. What
the build takes is two translation units, added to the shim's target:

```cmake
target_sources(goldberry PRIVATE
    "${md4c_SOURCE_DIR}/src/md4c.c"
    "${md4c_SOURCE_DIR}/src/entity.c")
```

This is a **departure from ADR-0190 and it is deliberate**. The quarantine exists
for heavy natives and for licence obligations; md4c is neither. MIT is
notice-only, which `THIRD-PARTY-NOTICES.md` already carries five of, and the
payload does not move the artifact's size. A second native artifact would cost
more than the thing it isolates.

What is **not** relaxed is the dependency direction. `:core` and `:widgets` do
not know Markdown exists; `:natives` exports md4c's wrapper to
`io.github.digitalsmile.goldberry.html` and to nobody else, which is ADR-0280's
seal with a second name on it:

```java
exports io.github.digitalsmile.goldberry.natives.md4c to
        io.github.digitalsmile.goldberry.html;
```

litehtml, when it comes, still gets a library of its own. It is C++, it needs a
native `document_container` because FFM cannot implement a virtual class, and it
is megabytes rather than kilobytes — every clause of ADR-0190's argument applies
to it and none of them applies here.

### The event stream is encoded natively and read once

`goldberry_shim.c` implements md4c's five callbacks in C and encodes what they
report into one growable buffer. Java makes **one downcall per document**:

```c
void*    goldberry_md_parse(const char* text, uint32_t size, uint32_t flags);
const void* goldberry_md_data(void* handle);
uint32_t goldberry_md_size(void* handle);
void     goldberry_md_free(void* handle);
int      goldberry_md_entity(const char* name, uint32_t size, uint32_t* out);
```

Five symbols for a whole parser, and **none of them is md4c's own**. Zero upcall
stubs. Zero detail structs in the layout table — every one of them is read in C,
by the compiler that built the library, and arrives in Java as three integers in
a record that has no layout to get wrong.

The wire format is a 20-byte little-endian header per record and then its text,
documented in two places that must agree: the comment above the encoder in
`goldberry_shim.c`, and `MarkdownStream`, which is the decoder. Attributes — a
link's href, a fence's info string — are emitted as their own records *before*
the record they belong to, because md4c reports an attribute as a string broken
into substrings with a text type each, and `[a](x?p=1&amp;q=2)` is exactly why
that matters: flatten it first and an HTML renderer emits `&amp;amp;`.

`goldberry_md_entity` is the fifth symbol and the one that is not about the
parse. md4c ships the HTML5 named-entity table — 2,125 names — and a copy of it
in Java would be a copy that drifts, which is ADR-0010's argument about struct
offsets applied to data.

### The enumerators are verified like every other library's

Every `MD_BLOCKTYPE`, `MD_SPANTYPE`, `MD_TEXTTYPE`, `MD_ALIGN` and `MD_FLAG_*`
the bindings hard-code is a `GB_CONSTANT` row in the layout table and a
`Md4cEnum` on the Java side, so `LayoutVerificationTest` compares them against
what the C compiler computed for the library actually loaded. md4c has inserted
enumerators into the **middle** of two of these enums between releases, and a
stream decoded against a shifted value does not crash: it renders a heading as a
block quote.

The ABI version is 9.

## Consequences

- **`libgoldberry` now contains a Markdown parser**, which is a sentence worth
  being uncomfortable with. The mitigation is the module graph rather than the
  binary: no module except `:html` can name a type that reaches it, and
  `ExportedSurfaceTest` fails if that changes.
- **A parse is one crossing and one allocation**, freed before `Md4c.parse`
  returns. Nothing in `:html` owns native memory, which is why a `Document` is an
  ordinary value that can be cached, compared and held across frames.
- **The wire format is a contract between two files in different languages.**
  That is the cost of the decision, and it is paid by `Md4cTest`, which drives
  the real encoder and asserts on what the real decoder produced — a hand-written
  buffer would test the decoder against itself.
- **Two `THIRD-PARTY-NOTICES` entries rather than a second notices file**: md4c
  and its entity table, both MIT, beside SDL3, Blend2D, Yoga and HarfBuzz.
- **A document larger than 4 GB cannot be parsed**, since the buffer's length
  crosses as a `uint32_t`. A note is not 4 GB.
- **ADR-0190's rule is now "quarantine what is heavy or encumbered"** rather than
  "quarantine everything optional". A future `goldberry-code` will face the same
  question for Tree-sitter, which is small but carries grammars — and it should
  be answered the same way: by measuring, and writing down which clause applies.

## Alternatives considered

- **`:natives-html` producing `libgoldberry-html`, as ADR-0190 prescribes.** The
  faithful reading, and it was rejected on cost: four CI legs and a second
  loader for 7,000 lines of C. If md4c had been PDFium this would have been the
  answer, and for litehtml it still is.
- **Five upcall stubs, the ordinary binding.** What every other library here
  gets. It is the shape ADR-0190's own first rule forbids, and it would have
  added seven struct layouts to the table — the largest single addition since
  SDL's event union — for a parser whose output is a stream of small values.
- **One upcall with a flattened signature**, keeping the parse in C but reporting
  each event as it happens. Fewer struct layouts, same thousands of crossings,
  and it makes the Java side a state machine driven from C: harder to test than a
  list, and impossible to hand to a second consumer.
- **md4c's own `md4c-html.c`, straight to HTML.** It would have answered brd's
  `body.html` with no model at all, and nothing else: no preview, no outline, no
  word count, and two renderers to keep in agreement. ADR-0295 is that argument.
- **A CommonMark parser in Java.** No native dependency, no wire format, no ABI
  bump — and a second implementation of a specification with 600 test cases,
  maintained by this project for ever. G8 named md4c for a reason.
