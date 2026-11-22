Destination: `book/src/components/drawing.md`, `## image`, the paragraph that
begins "The record is `ImageView`". Its last sentence, "An SVG does not decode,
and shows the error state.", is replaced by the paragraph below. W3.6 phase 3;
the full section on SVG (`components-vector.md`) follows with phase 6 and may
absorb this.

---

SVG decodes once `goldberry-vector` is on the module path, with its natives for
the platform. Its natural size is the document's own, and it is drawn for the
device pixels its box covers, so it stays sharp at any size and any display
scale; the same holds for an SVG named by a stylesheet's `url()` and for one an
HTML or Markdown view shows. A document drawn this way takes `currentColor` as
black. Without the module, an SVG shows the error state, and the log names the
artifact to add.
