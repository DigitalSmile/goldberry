package dev.goldberry.build.book;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.goldberry.build.repository.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guide at goldberry.dev/docs/, held to its own rules: every chapter is
 * listed and every listed chapter exists, every link lands, and every widget the
 * repository ships has a heading of its own.
 *
 * <p>A drift guard: a fact that has to be true in two places gets a test that
 * reads both. mdBook builds a book with a dead
 * link and a sidebar with a chapter whose page says something else, and the
 * Pages workflow checks only the landing page's links into the book. The
 * decision log is a different document with different rules, and
 * {@code DecisionLogTest} holds it; this holds the guide, which is everything
 * else under {@code book/src}.
 */
@DisplayName("the guide")
class BookTest {

    /** The chapters the guide's rules apply to: everything listed that is not a record. */
    private static List<Book.Chapter> guide() {
        return Book.chapters().stream().filter(chapter -> !chapter.inTheLog()).toList();
    }

    /** The parts of the guide a widget may be documented in. */
    private static final Set<String> CATALOGUE = Set.of("layout/", "components/");

    @Nested
    @DisplayName("the summary")
    class Summary {

        @Test
        @DisplayName("lists only chapters that exist, so mdBook does not invent an empty one")
        void everyListedChapterExists() {
            var missing = Book.chapters().stream()
                    .map(Book.Chapter::path)
                    .filter(path -> !Book.exists(path))
                    .toList();
            assertTrue(missing.isEmpty(), () -> "SUMMARY.md lists chapters that do not exist: " + missing);
        }

        @Test
        @DisplayName("lists every chapter that exists, so a written page is a reachable page")
        void everyPageIsListed() {
            var listed = Book.chapters().stream().map(Book.Chapter::path).collect(Collectors.toSet());
            var orphans = Book.pages().stream()
                    .filter(page -> !page.equals("SUMMARY.md"))
                    .filter(page -> !listed.contains(page))
                    .toList();
            assertTrue(orphans.isEmpty(), () -> "pages under book/src that SUMMARY.md does not list: " + orphans);
        }

        @Test
        @DisplayName("gives each chapter the title its own first heading gives it")
        void titlesAgree() {
            var disagreeing = guide().stream()
                    .filter(chapter -> !chapter.title().equals(Book.title(chapter.path()).orElse("")))
                    .map(chapter -> chapter.path() + ": SUMMARY says \"" + chapter.title() + "\", the page says \""
                            + Book.title(chapter.path()).orElse("<no heading>") + "\"")
                    .toList();
            assertTrue(disagreeing.isEmpty(), () -> "the sidebar and the page disagree: " + disagreeing);
        }
    }

    @Nested
    @DisplayName("a link in the guide")
    class Links {

        @Test
        @DisplayName("lands on a file that exists")
        void landsOnAFile() {
            var broken = guide().stream()
                    .flatMap(chapter -> Book.links(chapter.path()).stream())
                    .filter(link -> !link.external() && !link.onThisPage())
                    .filter(link -> !Book.exists(link.resolved().toString().replace('\\', '/')))
                    .map(link -> link.chapter() + " -> " + link.href())
                    .toList();
            assertTrue(broken.isEmpty(), () -> "links to files the book does not have: " + broken);
        }

        @Test
        @DisplayName("to a record points at GitHub and at a record that exists")
        void aRecordLinkLandsOnARecord() {
            var links = guide().stream()
                    .flatMap(chapter -> Book.links(chapter.path()).stream())
                    .toList();
            var relative = links.stream()
                    .filter(link -> !link.external() && link.target().contains("adr/"))
                    .map(link -> link.chapter() + " -> " + link.href())
                    .toList();
            var missing = links.stream()
                    .filter(link -> link.record().isPresent())
                    .filter(link -> !Book.exists(link.record().orElseThrow()))
                    .map(link -> link.chapter() + " -> " + link.href())
                    .toList();
            assertAll(
                    () -> assertTrue(relative.isEmpty(), () -> "records linked as pages of the book: " + relative),
                    () -> assertTrue(missing.isEmpty(), () -> "records on GitHub that do not exist: " + missing));
        }

        @Test
        @DisplayName("names a heading the page it lands on actually has")
        void landsOnAHeading() {
            var broken = guide().stream()
                    .flatMap(chapter -> Book.links(chapter.path()).stream())
                    .filter(link -> !link.external() && link.fragment().isPresent())
                    .filter(link -> {
                        var page = link.onThisPage()
                                ? link.chapter()
                                : link.resolved().toString().replace('\\', '/');
                        return Book.exists(page) && page.endsWith(".md")
                                && !Book.anchors(page).contains(link.fragment().orElseThrow());
                    })
                    .map(link -> link.chapter() + " -> " + link.href())
                    .toList();
            assertTrue(broken.isEmpty(), () -> "links to headings the page does not have: " + broken);
        }
    }

