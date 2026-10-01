package dev.goldberry.media.subtitle;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// What each format's markup comes down to: plain lines.
///
/// Every text format marks its words up differently, and a cue is drawn as plain
/// text ([Cue]), so this is where the markup goes: SubRip's and WebVTT's HTML-ish
/// tags, WebVTT's entities and inline timestamps, and ASS's `{\override}` blocks
/// and its `\N` breaks.
final class CueText {

    /// `<i>`, `</font>`, `<c.yellow>`, `<v Roger>`, `<00:01.500>`: any tag.
    private static final Pattern TAG = Pattern.compile("<[^<>]*>");

    /// `{\i1}`, `{\pos(10,20)\c&H00FF00&}`: an ASS override block. A brace group
    /// with no backslash is a comment in ASS, and goes too.
    private static final Pattern OVERRIDE = Pattern.compile("\\{[^{}]*}");

    private CueText() {}

    /// SubRip's and WebVTT's text: tags out, entities decoded, lines trimmed and
    /// blank ones dropped.
    static String fromMarkup(String text) {
        var plain = TAG.matcher(text)
                .replaceAll("")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("&lrm;", "")
                .replace("&rlm;", "")
                .replace("&amp;", "&");
        return lines(plain);
    }

    /// ASS's text: override blocks out, `\N` and `\n` as line breaks, `\h` as a
    /// space that does not break.
    static String fromAss(String text) {
        var plain = OVERRIDE.matcher(text)
                .replaceAll("")
                .replace("\\N", "\n")
                .replace("\\n", "\n")
                .replace("\\h", " ");
        return lines(plain);
    }

    /// Trims each line, drops blank ones, and joins the rest with `\n`.
    static String lines(String text) {
        return text.lines().map(String::strip).filter(line -> !line.isEmpty()).collect(Collectors.joining("\n"));
    }
}
