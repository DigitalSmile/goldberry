package dev.goldberry.example.docs;

import java.util.Optional;
import java.util.regex.Pattern;

/// One section of the published guide: a chapter, and a heading in it.
///
/// The chapter is written as the book writes it, without an extension —
/// `components/buttons` — and the heading as mdBook derives its anchor, so
/// `` ## `button` `` is `button` and `## The tray icon` is `the-tray-icon`. An
/// empty anchor names the chapter itself.
///
/// The record refuses what the site would not serve: an extension, an absolute
/// path, a character mdBook does not write into a path or an anchor. A test in
/// this module reads the book and checks that every link a card carries lands on
/// a heading the chapter has, so a heading renamed in the guide is a red build
/// rather than a card that opens the top of a page.
///
/// Read more: [The catalogue](https://goldberry.dev/docs/components/index.html).
///
/// @param page   the chapter, relative to the book's root and without `.md`
/// @param anchor the heading's anchor, or empty for the chapter itself
public record DocLink(String page, String anchor) {

    /// Where the guide is published.
    public static final String SITE = "https://goldberry.dev/docs/";

    private static final Pattern PAGE = Pattern.compile("[A-Za-z0-9-]+(/[A-Za-z0-9-]+)*");
    private static final Pattern ANCHOR = Pattern.compile("[a-z0-9_-]*");
    private static final Pattern URL =
            Pattern.compile(Pattern.quote(SITE) + "(?<page>[A-Za-z0-9/-]+)\\.html(?:#(?<anchor>[a-z0-9_-]*))?");

    public DocLink {
        if (!PAGE.matcher(page).matches()) {
            throw new IllegalArgumentException("\"" + page
                    + "\" is not a chapter: write it as the book does, without .md, e.g. components/buttons");
        }
        if (!ANCHOR.matcher(anchor).matches()) {
            throw new IllegalArgumentException("\"" + anchor + "\" is not an anchor mdBook writes");
        }
    }

    /// A heading in a chapter: `DocLink.to("components/buttons", "button")`.
    public static DocLink to(String page, String anchor) {
        return new DocLink(page, anchor);
    }

    /// A whole chapter, opened at its top.
    public static DocLink page(String page) {
        return new DocLink(page, "");
    }

    /// The link a card's `href` carries, read back: empty when `url` is not an
    /// address on the published guide.
    public static Optional<DocLink> parse(String url) {
        var matcher = URL.matcher(url);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        var anchor = matcher.group("anchor");
        return Optional.of(new DocLink(matcher.group("page"), anchor == null ? "" : anchor));
    }

    /// The address a browser opens.
    public String url() {
        return SITE + page + ".html" + (anchor.isEmpty() ? "" : "#" + anchor);
    }

    /// The chapter's source under `book/src`.
    public String source() {
        return page + ".md";
    }
}
