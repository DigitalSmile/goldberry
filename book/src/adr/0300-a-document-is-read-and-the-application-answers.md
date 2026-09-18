# 300. A document is read, and the application answers

Date: 2026-09-13

## Status

Accepted. Makes both content views **interactive** — links a reader can press,
images that are drawn, task boxes that tick — and draws the line in the same place
every other record about the toolkit's edges draws it.

**One sentence below is superseded by [ADR-0399](0399-a-task-box-is-counted-by-the-parser-that-found-it.md):**
"`toggleTask` is a scan, not a parse-and-write". The one-character edit it argues
for is kept and is the part that matters; the *scan* is gone. The risk this record
named in the next paragraph — "two counters must agree: md4c's, walking the model,
and a regex, walking the source" — is exactly the bug that arrived, and the answer
was to stop having two.

Builds on [ADR-0298](0298-html-is-a-document-and-not-an-engine.md), which put a
`button.link` where an HTML anchor was and left the Markdown view's links as
colour; this finishes the job in both directions. Text selection is **not** here,
and the last section says why and what it would take —
[ADR-0301](0301-a-selection-is-geometry-the-frame-already-had.md) then built it
to that design.

## Context

Both views shipped as renderings a reader cannot touch. Three things in
particular, and a fourth that is different in kind:

1. **A Markdown link is a colour.** ADR-0295 explained why — "a word is a `text`
   widget and a widget's pointer handling is per node, so a link that spans four
   words would be four hover targets and four handlers" — and ADR-0298's
   `Words.Piece` quietly removed the obstacle: a piece can be a **widget**, so a
   link's whole run is one node. The reason stopped being true and the code did
   not notice.
2. **An image is its alt text.** ADR-0283 shipped `Image.decode` and
   `Frame.drawImage` in 2026; what was missing was a widget that draws one and an
   answer about who fetches. `docs/gaps.md` G17 said so out loud: "not built, and
   not the engine's".
3. **A task box is a picture of a check box.** `TaskMark`'s own javadoc claimed
   the model carried a source offset to edit at. It does not — the claim was
   written for an `Item.taskMarkOffset` that was never built — so the feature was
   waiting on something that did not exist.
4. **Text cannot be selected.** Which is the engine's, and is treated separately
   below.

The common shape of the first three is worth naming, because it is what makes them
one record: **each needs an answer only the application has.** What a link *does*,
where a `src` *is*, what ticking a box *means* — none of these is the toolkit's,
and each of them was being answered with "nothing" because there was no way to
ask.

## Decision

### A link is a `button.link`, in both views

`MarkdownView.onLink(Consumer<String>)` and `onWikiLink(Consumer<String>)`; in
markup, `link=` and `wikilink=`. An anchor already worked this way in
`html-view`; this is the same code path through `Words.Node`, so the full stop
after a link stays against it rather than becoming a word of its own.

**Two handlers, not one.** An href points somewhere and a `[[wiki link]]`'s target
names something in the application's own collection — which is the reason that
extension exists at all (ADR-0295). A view that routed both to one consumer would
be handing over two kinds of string and asking the application to guess.

A link with **no text** — one wrapping only an image, or an empty destination — is
folded inline instead, because a button with nothing on it has nothing to click
and nothing to read out (§13).

**Following is still the application's.** Nothing here opens a browser, resolves a
relative path or fetches anything — ADR-0291's division, at the place a reader is
most likely to expect otherwise.

### An image is drawn when the application says where it is

```java
ImageSource assets = src -> cache.get(src);          // the application's answer

var view = MarkdownView.following(model.source()).images(assets);
```

```kdl
markdown-view bind="note.source" images="app.assets"
```

- **`ImageSource` is exported** and is the only type in
  `io.github.digitalsmile.goldberry.content`: a `src` is a **string** whose meaning
  is the application's — a path relative to something only it knows, a key in a
  store, a URL nothing here may fetch (ADR-0190).
- **`Picture` is a part**, in a package that is not exported: a CSS type a
  stylesheet reaches and nothing constructs (ADR-0065).
- **A source that answers null draws the alt text**, which is what every view did
  before and what a broken `src` should still show. There is no placeholder and no
  exception — a renderer that drew a broken-image icon would be inventing content.
- In markup it is `images=`, which names an **object** through `Wiring.handle` —
  the third registry, for a thing that is neither a value nor a method (ADR-0130).

**The size is arithmetic in `Picture` rather than a measure callback**, because the
toolkit's measured leaves are text and icons and there is no general measure hook
on `Box`. So: natural size, capped by `max-width`/`max-height` **in proportion**,
and a *percentage* cap ignored rather than guessed at — resolving one needs the
container's width, which only a measure callback has. Both stylesheets write their
caps in points and say so beside the rule.

This is also what makes it cheap: an `Image` is a **value** that owns no native
handle (ADR-0283), so it can be held in a widget that is rebuilt every frame and
cached in a map by the application that owns it.

### A task box reports an ordinal, and the application rewrites one character

```java
MarkdownView.following(model.source())
        .onTask(index -> notes.setSource(Markdown.toggleTask(notes.source(), index)));
```

