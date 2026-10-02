/// Long text being edited: a document shaped one hard line at a time, and its
/// visual lines at one width.
///
/// A `Paragraph` shapes its whole string at once, which is right for a label and
/// wrong for a document somebody is typing into, because every keystroke would
/// shape all of it again. [TextDocument] shapes each hard line on its own and,
/// given the document the last frame built, re-shapes only the lines the edit
/// touched. [DocumentLines] is that document's wrap at one width, computed on
/// demand so that a document of ten thousand lines allocates no record per line
/// per frame.
///
/// Exported because an application editing text on a `canvas` needs it as much as
/// `text-area` does; `dev.goldberry.text.edit` is built on it.
///
/// The package is null-marked: a parameter or return is non-null unless annotated
/// `@Nullable`.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
@NullMarked
package dev.goldberry.text.document;

import org.jspecify.annotations.NullMarked;
