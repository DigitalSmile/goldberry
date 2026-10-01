package dev.goldberry.build.book;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
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

    /** The decision log, which is not a part of the book: it is read on GitHub (ADR-0512). */
    static final String LOG = "adr/";

    /** Where a chapter links a record: the file on GitHub. */
    static final String RECORDS_ON_GITHUB = "https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/";

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

        /** The record on GitHub this points at, as a path under {@code book/src}, if it does. */
        Optional<String> record() {
            if (!href.startsWith(RECORDS_ON_GITHUB)) {
                return Optional.empty();
            }
            var file = target.substring(RECORDS_ON_GITHUB.length());
            return Optional.of(LOG + file);
        }

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

    /**
     * Every Markdown file under {@code book/src} that is a page of the book,
     * relative to it, with forward slashes. The decision log under {@code adr/} is
     * kept there for GitHub and is not built (ADR-0512).
     */
    static List<String> pages() {
        var root = Repository.root().resolve(SOURCE);
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .filter(page -> !page.startsWith(LOG))
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

    /**
     * Where a {@code java} sample breaks the bracket rule: a call whose arguments
     * start on their own lines closes on a line of its own, at the indent of the
     * line that opened it. Each entry names the sample's chapter and line, and
     * the line of the closing bracket within it.
     *
     * <p>Strings, text blocks, character literals and comments are skipped. A
     * bracket opened and closed on one line is not an argument list this rule
     * cares about, and neither is one whose first argument shares the opener's
     * line, which is how a lambda's body is usually written.
     */
    static List<String> bracketFaults(Sample sample) {
        var faults = new ArrayList<String>();
        var code = sample.text();
        var lines = code.split("\n", -1);
        var starts = new int[lines.length];
        for (var i = 1; i < lines.length; i++) {
            starts[i] = starts[i - 1] + lines[i - 1].length() + 1;
        }
        var openers = new ArrayDeque<Integer>();
        var i = 0;
        while (i < code.length()) {
            var c = code.charAt(i);
            if (code.startsWith("//", i)) {
                var end = code.indexOf('\n', i);
                i = end < 0 ? code.length() : end;
            } else if (code.startsWith("/*", i)) {
                var end = code.indexOf("*/", i + 2);
                i = end < 0 ? code.length() : end + 2;
            } else if (code.startsWith("\"\"\"", i)) {
                var end = code.indexOf("\"\"\"", i + 3);
                i = end < 0 ? code.length() : end + 3;
            } else if (c == '"' || c == '\'') {
                var j = i + 1;
                while (j < code.length() && code.charAt(j) != c) {
                    j += code.charAt(j) == '\\' ? 2 : 1;
                }
                i = j + 1;
            } else {
                if (c == '(') {
                    openers.push(i);
                } else if (c == ')' && !openers.isEmpty()) {
                    var opener = openers.pop();
                    var openLine = lineOf(starts, opener);
                    var closeLine = lineOf(starts, i);
                    var afterOpener = lines[openLine].substring(opener - starts[openLine] + 1)
                            .replaceAll("//.*$", "").strip();
                    var beforeCloser = code.substring(starts[closeLine], i);
                    if (openLine != closeLine && afterOpener.isEmpty()) {
                        var indent = lines[openLine].replaceAll("^([ \t]*).*$", "$1");
                        if (!beforeCloser.equals(indent)) {
                            faults.add(sample.chapter() + ":" + sample.line() + " line " + (closeLine + 1));
                        }
                    }
                }
                i++;
            }
        }
        return faults;
    }

    private static int lineOf(int[] starts, int index) {
        var line = 0;
        while (line + 1 < starts.length && starts[line + 1] <= index) {
            line++;
        }
        return line;
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