The round trip: the view hands over **which** box — the nth task in document order
— `Markdown.toggleTask` flips that one marker in the **source**, the application
stores it, and the preview re-parses because it is bound to the property
(ADR-0296). Nothing in the toolkit writes to anything.

**An ordinal, because the model has no offsets.** The alternatives were adding
source offsets to the model — which is a change to a public value type for one
feature, and md4c's own offsets are byte offsets into a UTF-8 buffer the model no
longer has — or handing back the `Item` and asking the application to find it.
An index is the smallest thing that identifies a box, and it is the same number on
both sides because both count *tasks*.

**`toggleTask` is a scan, not a parse-and-write.** A re-serialisation returns *a*
document with the same meaning rather than **the author's file** — it would reflow
their tables, renumber their lists and normalise their line endings because
somebody ticked a box. So one character changes and every other byte comes back as
it was. A marker inside a fenced code block is skipped, because it is a program
that contains `- [ ]`.

The risk in that decision is that two counters must agree: md4c's, walking the
model, and a regex, walking the source. `TasksTest` holds them together — it
toggles every task in a document and asserts the parser sees exactly that one box
change.

**A box nobody wired stays inert**, is not focusable, and reports
`isDisabled()` — a Tab stop that responds to nothing is a keyboard trap with extra
steps (ADR-0281's rule for `canvas`).

### Text selection is not here, and this is what it needs

The honest statement of the gap, so the next person does not rediscover it:

A selection spanning a document needs three things this toolkit does not have
arranged the right way. **Hit-testing a point to a word and an offset in it**: the
views build hundreds of `text` widgets, and a widget cannot see its children's
laid-out rectangles — only its own, through `Measured`. Making every word report
its rectangle would double the element count, which is the cost ADR-0299 has just
finished paying down. **Painting the highlight without rebuilding**: a `selected`
class per word means a rebuild of the document on every pointer move, which is
exactly the shape of frame the last record removed. **Character granularity**:
`TextGeometry` can answer inside one paragraph, and a selection across two faces is
a different question.

The design that fits is a **selection overlay**: one node above the document that
owns the anchor and focus, hit-tests against a geometry the layout pass hands it
once per layout rather than per frame, and draws the highlight itself as a painter
rather than as a class on six hundred words. That is a change to how a subtree
reports its geometry, which is the toolkit's business and not this module's —
which is why it is a separate piece of work and is recorded in `book/src/TODO.md`
rather than half-built here.

ADR-0301 is that piece of work, and it landed: what the section above describes
is what it built, with `Located` answering the first question, a painter reading
mutable state answering the second and `Paragraph.offsetAt` answering the third.

## Consequences

**Both views are the same shape again.** Every capability above exists in both,
except tasks — GitHub's task list is a Markdown extension and HTML has no
equivalent, so `html-view` has nothing to offer there.

**The showcase demonstrates the division rather than describing it.** Both screens
have a line under the preview that fills in with what the application was handed,
and ticking a box in the Markdown preview rewrites the text in the editor beside
it — which is the clearest possible statement that the toolkit reports and the
application decides.

**`MarkdownView` grew from four components to eight**, and that is a real cost. The
four-argument constructor is kept so existing callers compile, and the four new
ones are `@Nullable` with "not wired" as the default everywhere.

**`TaskMark` became a control.** It is `Handles` and `Semantics` now — a
`Role.CHECKBOX` that is disabled when inert — so it can take `Space` and `Enter`
and appear to a screen reader as what it is.

**Three claims in the documentation were wrong and are corrected**, not quietly:
ADR-0295's "nothing is clickable", `TaskMark`'s "the model says where", and
`TODO.md`'s image bullet. Each of them was true when written and had stopped being
true for a different reason.

## Alternatives considered

**Make every word clickable.** Rejected, and it is what ADR-0295 rejected: four
hover targets and four handlers for one link, none of which knows it is part of a
run. `Words.Node` makes the run one widget, which is the only version that hovers
correctly.

**A `src=` resolver inside the module** — a file loader, a classpath resolver.
Rejected: it decides what a relative path is relative to, which is the question
`Icons` and the stylesheets each needed their own answer for, and it puts a file
system under a widget that gets rebuilt on every keystroke.

**An `img` widget in `:widgets`.** Considered and deferred: a general image widget
wants object-fit, intrinsic ratios in the layout engine and a measure hook, and
none of that is needed to draw a picture in a document. `Picture` is a part in the
module that has the requirement; when a second module wants one, that is the
evidence for promoting it.

**Source offsets in the Markdown model**, so a task could be edited precisely.
Rejected for now: it is a change to a public value type, md4c's offsets are into a
byte buffer the model does not keep, and an ordinal is exact for the one thing that
needs it. If a second feature wants offsets — a click-to-edit editor, an inline
comment anchor — that is the evidence, and the model can grow a component.

**Half a selection**: click-to-select a paragraph, or a "copy" button on each
block. Rejected as inventing a UI: a reader who drags across text expects a
selection, and an affordance that is not the one they reached for is worse than an
honest absence.
