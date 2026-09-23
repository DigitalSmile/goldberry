package io.github.digitalsmile.goldberry.media.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/// An ICY ("SHOUTcast") response body with its metadata taken out: what an
/// internet radio station sends when asked with `Icy-MetaData: 1`
/// (`docs/goldberry-media.md` §4, S6).
///
/// The server says in `icy-metaint: N` how many bytes of audio come between two
/// metadata blocks. After every `N` bytes comes one length byte `L`, then `16 × L`
/// bytes of text, padded with NULs, such as
/// `StreamTitle='Artist - Title';StreamUrl='';`. `L` is 0 when nothing changed.
/// This stream hands on only the audio, and each block's `StreamTitle` to a
/// callback, so the demuxer sees a plain MP3 or Ogg stream.
///
/// A body that ends inside a metadata block ends there: the audio before it was
/// whole, and the block would have said only what was playing.
final class IcyStream extends InputStream {

    /// A key starting a field: letters, then `='`. How the end of a value is told
    /// from a quote inside one, since values are not escaped.
    private static final Pattern NEXT_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]*='");

    private final InputStream in;
    private final int metaint;
    private final Consumer<String> onTitle;
    private int untilMetadata;

    /// Strips metadata from `in` every `metaint` bytes, and hands each block's
    /// `StreamTitle` to `onTitle`, possibly empty.
    IcyStream(InputStream in, int metaint, Consumer<String> onTitle) {
        if (metaint <= 0) {
            throw new IllegalArgumentException("icy-metaint " + metaint);
        }
        this.in = Objects.requireNonNull(in, "in");
        this.metaint = metaint;
        this.onTitle = Objects.requireNonNull(onTitle, "onTitle");
        this.untilMetadata = metaint;
    }

    @Override
    public int read() throws IOException {
        var one = new byte[1];
        var read = read(one, 0, 1);
        return read < 0 ? -1 : one[0] & 0xff;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        if (length == 0) {
            return 0;
        }
        while (untilMetadata == 0) {
            var blocks = in.read();
            if (blocks < 0) {
                return -1;
            }
            var size = blocks * 16;
            if (size > 0) {
                var block = in.readNBytes(size);
                if (block.length < size) {
                    return -1;
                }
                var title = fields(block).get("StreamTitle");
                if (title != null) {
                    onTitle.accept(title);
                }
            }
            untilMetadata = metaint;
        }
        var read = in.read(buffer, offset, Math.min(length, untilMetadata));
        if (read > 0) {
            untilMetadata -= read;
        }
        return read;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    /// The fields of one metadata block, in order.
    ///
    /// Values are quoted with `'` and not escaped, so a title may hold a quote of
    /// its own (`Don't Stop`). A value ends at the `';` that is followed by the
    /// end of the block or by the next `key='`; failing that, at its last quote.
    /// Text is UTF-8 when it decodes as UTF-8, and Latin-1 otherwise, which is what
    /// older servers send.
    static Map<String, String> fields(byte[] block) {
        var text = decode(block);
        var end = text.indexOf('\0');
        if (end >= 0) {
            text = text.substring(0, end);
        }
        var fields = new LinkedHashMap<String, String>();
        var at = 0;
        while (at < text.length()) {
            var equals = text.indexOf("='", at);
            if (equals < 0) {
                break;
            }
            var key = text.substring(at, equals).strip();
            var valueStart = equals + 2;
            var valueEnd = valueEnd(text, valueStart);
            fields.put(key, text.substring(valueStart, valueEnd));
            at = valueEnd + 1;
            if (at < text.length() && text.charAt(at) == ';') {
                at++;
            }
        }
        return fields;
    }

    private static int valueEnd(String text, int from) {
        var search = from;
        while (true) {
            var candidate = text.indexOf("';", search);
            if (candidate < 0) {
                break;
            }
            var after = candidate + 2;
            if (after >= text.length()
                    || text.substring(after).isBlank()
                    || NEXT_KEY.matcher(text).region(after, text.length()).lookingAt()) {
                return candidate;
            }
            search = candidate + 1;
        }
        var lastQuote = text.lastIndexOf('\'');
        return lastQuote >= from ? lastQuote : text.length();
    }

    private static String decode(byte[] block) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(block))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(block, StandardCharsets.ISO_8859_1);
        }
    }
}
