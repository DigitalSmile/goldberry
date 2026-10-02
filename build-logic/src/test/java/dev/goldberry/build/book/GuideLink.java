package dev.goldberry.build.book;

import java.util.Optional;
import java.util.regex.Pattern;

/// A link from a source file into the published guide, the way a doc comment
/// writes it: absolute, under `https://goldberry.dev/docs/`, naming the page
/// mdBook writes and, optionally, a heading on it.
///
/// The drift guard needs the chapter mdBook writes the page from, so the
/// `.html` is turned back into the `.md` under `book/src`, and a directory is
/// its `index.md`. The fragment is kept, because a heading is checked too.
///
/// @param href     the link as written
/// @param page     the chapter under `book/src`, or empty for the book's front page
/// @param fragment the heading named after the `#`, if any
record GuideLink(String href, Optional<String> page, Optional<String> fragment) {

    /// Where the guide is published.
    static final String BASE = "https://goldberry.dev/docs/";

    /// A guide link wherever it appears in a source: it ends at whitespace or at
    /// the character that closes a Markdown link, an HTML attribute or a string.
    static final Pattern IN_TEXT = Pattern.compile("https://goldberry\\.dev/docs/[^\\s)\\]\"'<>`]*");

    /// The path after the base, with the fragment split off.
    private static final Pattern SHAPE = Pattern.compile("^([A-Za-z0-9/._-]*)(?:#([A-Za-z0-9_-]*))?$");

    /// The link parsed, or empty when it is not a guide link or names something
    /// mdBook does not write: a `.md`, a page with no extension, a bad character.
    static Optional<GuideLink> parse(String href) {
        if (!href.startsWith(BASE)) {
            return Optional.empty();
        }
        var matcher = SHAPE.matcher(href.substring(BASE.length()));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        var path = matcher.group(1);
        var fragment = Optional.ofNullable(matcher.group(2)).filter(name -> !name.isEmpty());
        if (path.isEmpty()) {
            return Optional.of(new GuideLink(href, Optional.empty(), fragment));
        }
        if (path.endsWith("/")) {
            return Optional.of(new GuideLink(href, Optional.of(path + "index.md"), fragment));
        }
        if (path.endsWith(".html")) {
            var chapter = path.substring(0, path.length() - ".html".length()) + ".md";
            return Optional.of(new GuideLink(href, Optional.of(chapter), fragment));
        }
        return Optional.empty();
    }
}
