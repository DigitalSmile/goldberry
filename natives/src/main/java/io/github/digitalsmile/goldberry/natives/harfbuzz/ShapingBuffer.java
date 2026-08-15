package io.github.digitalsmile.goldberry.natives.harfbuzz;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/// The run of text being shaped, and afterwards the glyphs it produced.
///
/// Meant to be **reused**. A layout pass shapes the same paragraph at several
/// widths as Yoga searches, so creating a buffer per attempt would allocate and
/// free native memory in the middle of the measure callback. [#reset()] returns
/// it to a clean state and keeps the allocation.
///
/// The text crosses as UTF-16 because a Java `String` already is UTF-16: no
/// transcoding, and the cluster indices HarfBuzz reports are indices into the
/// original `String` rather than into some re-encoded copy of it. That
/// correspondence is what a caret and a selection are built on, and it is the
/// reason `hb_buffer_add_utf16` is the entry point rather than the UTF-8 one.
///
/// Confined to the thread that created it, and must be closed.
public final class ShapingBuffer implements AutoCloseable {

    private final HarfBuzz harfbuzz = HarfBuzz.get();
    private final Thread owner = Thread.currentThread();
    private final MemorySegment buffer;

    private boolean closed;

    private ShapingBuffer() {
        this.buffer = harfbuzz.bufferCreate();
    }

    /// A new, empty buffer.
    public static ShapingBuffer create() {
        return new ShapingBuffer();
    }

    /// Empties the buffer, keeping its allocation, and clears the direction,
    /// script and language with it.
    public void reset() {
        requireUsable();
        harfbuzz.bufferReset(buffer);
    }

    /// Adds the whole of `text` as the run to shape.
    public void addText(CharSequence text) {
        Objects.requireNonNull(text, "text");
        addText(text, 0, text.length());
    }

    /// Adds `text`, shaping only `[start, end)` of it.
    ///
    /// Everything outside that range is **context**: the shaper may look at it
    /// to decide joining and contextual forms, but produces no glyphs for it.
    /// That is what makes shaping one word of a line give the same result as
    /// shaping the whole line — in Arabic, a letter's form depends on what is
    /// beside it, so shaping a fragment without its neighbours is visibly wrong.
    ///
    /// @throws IndexOutOfBoundsException if the range is outside the text
    public void addText(CharSequence text, int start, int end) {
        requireUsable();
        Objects.requireNonNull(text, "text");
        Objects.checkFromToIndex(start, end, text.length());
        if (text.isEmpty()) {
            return;
        }

        // Confined to this call: hb_buffer_add_utf16 copies the code units into
        // the buffer, so nothing native refers to this segment afterwards.
        try (var arena = Arena.ofConfined()) {
            var length = text.length();
            var segment = arena.allocate(ValueLayout.JAVA_CHAR, length);
            for (var i = 0; i < length; i++) {
                segment.setAtIndex(ValueLayout.JAVA_CHAR, i, text.charAt(i));
            }
            harfbuzz.bufferAddUtf16(buffer, segment, length, start, end - start);
        }
    }

    /// Fills in direction, script and language from the text already added.
    ///
    /// HarfBuzz guesses from the first strong character it finds, which is right
    /// far more often than it is wrong and is what a paragraph of ordinary text
    /// wants. It is a guess about the *whole run* though: real bidirectional
    /// text has to be split into runs of one direction before it gets here, and
    /// that splitting is not HarfBuzz's job.
    ///
    /// Must be called after the text and before shaping — a buffer with no
    /// direction shapes to nothing.
    public void guessSegmentProperties() {
        requireUsable();
        harfbuzz.bufferGuessSegmentProperties(buffer);
    }

    /// Sets the direction explicitly, overriding any guess.
    public void setDirection(TextDirection direction) {
        requireUsable();
        Objects.requireNonNull(direction, "direction");
        harfbuzz.bufferDirection(buffer, direction);
    }

    /// The direction the buffer will shape in.
    public TextDirection direction() {
        requireUsable();
        return harfbuzz.bufferDirection(buffer);
    }

    /// Sets the script from its four-letter ISO 15924 tag, e.g. `Latn`, `Arab`.
    ///
    /// @throws IllegalArgumentException if the tag is not four characters
    public void setScript(String tag) {
        requireUsable();
        Objects.requireNonNull(tag, "tag");
        if (tag.length() != 4) {
            throw new IllegalArgumentException(
                    "a script tag is four characters (ISO 15924), and \"" + tag + "\" is "
                            + tag.length());
        }
        try (var arena = Arena.ofConfined()) {
            var bytes = tag.getBytes(StandardCharsets.US_ASCII);
            var segment = arena.allocate(ValueLayout.JAVA_BYTE, bytes.length);
            MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, 0, bytes.length);
            harfbuzz.bufferScript(buffer, harfbuzz.scriptFromString(segment, bytes.length));
        }
    }

    /// Sets the language from a BCP 47 tag, e.g. `en`, `ar-EG`.
    ///
    /// It matters less than it looks: language selects between locale-specific
    /// forms in fonts that have them, and most do not. It is bound because the
    /// ones that do — Serbian versus Russian Cyrillic, say — are wrong in a way
    /// a native reader notices immediately and nobody else does.
    public void setLanguage(String tag) {
        requireUsable();
        Objects.requireNonNull(tag, "tag");
        if (tag.isBlank()) {
            throw new IllegalArgumentException("a language tag must not be blank");
        }
        try (var arena = Arena.ofConfined()) {
            var bytes = tag.getBytes(StandardCharsets.US_ASCII);
            var segment = arena.allocate(ValueLayout.JAVA_BYTE, bytes.length);
            MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, 0, bytes.length);
            // The returned hb_language_t is interned and owned by HarfBuzz
            // forever -- it is not something to free.
            harfbuzz.bufferLanguage(buffer, harfbuzz.languageFromString(segment, bytes.length));
        }
    }

    /// How many items the buffer holds — characters before shaping, glyphs
    /// after.
    public int length() {
        requireUsable();
        return harfbuzz.bufferLength(buffer);
    }

    /// Shapes the buffer with `font` and returns the glyphs.
    ///
    /// The buffer is left holding the shaped result, so it must be [#reset()]
    /// before it is used for another run. The returned [GlyphRun] is a copy and
    /// stays valid afterwards.
    public GlyphRun shape(ShapedFont font) {
        requireUsable();
        Objects.requireNonNull(font, "font");
        harfbuzz.shape(font.pointer(), buffer);
        return harfbuzz.readGlyphs(buffer);
    }

    /// Whether the buffer has been closed.
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        requireOwner();
        closed = true;
        harfbuzz.bufferDestroy(buffer);
    }

    private void requireUsable() {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("this ShapingBuffer has been closed");
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "a ShapingBuffer belongs to the thread that created it, and this is not it");
        }
    }

    @Override
    public String toString() {
        return "ShapingBuffer" + (closed ? " (closed)" : "[" + length() + " items]");
    }
}
