package dev.goldberry.example.docs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/// The guide's chapters and headings, read from `book/src`, and what the gallery
/// has to show of them.
///
/// Three kinds of chapter, and the rule for each is how much of it needs a card:
///
/// - a **widget** chapter, under `components/` and the layout part: every `##`
///   heading, which is one widget or one feature of a family. A `###` under it is a
///   child node, `option` under `select`, and the parent's card shows it;
/// - a **feature** chapter of the developer guide: every `##` and `###` heading,
///   because each is something a window does that a card can show;
/// - every other chapter in the summary: a link to the chapter, on a reference
///   card or a screen's header.
///
/// `Read more` is a list of records and never needs a card.
public final class BookSections {

    /// The chapters whose every `##` heading needs a card.
    static final Set<String> WIDGET_CHAPTERS = Set.of(
            "layout/row-and-column",
            "layout/spacer",
            "layout/stack",
            "layout/scroll",
            "layout/split-pane",
            "layout/masonry",
            "layout/affix",
            "layout/sizing",
            "components/text",
            "components/buttons",
            "components/choices",
            "components/values",
            "components/forms",
            "components/panels",
            "components/collections",
            "components/navigation",
            "components/menus",
            "components/overlays",
            "components/charts",
            "components/drawing",
            "components/content",
            "components/media",
            "components/gpu");

    /// The chapters whose every `##` and `###` heading needs a card.
    static final Set<String> FEATURE_CHAPTERS = Set.of(
            "applications",
            "guide/markup",
            "guide/styling",
            "guide/design-system",
            "guide/text",
            "guide/input",
            "guide/windows",
            "guide/logging");

    /// Headings that are never a card.
    private static final Set<String> NEVER = Set.of("read-more");

    private static final Pattern LISTED = Pattern.compile("\\]\\(([A-Za-z0-9/_-]+)\\.md\\)");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");

    /// A heading: how deep it is, what it says, and the anchor mdBook gives it.
    public record Heading(int level, String text, String anchor) {}

    private BookSections() {}

    /// `book/src`, found from wherever the test runs.
    public static Path root() {
        var directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            var summary = directory.resolve("book/src/SUMMARY.md");
            if (Files.isRegularFile(summary)) {
                return summary.getParent();
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException(
                "cannot find book/src/SUMMARY.md above " + Path.of("").toAbsolutePath());
    }

    /// Every chapter the summary lists, in its order, without `.md`.
    public static List<String> chapters() {
        var chapters = new LinkedHashSet<String>();
        LISTED.matcher(read("SUMMARY.md")).results().forEach(match -> chapters.add(match.group(1)));
        return List.copyOf(chapters);
    }

    /// Every heading in `chapter`, outside code fences, with the anchor the book's
    /// own links use. mdBook gives a repeated heading a `-1`; the book never links
    /// one, and neither does a card, so it is not reproduced.
    public static List<Heading> headings(String chapter) {
        var headings = new ArrayList<Heading>();
        var fenced = false;
        for (var line : read(chapter + ".md").lines().toList()) {
            if (line.stripLeading().startsWith("```")) {
                fenced = !fenced;
                continue;
            }
            var matcher = HEADING.matcher(line);
            if (fenced || !matcher.matches()) {
                continue;
            }
            var text = matcher.group(2);
            headings.add(new Heading(matcher.group(1).length(), text, anchorOf(text)));
        }
        return List.copyOf(headings);
    }

    /// Whether `link` lands on a chapter the summary lists, and on a heading it has.
    public static boolean resolves(DocLink link) {
        if (!chapters().contains(link.page())) {
            return false;
        }
        return link.anchor().isEmpty()
                || headings(link.page()).stream()
                        .anyMatch(heading -> heading.anchor().equals(link.anchor()));
    }

    /// Every section a card has to show, by the rules above. A chapter that only
    /// needs a link is in the list as the chapter itself.
    public static List<DocLink> required() {
        var required = new ArrayList<DocLink>();
        for (var chapter : chapters()) {
            var deepest = WIDGET_CHAPTERS.contains(chapter) ? 2 : FEATURE_CHAPTERS.contains(chapter) ? 3 : 0;
            if (deepest == 0) {
                required.add(DocLink.page(chapter));
                continue;
            }
            headings(chapter).stream()
                    .filter(heading -> heading.level() >= 2 && heading.level() <= deepest)
                    .map(Heading::anchor)
                    .filter(anchor -> !NEVER.contains(anchor))
                    .distinct()
                    .forEach(anchor -> required.add(DocLink.to(chapter, anchor)));
        }
        return List.copyOf(required);
    }

    /// Whether `links` shows `section`: the same heading, or for a chapter that
    /// only needs a link, any link into it.
    public static boolean covers(Set<DocLink> links, DocLink section) {
        if (section.anchor().isEmpty()) {
            return links.stream().anyMatch(link -> link.page().equals(section.page()));
        }
        return links.contains(section);
    }

    /// mdBook's anchor for a heading: code marks and HTML dropped, letters, digits,
    /// `_` and `-` kept in lower case, one `-` per whitespace character, the rest
    /// dropped.
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

    private static String read(String relative) {
        try {
            return Files.readString(root().resolve(relative));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
