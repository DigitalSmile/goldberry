<!-- Three pieces for CSS-50 (ADR-0622), each says where it goes. -->

<!-- 1. Destination: `book/src/guide/text.md`, `## Paragraphs`. Replaces the
     paragraph "Right-to-left text is shaped in logical order and therefore
     drawn mirrored. `isBidiApproximate()` says when that happened. It is an
     approximation that says so rather than a refusal." Keep the sentence on
     style runs that follows it only if it is still true then. -->

A paragraph has a base direction, and text in a right-to-left script is split
into runs that each read their own way, placed in reading order on every
line. `Paragraph.of(font, text)` takes each paragraph's direction from its
first letter that has one; a widget's paragraph takes its style's `direction`
and `unicode-bidi`.

```java
var label = Paragraph.of(font, "שלום, world", 0, 0, Directionality.RTL);
double x = label.caretX(line.start(), line.end(), offset);   // where the caret is drawn
List<Stretch> lit = label.stretchesOf(line.start(), line.end(), from, to);
```

A width is the same whichever way the runs are placed, so `widthBetween`
sizes a box; where a caret or a highlight goes on a line is `caretX` and
`stretchesOf`, and `offsetAt` is the caret nearest a click.

<!-- 2. Destination: `book/src/components/forms.md`, before the Forms screen
     picture. Replaces "Right-to-left editing is not built. A paragraph
     approximates bidirectional text rather than refusing it." -->

Right-to-left text reads from the right in both fields. The caret, a click and
a selection follow the text as it is drawn, and the arrow keys move through it
in reading order: `Left` moves on in a right-to-left paragraph. A field's base
direction is its `direction`; `unicode-bidi: plaintext` lets each paragraph
find its own.

<!-- 3. Destination: `book/src/overview/limitations.md`. Deletes the
     *Bidirectional text* row ("Right-to-left runs are shaped in logical order
     and drawn mirrored. The paragraph says so rather than refusing"). If a row
     is wanted, it reads: -->

| Vertical text | `writing-mode` is not in the subset; text runs horizontally, in either direction |
