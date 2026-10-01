package dev.goldberry.build.book;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import dev.goldberry.build.repository.Repository;

/**
 * The book under {@code book/src}, read the way mdBook reads it: a summary that
 * lists chapters, chapters that link one another, and headings that become the
 * anchors those links name.
 *
 * <p>Everything here is a plain reading of the files. What is held to be true
 * about them is {@link BookTest}'s business.
 */
final class Book {

    /** Where mdBook reads the chapters from. */
    static final Path SOURCE = Path.of("book", "src");

    /** The decision log, which the guide's rules do not apply to. */
    static final String LOG = "adr/";

    /** A chapter as {@code SUMMARY.md} lists it: {@code [Title](path.md)}. */
    private static final Pattern LISTED = Pattern.compile("\\[([^\\]]*)\\]\\(([^)]+\\.md)\\)");

    /** A Markdown link or an HTML {@code href}, as the guide writes them. */
    private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)|href=\"([^\"]+)\"");

    /** A heading of any level, at the start of a line. */
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*$");

    /** An explicit {@code id="..."} in an HTML block, which mdBook keeps. */
    private static final Pattern HTML_ID = Pattern.compile("\\sid=\"([^\"]+)\"");

    /** A fenced block's opening line, with whatever the author wrote after the fence. */
    private static final Pattern FENCE = Pattern.compile("^```(\\S*)\\s*$");

    private Book() {
    }

    /** A chapter the summary lists: its title and its path under {@code book/src}. */
    record Chapter(String title, String path) {

        boolean inTheLog() {
            return path.startsWith(LOG);
        }
    }

    /**
     * A link one chapter writes, split the way a resolver needs it: the file it
     * names, relative to the chapter, and the fragment after the {@code #} if any.
     */
    record Link(String chapter, String href, String target, Optional<String> fragment) {

        /** Whether this points somewhere outside the book, which is not checked here. */
        boolean external() {
            return href.startsWith("http://") || href.startsWith("https://") || href.startsWith("mailto:");
        }

        /** Whether this names only a heading on its own page. */
        boolean onThisPage() {
            return target.isEmpty();
        }

        /** The file the link lands on, as a path under {@code book/src}. */
        Path resolved() {
            var page = target.endsWith(".html") ? target.substring(0, target.length() - 5) + ".md" : target;
            return Path.of(chapter).resolveSibling(page).normalize();
        }

        static Link parse(String chapter, String href) {
            var hash = href.indexOf('#');
            var target = hash < 0 ? href : href.substring(0, hash);
            var fragment = hash < 0 ? Optional.<String>empty() : Optional.of(href.substring(hash + 1));
            return new Link(chapter, href, target, fragment);
        }
    }

    /** A fenced code sample: where it is, what language it claims, and its text. */
    record Sample(String chapter, int line, String info, String text) {

        /** The language before any comma: {@code kdl} for {@code kdl,ignore}. */
        String language() {
            var comma = info.indexOf(',');
            return comma < 0 ? info : info.substring(0, comma);
        }

        /** Whether the author marked the block as not a whole document. */
        boolean ignored() {
            return info.contains("ignore");
        }
    }

    /** The chapters {@code SUMMARY.md} lists, in order, prefix and suffix chapters included. */
    static List<Chapter> chapters() {
        var summary = Repository.read(SOURCE.resolve("SUMMARY.md").toString());
        return LISTED.matcher(summary).results()
                .map(match -> new Chapter(match.group(1), match.group(2)))
                .toList();
    }

    /** Every Markdown file under {@code book/src}, relative to it, with forward slashes. */
    static List<String> pages() {
        var root = Repository.root().resolve(SOURCE);
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + root, e);
        }
    }

    /** A chapter's text, with its line endings normalised. */
    static String text(String page) {
        return Repository.read(SOURCE.resolve(page).toString());
    }

    static boolean exists(String page) {
        return Repository.exists(SOURCE.resolve(page).toString());
    }

    /** Every link a chapter writes, in Markdown and in its HTML blocks. */
    static List<Link> links(String page) {
        return LINK.matcher(text(page)).results()
                .map(match -> match.group(1) != null ? match.group(1) : match.group(2))
                .map(href -> Link.parse(page, href))
                .toList();
    }

    /** The first line of a chapter that is a heading, without its marks. */
    static Optional<String> title(String page) {
        return text(page).lines()
                .map(HEADING::matcher)
                .filter(java.util.regex.Matcher::matches)
                .map(match -> match.group(2))
                .findFirst();
    }

    /** Every heading in a chapter, as written, marks removed. */
    static List<String> headings(String page) {
        return text(page).lines()
                .map(HEADING::matcher)
                .filter(java.util.regex.Matcher::matches)
                .map(match -> match.group(2))
                .toList();
    }

    /**
     * The anchors a chapter has: one per heading, the way mdBook derives them,
     * plus every explicit {@code id} in its HTML.
     *
     * <p>mdBook drops the heading's HTML and its code marks, keeps letters, digits,
     * hyphens and underscores in lower case, writes a hyphen for every whitespace
     * character, one each, and drops the rest: {@code M4 — GPU} is
     * {@code m4--gpu}. A second heading with the same text gets {@code -1}, which
     * this does not reproduce: a link to the second of two identical headings is
     * a link worth rewriting anyway.
     */
    static List<String> anchors(String page) {
        var anchors = new ArrayList<String>();
        headings(page).forEach(heading -> anchors.add(anchorOf(heading)));
        HTML_ID.matcher(text(page)).results().forEach(match -> anchors.add(match.group(1)));
        return anchors;
    }

    static String anchorOf(String heading) {
        var plain = heading.replaceAll("<[^>]+>", "").replace("`", "").strip();
        var anchor = new StringBuilder();
        plain.codePoints().forEach(ch -> {
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-') {
                anchor.appendCodePoint(Character.toLowerCase(ch));
            } else if (Character.isWhitespace(ch)) {
                anchor.append('-');
            }
        });
        return anchor.toString();
    }

    /** Every fenced sample in a chapter, with the line its fence opens on. */
    static List<Sample> samples(String page) {
        var samples = new ArrayList<Sample>();
        var lines = text(page).lines().toList();
        String info = null;
        var body = new StringBuilder();
        var opened = 0;
        for (var number = 0; number < lines.size(); number++) {
            var line = lines.get(number);
            if (info == null) {
                var fence = FENCE.matcher(line);
                if (fence.matches()) {
                    info = fence.group(1);
                    opened = number + 1;
                    body.setLength(0);
                }
            } else if (line.strip().equals("```")) {
                samples.add(new Sample(page, opened, info, body.toString()));
                info = null;
            } else {
                body.append(line).append('\n');
            }
        }
        return samples;
    }
}
