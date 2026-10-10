# A property nothing reads — working notes, 2026-10-10

Answers the `TODO.md` entry **The inflater accepts a property nothing reads**
(under *The catalog: specified and unbuilt*). The record is ADR-0587, parked in
[`snapshot/adr/`](snapshot/adr/0587-a-property-nothing-reads-is-refused-as-an-unknown-node-is.md)
until the release, with the guide text in
[`snapshot/guide-markup-unread-properties.md`](snapshot/guide-markup-unread-properties.md).

## Status

| Piece | Status |
|---|---|
| Reads noted per node during an inflation (`PropertyReads`, a `ScopedValue`) | **done** |
| `UnreadPolicy` (`REFUSE` by default, `WARN`, `IGNORE`) on `KdlInflater` | **done** |
| `UnreadProperty`: node, key, value, what the node did read, position | **done** |
| Unit tests, `core/src/test/java/dev/goldberry/kdl/UnreadPropertyTest.java` (15) | **done** |
| Sweep of the test suites with `REFUSE` on: six findings, all real | **done**, see below |
| Sweep of the 96 `kdl` samples in doc comments | **done**: one dead property (`Masonry`'s `gap=12`) |
| `docs/core-widgets.md` samples (`row gap=8`, `masonry … gap=16`) | **done** |
| ADR-0587 and the guide text | **parked** in `docs/snapshot/`, manifest updated |
| `book/src/TODO.md` entry moves to *Answered* | **at release**, per the manifest |

## What the sweep found

`:core`, `:widgets`, `:html`, `:media` and `:example` ran 3,092 tests with
`REFUSE` as the default. Six failed, all in `:widgets`:

| Test | Property | What was done |
|---|---|---|
| `RadioTest` *markup cannot mark an option selected* | `radio selected=` | the test asserted the drop; it asserts the refusal now |
| `SegmentedTest` *markup cannot mark a segment selected* | `option selected=` | likewise |
| `StepsTest` *a document cannot write a step's state* | `step current=` | likewise |
| `CheckboxTest` *`indeterminate=#true` wins over `checked=#true`* | `checked=` beside `indeterminate=` | `Checkbox.inflate` reads both before deciding; the precedence is unchanged |
| `WidgetParityTest` *styleable* `[point]`, `[series]` | `id=`, `class=` on chart data | the parity test asserts the refusal, then checks the type selector without them |

`ShowcaseDocumentsTest` (every showcase `.kdl`) and `BookMarkupTest` (the
guide's 109 samples) were clean.

## What each piece touched

- `core/src/main/java/dev/goldberry/kdl/`: `PropertyReads` (new, package-private),
  `UnreadPolicy` and `UnreadProperty` (new, public), `KdlNode` (the accessors note
  a read; `properties()` is a noting view during an inflation), `KdlInflater` (the
  policy, and `checked`, which wraps both `inflate` and `inflateAll`).
- `widgets/…/controls/checkbox/Checkbox.java`: reads `checked` unconditionally.
- `widgets/…/panel/masonry/Masonry.java`: the javadoc sample drops `gap=12`.
- Five tests in `:widgets`, listed above.
- `docs/core-widgets.md`, `docs/book.md`, `docs/snapshot/`.

## Not done, on purpose

- **Arguments are not checked.** A second positional value nothing reads is a
  different question from a misspelt attribute. See the ADR's consequences.
- **No per-widget schema.** What a widget understands is still only the calls
  its factory makes. A schema would let an editor complete attribute names, and
  it would be a second list to keep in step with the factories.
