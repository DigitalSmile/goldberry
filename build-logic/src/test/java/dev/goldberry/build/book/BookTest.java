package dev.goldberry.build.book;

import java.util.List;
import java.util.Map;
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
 * The guide at goldberry.dev/docs/, held to what {@code docs/book.md} says about
 * it: every chapter is listed and every listed chapter exists, every link lands,
 * and every widget the repository ships has a heading of its own.
 *
 * <p>A drift guard in the sense of ADR-0082. mdBook builds a book with a dead
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
        @DisplayName("keeps the decision log out of search, which is where the index's size went")
        void searchSkipsTheLog() {
            assertAll(
                    () -> assertTrue(config.contains("[output.html.search.chapter]")),
                    () -> assertTrue(config.contains("\"adr/\" = { enable = false }")));
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
