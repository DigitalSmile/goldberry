package io.github.digitalsmile.goldberry.natives.md4c;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.md4c.calls.MarkdownCalls;
import io.github.digitalsmile.goldberry.natives.md4c.enums.MarkdownFlag;

/// The Markdown parser, as one call that hands back a list of events.
///
/// md4c is a SAX parser — five callbacks, invoked thousands of times for a document
/// of any size — and this is deliberately **not** that shape. The events are encoded
/// natively into one buffer and read once, so nothing crosses the FFM boundary per
/// block and no detail struct is modelled in Java at all
/// (ADR-0294).
/// What a caller gets is a value:
///
/// ```java
/// for (var event : Md4c.get().parse("# Hello\n", Set.of(MarkdownFlag.TABLES))) {
///     switch (event) {
///         case MarkdownEvent.EnterBlock(var type, BlockDetail.Heading(var level)) -> …
///         case MarkdownEvent.Text(var type, var text) -> …
///         default -> { }
///     }
/// }
/// ```
///
/// ## Why this is in `:natives` and reaches exactly one module
///
/// The wrapper traffics in Java types — a `String` in, records out — so it obeys
/// §3.1 like every other wrapper here. But md4c is not part of the toolkit's own
/// surface: it is `goldberry-html`'s dependency, and the module descriptor exports
/// this package to that module and to nobody else, the way Blend2D and Yoga are
/// exported to `:core` alone
/// (ADR-0280).
/// An application that wants Markdown asks `:html` for a document, not this for
/// events.
public final class Md4c {

    private static final class Holder {
        private static final Md4c INSTANCE = new Md4c(NativeLibrary.get().lookup());
    }

    private final MarkdownCalls calls;

    private Md4c(SymbolLookup lookup) {
        this.calls = MarkdownCalls.bind(lookup);
    }

    /// The parser, loading `libgoldberry` on first call.
    public static Md4c get() {
        return Holder.INSTANCE;
    }

    /// Parses `markdown` with the extensions in `flags`.
    ///
    /// The whole parse happens inside this call: one downcall, one buffer, and the
    /// buffer is freed before it returns. There is nothing left over to close, which
    /// is why this hands back a `List` rather than something that owns native memory.
    ///
    /// @param markdown the document, as text. UTF-8 on the wire, which is what md4c
    ///        reads and what its offsets count in
    /// @param flags the dialect — [MarkdownFlag#TABLES] and friends. Empty means
    ///        plain CommonMark
    /// @return every event, in document order, `DOC` pair included. Empty text still
    ///         produces the pair, because an empty document is a document
    /// @throws IllegalStateException if the parse failed, which means md4c reported a
    ///         runtime error or the native buffer could not grow — not that the text
    ///         was not Markdown, since there is no such text
    public List<MarkdownEvent> parse(String markdown, Set<MarkdownFlag> flags) {
        Objects.requireNonNull(markdown, "markdown");
        Objects.requireNonNull(flags, "flags");
        var utf8 = markdown.getBytes(StandardCharsets.UTF_8);
        try (var arena = Arena.ofConfined()) {
            var text = MemorySegment.NULL;
            if (utf8.length > 0) {
                text = arena.allocate(utf8.length);
                MemorySegment.copy(utf8, 0, text, ValueLayout.JAVA_BYTE, 0, utf8.length);
            }
            var handle = calls.parse().call(text, utf8.length, MarkdownFlag.mask(flags));
            if (MemorySegment.NULL.equals(handle)) {
                throw new IllegalStateException("goldberry_md_parse() could not encode " + utf8.length
                        + " bytes of Markdown; md4c failed or the event buffer could not grow");
            }
            try {
                return MarkdownStream.decode(events(handle));
            } finally {
                calls.free().call(handle);
            }
        }
    }

    /// The encoded events, as a segment of the length the library reports.
    ///
    /// Restricted: a bare pointer carries no extent, and resizing it against the
    /// size the same library just reported is the only way to read it — the same
    /// move [io.github.digitalsmile.goldberry.natives.GoldberryShim#layoutTable()]
    /// needs for the layout table. Suppressed per call site so a new crossing still
    /// shows up as a build failure.
    @SuppressWarnings("restricted")
    private MemorySegment events(MemorySegment handle) {
        var size = Integer.toUnsignedLong(calls.size().call(handle));
        var data = calls.data().call(handle);
        if (size == 0 || MemorySegment.NULL.equals(data)) {
            return MemorySegment.NULL.reinterpret(0);
        }
        return data.reinterpret(size);
    }

    /// Resolves an HTML5 named entity into its codepoints.
    ///
    /// @param reference the reference as written, `&` and `;` included — `"&amp;"`
    /// @return one codepoint, or two for the handful of entities that need them, or
    ///         an empty array when the name is not an entity. A caller that gets
    ///         nothing back shows the reference verbatim, which is what every
    ///         Markdown renderer does with `&nope;`
    public int[] entity(String reference) {
        Objects.requireNonNull(reference, "reference");
        var utf8 = reference.getBytes(StandardCharsets.UTF_8);
        if (utf8.length == 0) {
            return new int[0];
        }
        try (var arena = Arena.ofConfined()) {
            var name = arena.allocate(utf8.length);
            MemorySegment.copy(utf8, 0, name, ValueLayout.JAVA_BYTE, 0, utf8.length);
            var out = arena.allocate(ValueLayout.JAVA_INT, 2);
            if (calls.entity().call(name, utf8.length, out) == 0) {
                return new int[0];
            }
            var first = out.getAtIndex(ValueLayout.JAVA_INT, 0);
            var second = out.getAtIndex(ValueLayout.JAVA_INT, 1);
            // Most entities are one codepoint and say so by leaving the second zero.
            return second == 0 ? new int[] {first} : new int[] {first, second};
        }
    }
}
