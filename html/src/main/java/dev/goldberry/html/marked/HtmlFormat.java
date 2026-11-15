package dev.goldberry.html.marked;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import dev.goldberry.text.mark.MarkedFormat;
import dev.goldberry.text.mark.MarkedText;

/// `text/html` on the clipboard: [MarkedHtml#format()].
///
/// Written as UTF-8 behind a `meta` that says so, which is what a word
/// processor needs to read it right. Read in whatever the bytes say they are: a
/// byte-order mark for UTF-16, which one browser writes on Linux, and the
/// `Version:` header Windows puts in front of a page, skipped to the first tag.
final class HtmlFormat implements MarkedFormat {

    static final HtmlFormat INSTANCE = new HtmlFormat();

    /// What a copy starts with.
    static final String PREFIX = "<meta charset=\"utf-8\">";

    private HtmlFormat() {}

    @Override
    public String mime() {
        return "text/html";
    }

    @Override
    public byte[] write(MarkedText text) {
        return (PREFIX + MarkedHtml.write(text)).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Optional<MarkedText> read(byte[] bytes) {
        var html = decode(bytes);
        if (html.startsWith("Version:")) {
            var tag = html.indexOf('<');
            html = tag < 0 ? "" : html.substring(tag);
        }
        var read = MarkedHtml.read(html);
        return read.isEmpty() ? Optional.empty() : Optional.of(read);
    }

    /// `bytes` as text, by their byte-order mark, UTF-8 when they have none.
    static String decode(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return text(bytes, 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return text(bytes, 2, StandardCharsets.UTF_16BE);
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return text(bytes, 3, StandardCharsets.UTF_8);
        }
        return text(bytes, 0, StandardCharsets.UTF_8);
    }

    private static String text(byte[] bytes, int skip, Charset charset) {
        return new String(bytes, skip, bytes.length - skip, charset);
    }

    @Override
    public String toString() {
        return "MarkedFormat[text/html]";
    }
}
