package dev.goldberry.build.book;

import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/// Words the guide does not say to its reader, each kind a pattern over one line
/// of prose.
///
/// The guide is user documentation. It says what the toolkit does, so a line
/// reporting how far something has been checked is a line from a log; and it
/// says what the reader writes, so a line naming a library the toolkit uses
/// inside is a line about the implementation, which belongs to the overview.
///
/// A line is read the way a reader sees it: inline code, link targets and HTML
/// tags are taken out first, because a Maven classifier, a quoted error message
/// or a URL is something the reader types or follows, not a sentence.
enum GuideVocabulary {

    /// The state of a check: "not verified", "untested", "a hand test".
    VERIFICATION(Pattern.compile(
            "(?i)\\b(?:not (?:yet )?(?:verified|tested|measured|proven|confirmed)"
                    + "|unverified|untested|unproven|unmeasured|hand[- ]tests?|work in progress)\\b")),

    /// A library the toolkit is built on, named in a sentence.
    LIBRARY(Pattern.compile(
            "\\b(?:Yoga|Blend2D|HarfBuzz|FreeType|SDL3?|md4c|libwebp|FFmpeg|GStreamer|dav1d|Lucide|LWJGL"
                    + "|SPIRV-Cross|ThorVG|libunibreak|IBus|libdbus)\\b"));

    /// A code span, single or double backticked.
    private static final Pattern CODE = Pattern.compile("``.*?``|`[^`]*`");

    /// The target of a Markdown link; the text in brackets stays.
    private static final Pattern TARGET = Pattern.compile("\\]\\([^)]*\\)");

    /// An HTML tag with its attributes; the text between tags stays.
    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    private final Pattern words;

    GuideVocabulary(Pattern words) {
        this.words = words;
    }

    /// The words of this kind on `line`, in order, read as the reader sees it.
    List<String> in(String line) {
        return words.matcher(readable(line)).results().map(MatchResult::group).toList();
    }

    /// `line` without what a reader types or follows rather than reads.
    static String readable(String line) {
        var withoutCode = CODE.matcher(line).replaceAll(" ");
        var withoutTargets = TARGET.matcher(withoutCode).replaceAll("]");
        return TAG.matcher(withoutTargets).replaceAll(" ");
    }
}
