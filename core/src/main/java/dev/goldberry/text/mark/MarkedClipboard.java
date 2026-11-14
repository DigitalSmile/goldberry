package dev.goldberry.text.mark;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

import dev.goldberry.render.clipboard.Clipboard;
import dev.goldberry.text.mark.MarkKind.Behaviour;
import dev.goldberry.text.mark.MarkKind.Custom;
import dev.goldberry.text.mark.MarkKind.Link;
import dev.goldberry.text.mark.MarkKind.Mention;
import dev.goldberry.text.mark.MarkKind.Style;

/// Marked text on the clipboard: copied with its marks, pasted with them where
/// the clipboard has them, and as plain text everywhere else.
///
/// ```java
/// MarkedClipboard.copy(window.clipboard(), edit.selected());
/// MarkedClipboard.paste(window.clipboard()).ifPresent(edit::insert);
/// ```
///
/// A copy offers [#MIME], the toolkit's own type, **and** the plain text, so a
/// paste into another rich field keeps the bold and a paste into a terminal
/// gets the words. A paste prefers the toolkit's type and falls back to the
/// text.
///
/// The type is a few lines of UTF-8: a header, the marks one per line, and the
/// text. [#encode] and [#decode] are its whole definition, and a payload that
/// does not decode is read as no payload, never as an error: the bytes came
/// from another process.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
public final class MarkedClipboard {

    /// The toolkit's own clipboard type for marked text.
    public static final String MIME = "application/x-goldberry-marked";

    /// The plain text, as SDL and the desktops name it.
    public static final String TEXT_MIME = "text/plain;charset=utf-8";

    private static final String HEADER = "goldberry-marked 1";

    private MarkedClipboard() {}

    /// Puts `text` on `clipboard`: its own type and the plain text when it has
    /// marks, the plain text alone when it has none.
    ///
    /// @return whether the platform accepted it
    public static boolean copy(Clipboard clipboard, MarkedText text) {
        Objects.requireNonNull(clipboard, "clipboard");
        Objects.requireNonNull(text, "text");
        if (text.isPlain()) {
            return clipboard.text(text.text());
        }
        var offer = new LinkedHashMap<String, byte[]>();
        offer.put(MIME, encode(text));
        var plain = text.text().getBytes(StandardCharsets.UTF_8);
        offer.put(TEXT_MIME, plain);
        offer.put("text/plain", plain);
        // A backend with no data clipboard refuses the offer; the words still
        // go, which is what a copy is for.
        return clipboard.write(offer) || clipboard.text(text.text());
    }

    /// What `clipboard` holds as marked text: its own type when it is there and
    /// reads, the plain text otherwise, and nothing when it holds neither.
    public static Optional<MarkedText> paste(Clipboard clipboard) {
        Objects.requireNonNull(clipboard, "clipboard");
        if (clipboard.has(MIME)) {
            var marked = decode(clipboard.read(MIME));
            if (marked.isPresent()) {
                return marked;
            }
        }
        if (!clipboard.hasText()) {
            return Optional.empty();
        }
        var text = clipboard.text();
        return text.isEmpty() ? Optional.empty() : Optional.of(MarkedText.of(text));
    }

    /// `text` in the toolkit's clipboard type.
    public static byte[] encode(MarkedText text) {
        Objects.requireNonNull(text, "text");
        var out = new StringBuilder(text.length() + 32 * text.marks().size() + 32);
        out.append(HEADER).append('\n').append(text.marks().size()).append('\n');
        for (var mark : text.marks()) {
            out.append(mark.start()).append(' ').append(mark.end()).append(' ');
            switch (mark.kind()) {
                case Style style -> out.append(style.className());
                case Link(var uri) -> out.append("link ").append(escape(uri.toString()));
                case Mention(var id) -> out.append("mention ").append(escape(id));
                case Custom(var name, var value, var behaviour) ->
                    out.append("custom ")
                            .append(name)
                            .append(' ')
                            .append(flag(behaviour.growsAtEnd()))
                            .append(flag(behaviour.atomic()))
                            .append(flag(behaviour.exclusive()))
                            .append(' ')
                            .append(escape(value));
            }
            out.append('\n');
        }
        out.append(text.text());
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /// The marked text in `bytes`, or nothing when they are not the toolkit's
    /// clipboard type.
    public static Optional<MarkedText> decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        var all = new String(bytes, StandardCharsets.UTF_8);
        try {
            var at = 0;
            var end = all.indexOf('\n', at);
            if (end < 0 || !all.substring(at, end).equals(HEADER)) {
                return Optional.empty();
            }
            at = end + 1;
            end = all.indexOf('\n', at);
            var count = Integer.parseInt(all.substring(at, end));
            if (count < 0) {
                return Optional.empty();
            }
            at = end + 1;
            var marks = new ArrayList<Mark>(Math.min(count, 1024));
            for (var i = 0; i < count; i++) {
                end = all.indexOf('\n', at);
                marks.add(mark(all.substring(at, end).split(" ", -1)));
                at = end + 1;
            }
            return Optional.of(new MarkedText(all.substring(at), marks));
        } catch (RuntimeException malformed) {
            // Bytes from another process: anything that does not parse is a
            // payload this toolkit did not write.
            return Optional.empty();
        }
    }

    private static Mark mark(String[] parts) {
        var start = Integer.parseInt(parts[0]);
        var end = Integer.parseInt(parts[1]);
        MarkKind kind = switch (parts[2]) {
            case "link" -> new Link(URI.create(unescape(parts[3])));
            case "mention" -> new Mention(unescape(parts[3]));
            case "custom" ->
                new Custom(
                        parts[3],
                        unescape(parts[5]),
                        new Behaviour(parts[4].charAt(0) == '1', parts[4].charAt(1) == '1', parts[4].charAt(2) == '1'));
            default -> style(parts[2]);
        };
        return new Mark(start, end, kind);
    }

    private static Style style(String className) {
        for (var style : Style.values()) {
            if (style.className().equals(className)) {
                return style;
            }
        }
        throw new IllegalArgumentException("no mark kind is called " + className);
    }

    private static char flag(boolean value) {
        return value ? '1' : '0';
    }

    private static String escape(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String unescape(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