    @Nested
    @DisplayName("a widget")
    class Widgets {

        /** The chapter each name is documented in, by the heading that is exactly the name in code marks. */
        private Map<String, List<String>> headingsByName() {
            return guide().stream()
                    .filter(chapter -> CATALOGUE.stream().anyMatch(part -> chapter.path().startsWith(part)))
                    .flatMap(chapter -> Book.headings(chapter.path()).stream()
                            .filter(heading -> heading.matches("`[a-z0-9-]+`"))
                            .map(heading -> Map.entry(
                                    heading.substring(1, heading.length() - 1), chapter.path())))
                    .collect(Collectors.groupingBy(
                            Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        }

        @Test
        @DisplayName("that a document can name has exactly one heading in Layout or Components")
        void everyMarkupNameHasAHeading() {
            var headings = headingsByName();
            var names = MarkupNames.all();
            var undocumented = names.stream().filter(name -> !headings.containsKey(name)).toList();
            var twice = names.stream().filter(name -> headings.getOrDefault(name, List.of()).size() > 1).toList();
            assertAll(
                    () -> assertTrue(!names.isEmpty(), "no @Markup names found; MarkupNames.MODULES is wrong"),
                    () -> assertTrue(
                            undocumented.isEmpty(), () -> "@Markup names with no `name` heading: " + undocumented),
                    () -> assertTrue(twice.isEmpty(), () -> "names documented under two headings: " + twice));
        }

        @Test
        @DisplayName("heading names a node the repository ships, so a renamed widget renames its chapter")
        void everyHeadingIsAMarkupName() {
            var names = Set.copyOf(MarkupNames.all());
            var stale = headingsByName().keySet().stream().filter(name -> !names.contains(name)).sorted().toList();
            assertTrue(stale.isEmpty(), () -> "headings in code marks that are not @Markup names: " + stale);
        }

        @Test
        @DisplayName("is listed in the catalogue with a link to its heading")
        void theCatalogueListsEveryName() {
            var catalogue = Book.text("components/index.md");
            var links = Book.links("components/index.md").stream()
                    .filter(link -> link.fragment().isPresent())
                    .collect(Collectors.toMap(
                            link -> link.fragment().orElseThrow(), Function.identity(), (first, _) -> first));
            var unlisted = MarkupNames.all().stream()
                    .filter(name -> !catalogue.contains("`" + name + "`") || !links.containsKey(name))
                    .toList();
            assertTrue(unlisted.isEmpty(), () -> "names the catalogue does not list with a link: " + unlisted);
        }
    }

    @Nested
    @DisplayName("a java sample")
    class JavaSamples {

        @Test
        @DisplayName("closes a multi-line argument list on its own line, at the indent that opened it")
        void closesOnItsOwnLine() {
            var faults = guide().stream()
                    .flatMap(chapter -> Book.samples(chapter.path()).stream())
                    .filter(sample -> sample.language().equals("java"))
                    .flatMap(sample -> Book.bracketFaults(sample).stream())
                    .toList();
            assertTrue(
                    faults.isEmpty(), () -> "a closing bracket that shares a line with the last argument: " + faults);
        }
    }

    @Nested
    @DisplayName("a picture")
    class Pictures {

        /**
         * The pictures shown in one shade only, and why: each is a state no
         * sample reaches on its own, taken by a test of its own at one scale.
         * Everything else is a light and a dark picture at 2x.
         */
        private static final Set<String> ONE_SHADE = Set.of(
                "affix-pinned.png", // the showcase after seven lines of wheel
                "gallery-drawing.png", // pinned at one scale: a decoded bitmap and a QR code
                "gallery-gpu.png", // the GPU lane's picture
                "media-player-subtitles.png", // a player mid-stream
                "toast-dark.png"); // three toasts in flight

        private List<Book.Shot> shots() {
            return guide().stream().flatMap(chapter -> Book.shots(chapter.path()).stream()).toList();
        }

        @Test
        @DisplayName("is a light and a dark picture, with the same alt text and the same width")
        void comesInBothShades() {
            var faults = shots().stream()
                    .flatMap(shot -> {
                        var light = shot.pictures().stream().filter(p -> p.cls().equals(Optional.of("gb-light"))).toList();
                        var dark = shot.pictures().stream().filter(p -> p.cls().equals(Optional.of("gb-dark"))).toList();
                        var plain = shot.pictures().stream().filter(p -> p.cls().isEmpty()).toList();
                        var where = shot.chapter() + ":" + shot.line();
                        var problems = new java.util.ArrayList<String>();
                        if (light.size() != dark.size() || light.size() > 1) {
                            problems.add(where + " has " + light.size() + " light and " + dark.size() + " dark pictures");
                        } else if (light.size() == 1) {
                            var l = light.getFirst();
                            var d = dark.getFirst();
                            if (!l.alt().equals(d.alt()) || !l.width().equals(d.width())) {
                                problems.add(where + ": the two shades disagree on alt or width");
                            }
                            if (!l.file().orElse("").endsWith("-light.webp") || !d.file().orElse("").endsWith("-dark.webp")) {
                                problems.add(where + ": a shade's file is not named for it");
                            }
                        }
                        plain.stream()
                                .filter(p -> !ONE_SHADE.contains(p.file().orElse("")))
                                .forEach(p -> problems.add(where + ": " + p.src() + " is shown in one shade only"));
                        if (shot.pictures().isEmpty()) {
                            problems.add(where + " has no picture");
                        }
                        return problems.stream();
                    })
                    .toList();
            assertTrue(faults.isEmpty(), () -> "shots that are not a pair: " + faults);
        }

        @Test
        @DisplayName("names a file that exists, and every file is shown somewhere")
        void filesAndShotsAgree() {
            var files = Set.copyOf(Book.imageFiles());
            var shown = shots().stream()
                    .flatMap(shot -> shot.pictures().stream())
                    .map(p -> p.file().orElse(p.src()))
                    .collect(Collectors.toSet());
            var missing = shown.stream().filter(file -> !files.contains(file)).sorted().toList();
            var orphans = files.stream().filter(file -> !shown.contains(file)).sorted().toList();
            assertAll(
                    () -> assertTrue(missing.isEmpty(), () -> "pictures the guide shows that do not exist: " + missing),
                    () -> assertTrue(orphans.isEmpty(), () -> "files under images/ no chapter shows: " + orphans));
        }

        @Test
        @DisplayName("of a widget carries the logical width it was taken at, half the file's pixels, and sits under its heading")
        void widthIsLogical() {
            var faults = guide().stream()
                    .flatMap(chapter -> Book.shots(chapter.path()).stream())
                    .flatMap(shot -> shot.pictures().stream()
                            .filter(p -> p.width().isPresent())
                            .map(p -> {
                                var file = p.file().orElseThrow();
                                var expected = Book.webpWidth(file) / 2;
                                return Integer.parseInt(p.width().orElseThrow()) == expected
                                        ? null
                                        : shot.chapter() + ":" + shot.line() + " " + file + " says width " + p.width().orElseThrow() + ", the file is " + expected * 2 + " px";
                            })
                            .filter(java.util.Objects::nonNull))
                    .toList();
            assertTrue(faults.isEmpty(), () -> "pictures whose width is not the logical one: " + faults);
        }

        @Test
        @DisplayName("of a widget is shown under that widget's own heading")
        void underItsHeading() {
            var misplaced = guide().stream()
                    .filter(chapter -> CATALOGUE.stream().anyMatch(part -> chapter.path().startsWith(part)))
                    .flatMap(chapter -> {
                        var lines = Book.text(chapter.path()).lines().toList();
                        return Book.shots(chapter.path()).stream()
                                .flatMap(shot -> shot.pictures().stream()
                                        .filter(p -> p.cls().equals(Optional.of("gb-light")))
                                        .map(p -> p.file().orElse("").replace("-light.webp", ""))
                                        .filter(name -> !name.startsWith("screen-") && !name.startsWith("diagram-"))
                                        .filter(name -> !headingAbove(lines, shot.line()).equals("`" + name + "`"))
                                        .map(name -> shot.chapter() + ":" + shot.line() + " shows " + name + " under "
                                                + headingAbove(lines, shot.line())));
                    })
                    .toList();
            assertTrue(misplaced.isEmpty(), () -> "pictures under another widget's heading: " + misplaced);
        }

        private static String headingAbove(List<String> lines, int line) {
            for (var k = line - 1; k >= 0; k--) {
                var heading = lines.get(k);
                if (heading.startsWith("#")) {
                    return heading.replaceFirst("^#+\\s+", "").strip();
                }
            }
            return "";
        }
    }

    @Nested
    @DisplayName("a tab group")
    class Tabs {

        private List<Book.TabGroup> groups() {
            return guide().stream().flatMap(chapter -> Book.tabGroups(chapter.path()).stream()).toList();
        }

        @Test
        @DisplayName("holds two or more samples in different languages and nothing else")
        void holdsSamplesOnly() {
            var faults = groups().stream()
                    .filter(group -> group.samples().size() < 2
                            || group.samples().stream().map(Book.Sample::language).distinct().count() < 2
                            || !group.otherLines().isEmpty())
                    .map(group -> group.chapter() + ":" + group.line())
                    .toList();
            assertTrue(faults.isEmpty(), () -> "tab groups that are not two or more samples: " + faults);
        }

        /**
         * Where the rule holds: the catalogue, whose style is a markup sample
         * and then the Java that builds the same tree, and the concept page that
         * shows the pair first. A guide chapter may put a document beside the
         * class that loads it, which is two things and not one said twice.
         */
        private static final Set<String> TABBED = Set.of("layout/", "components/", "overview/concept.md");

        @Test
        @DisplayName("is what a markup sample and the Java beside it are wrapped in, in the catalogue")
        void everyPairIsTabbed() {
            var loose = guide().stream()
                    .filter(chapter -> TABBED.stream().anyMatch(part -> chapter.path().startsWith(part)))
                    .flatMap(chapter -> {
                        var samples = Book.samples(chapter.path());
                        var lines = Book.text(chapter.path()).lines().toList();
                        var tabbed = Book.tabGroups(chapter.path()).stream()
                                .flatMap(group -> group.samples().stream())
                                .map(Book.Sample::line)
                                .collect(Collectors.toSet());
                        var found = new java.util.ArrayList<String>();
                        for (var i = 0; i + 1 < samples.size(); i++) {
                            var first = samples.get(i);
                            var second = samples.get(i + 1);
                            if (!first.language().equals("kdl") || !second.language().equals("java")) {
                                continue;
                            }
                            var end = first.line() + (int) first.text().lines().count() + 1;
                            var between = lines.subList(end, second.line() - 1);
                            if (between.stream().allMatch(String::isBlank) && !tabbed.contains(first.line())) {
                                found.add(chapter.path() + ":" + first.line());
                            }
                        }
                        return found.stream();
                    })
                    .toList();
            assertTrue(loose.isEmpty(), () -> "a kdl sample and its Java not in a gb-tabs block: " + loose);
        }

        @Test
        @DisplayName("is built by the theme's script, and the old side-by-side block is gone")
        void theThemeBuildsIt() {
            var pairs = guide().stream()
                    .filter(chapter -> Book.text(chapter.path()).contains("gb-pair"))
                    .map(Book.Chapter::path)
                    .toList();
            assertAll(
                    () -> assertTrue(Repository.read("book/theme/goldberry.js").contains("gb-tabs")),
                    () -> assertTrue(Repository.read("book/theme/goldberry.css").contains(".gb-tabs")),
                    () -> assertTrue(Repository.read("book/theme/goldberry.css").contains("html.navy .gb-shot .gb-dark")),
                    () -> assertTrue(pairs.isEmpty(), () -> "chapters still using gb-pair: " + pairs));
        }
    }

    @Nested
    @DisplayName("book.toml")
    class Configuration {

        private final String config = Repository.read("book/book.toml");

        @Test
        @DisplayName("loads the theme the landing page's colours are in, and the file exists")
        void loadsTheTheme() {
            assertAll(
                    () -> assertTrue(config.contains("additional-css = [\"theme/goldberry.css\"]")),
                    () -> assertTrue(Repository.exists("book/theme/goldberry.css")),
                    () -> assertTrue(Repository.exists("book/theme/favicon.svg")));
        }

        @Test
        @DisplayName("loads the script that builds the landing page's header, and the file exists")
        void loadsTheHeader() {
            assertAll(
                    () -> assertTrue(config.contains("additional-js = [\"theme/goldberry.js\"]")),
                    () -> assertTrue(Repository.exists("book/theme/goldberry.js")));
        }

        @Test
        @DisplayName("opens on the light theme and falls to navy in the dark, the two the stylesheet recolours")
        void twoThemes() {
            assertAll(
                    () -> assertEquals(1, config.lines()
                            .filter(line -> line.equals("default-theme = \"light\""))
                            .count()),
                    () -> assertEquals(1, config.lines()
                            .filter(line -> line.equals("preferred-dark-theme = \"navy\""))
                            .count()));
        }
    }
}
