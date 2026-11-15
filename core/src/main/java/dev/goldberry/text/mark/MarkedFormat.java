package dev.goldberry.text.mark;

import java.util.Optional;

/// A clipboard type marked text is written in and read back from, beyond the
/// toolkit's own: `text/html`, say, so that a paste from a browser keeps its
/// bold and a copy into a word processor does too.
///
/// ```java
/// new RichInput(draft, onChange).clipboardFormats(List.of(MarkedHtml.format()));
/// ```
///
/// The toolkit itself writes and reads only [MarkedClipboard#MIME] and plain
/// text: a format is installed by an application, from a module that knows the
/// type, because `core` holds no HTML reader.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
public interface MarkedFormat {

    /// The clipboard type, such as `text/html`.
    String mime();

    /// `text` in this format.
    byte[] write(MarkedText text);

    /// The marked text in `bytes`, or nothing when they cannot be read as it.
    /// Bytes from another process: never an exception.
    Optional<MarkedText> read(byte[] bytes);
}
