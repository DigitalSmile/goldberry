package dev.goldberry.build.site;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A link from the landing page into the book, the way {@code site/content.js} and
 * {@code site/news.js} write it: relative to the site root and under {@code docs/}.
 *
 * <p>The link names the page mdBook writes; the drift guard needs the chapter
 * mdBook writes it from. The three shapes in use map differently, which is why
 * this is a sealed type rather than one string rewrite.
 */
sealed interface BookLink {

    /** The book's own front page, {@code docs/}: mdBook writes it from the first chapter. */
    record Front() implements BookLink {}

    /** A directory, {@code docs/adr/}: mdBook writes its {@code index.html} from {@code index.md}. */
    record Directory(String path) implements BookLink {}

    /** A chapter, {@code docs/status.html}, written from {@code status.md}. */
    record Chapter(String path) implements BookLink {}

    /** {@code docs/} followed by a path; a fragment is dropped, it names no file. */
    Pattern SHAPE = Pattern.compile("^docs/([a-z0-9/._-]*)$");

    /** The link as the page writes it, or empty when it does not point into the book. */
    static Optional<BookLink> parse(String href) {
        var bare = href.contains("#") ? href.substring(0, href.indexOf('#')) : href;
        var matcher = SHAPE.matcher(bare);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        var path = matcher.group(1);
        if (path.isEmpty()) {
            return Optional.of(new Front());
        }
        if (path.endsWith("/")) {
            return Optional.of(new Directory(path));
        }
        if (path.endsWith(".html")) {
            return Optional.of(new Chapter(path));
        }
        return Optional.empty();
    }

    /**
     * The chapter under {@code book/src} the page is built from, as the path
     * {@code SUMMARY.md} lists it; empty for the front page, which is whatever
     * chapter comes first.
     */
    default Optional<String> source() {
        return switch (this) {
            case Front() -> Optional.empty();
            case Directory(var path) -> Optional.of(path + "index.md");
            case Chapter(var path) -> Optional.of(path.substring(0, path.length() - ".html".length()) + ".md");
        };
    }
}
