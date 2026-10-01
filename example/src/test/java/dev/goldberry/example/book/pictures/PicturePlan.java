package dev.goldberry.example.book.pictures;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// Which widgets the guide pictures, read from the guide itself.
///
/// Under every heading that is exactly a markup name in code marks —
/// `` ## `button` `` — the first fenced block is the widget's sample
/// (`docs/book.md`). When it is a plain `kdl` block the widget is pictured from
/// it, in both shades, and the picture is placed under the heading. When the
/// author fenced it `kdl,ignore`, the sample needs something bound — a player, a
/// renderer — that a preview cannot supply, and the heading is skipped with
/// that said.
///
/// Reading the guide rather than listing names here is what makes a new widget
/// turn up: the chapter it is documented in is the one place its author
/// writes, and `BookPicturesTest` fails until the picture is taken.
public final class PicturePlan {

    /// The parts of the guide a widget is documented in.
    static final Set<String> CATALOGUE = Set.of("layout", "components");

    private static final Pattern WIDGET_HEADING = Pattern.compile("^#{2,3} `([a-z0-9-]+)`\\s*$");
    private static final Pattern ANY_HEADING = Pattern.compile("^#{1,6} .*$");
    private static final Pattern FENCE = Pattern.compile("^```(\\S*)\\s*$");

    private PicturePlan() {}

    /// A heading in the catalogue, and what became of it.
    public sealed interface Entry permits Pictured, Skipped {

        /// The markup name the heading is.
        String name();
    }

    /// A heading whose sample is a document, so the widget is pictured from it.
    public record Pictured(WidgetPicture picture) implements Entry {

        @Override
        public String name() {
            return picture.name();
        }
    }

    /// A heading the guide cannot picture, and why.
    public record Skipped(String name, String chapter, String reason) implements Entry {}

    /// Every widget heading under `root`'s catalogue parts, in chapter order.
    public static List<Entry> read(Path root) {
        return chapters(root).stream()
                .flatMap(chapter ->
                        read(root.relativize(chapter).toString().replace('\\', '/'), lines(chapter)).stream())
                .toList();
    }

    /// The headings of one chapter, given its lines. Package-private so a test
    /// can hand over a chapter it wrote.
    static List<Entry> read(String chapter, List<String> lines) {
        var entries = new ArrayList<Entry>();
        var i = 0;
        while (i < lines.size()) {
            var heading = WIDGET_HEADING.matcher(lines.get(i));
            if (!heading.matches()) {
                i++;
                continue;
            }
            var name = heading.group(1);
            var j = i + 1;
            Entry entry = new Skipped(name, chapter, "no fenced sample under the heading");
            while (j < lines.size() && !ANY_HEADING.matcher(lines.get(j)).matches()) {
                var fence = FENCE.matcher(lines.get(j));
                if (fence.matches()) {
                    entry = sample(name, chapter, lines, j, fence.group(1));
                    break;
                }
                j++;
            }
            entries.add(entry);
            i = j;
        }
        return entries;
    }

    private static Entry sample(String name, String chapter, List<String> lines, int fenceLine, String info) {
        var language = info.contains(",") ? info.substring(0, info.indexOf(',')) : info;
        if (!language.equals("kdl")) {
            return new Skipped(name, chapter, "its first sample is " + (info.isEmpty() ? "plain" : info) + ", not kdl");
        }
        if (info.contains("ignore")) {
            return new Skipped(
                    name, chapter, "its sample is kdl,ignore: it needs something bound that a preview cannot supply");
        }
        var body = new StringBuilder();
        for (var k = fenceLine + 1; k < lines.size() && !lines.get(k).strip().equals("```"); k++) {
            body.append(lines.get(k)).append('\n');
        }
        return new Pictured(new WidgetPicture(name, chapter, fenceLine + 1, body.toString()));
    }

    private static List<Path> chapters(Path root) {
        var chapters = new ArrayList<Path>();
        for (var part : CATALOGUE.stream().sorted().toList()) {
            try (Stream<Path> files = Files.list(root.resolve(part))) {
                files.filter(file -> file.toString().endsWith(".md")).sorted().forEach(chapters::add);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot list " + root.resolve(part), e);
            }
        }
        return chapters;
    }

    private static List<String> lines(Path chapter) {
        try {
            return Files.readAllLines(chapter);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + chapter, e);
        }
    }
}
