package io.github.digitalsmile.goldberry.build.site;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

import io.github.digitalsmile.goldberry.build.repository.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * goldberry.dev, held to what ADR-0509 says about it: the landing page at
 * {@code /}, the book at {@code /docs/}, both from this repository.
 *
 * <p>Pages builds only on a push that touches {@code site/} or {@code book/}, so a
 * chapter renamed in a commit that touches neither page leaves a 404 on the live
 * site until somebody happens to click it. The workflow checks its own links once
 * the book is built; this checks them without mdBook, in the build everybody runs.
 */
@DisplayName("the goldberry.dev site")
class SiteTest {

    private static final String DOMAIN = "goldberry.dev";

    /** A quoted link into the book, as the content files write one. */
    private static final Pattern DOCS_LINK = Pattern.compile("\"(docs/[^\"]*)\"");

    /** A chapter as {@code SUMMARY.md} lists it: {@code [Title](path.md)}. */
    private static final Pattern CHAPTER = Pattern.compile("\\]\\(([^)]+\\.md)\\)");

    private static final Pattern NEWS_DATE = Pattern.compile("date: \"(\\d{4}-\\d{2}-\\d{2})\"");

    private static final List<String> CONTENT = List.of("site/content.js", "site/news.js");

    private static List<String> matches(Pattern pattern, String text) {
        return pattern.matcher(text).results().map(result -> result.group(1)).toList();
    }

    @Nested
    @DisplayName("the domain")
    class Domain {

        @Test
        @DisplayName("is goldberry.dev in CNAME and in the canonical URL the page writes into its head")
        void oneDomain() {
            assertAll(
                    () -> assertEquals(DOMAIN, Repository.read("site/CNAME").strip()),
                    () -> assertTrue(
                            Repository.read("site/content.js").contains("url: \"https://" + DOMAIN + "/\""),
                            "seo.url in site/content.js is not https://" + DOMAIN + "/"),
                    () -> assertTrue(Repository.exists("site/.nojekyll"), "site/.nojekyll is gone"));
        }
    }

    @Nested
    @DisplayName("a link from the landing page into the book")
    class LinksIntoTheBook {

        private final String summary = Repository.read("book/src/SUMMARY.md");

        private List<String> links() {
            return CONTENT.stream()
                    .flatMap(file -> matches(DOCS_LINK, Repository.read(file)).stream())
                    .distinct()
                    .toList();
        }

        @Test
        @DisplayName("is one of the shapes the book is published under")
        void hasAKnownShape() {
            var unknown =
                    links().stream().filter(link -> BookLink.parse(link).isEmpty()).toList();
            assertTrue(unknown.isEmpty(), () -> "links into docs/ that name no page of the book: " + unknown);
        }

        @Test
        @DisplayName("lands on a chapter that exists and that SUMMARY.md lists, so mdBook renders it")
        void landsOnAListedChapter() {
            var broken = links().stream()
                    .map(BookLink::parse)
                    .flatMap(Optional::stream)
                    .map(BookLink::source)
                    .flatMap(Optional::stream)
                    .filter(source -> !Repository.exists("book/src/" + source) || !summary.contains("](" + source + ")"))
                    .toList();
            assertTrue(broken.isEmpty(), () -> "the landing page links to chapters the book does not build: " + broken);
        }

        @Test
        @DisplayName("to docs/ lands on the book's first chapter, which mdBook makes its index")
        void frontPageExists() {
            var chapters = matches(CHAPTER, summary);
            assertTrue(!chapters.isEmpty() && Repository.exists("book/src/" + chapters.getFirst()));
        }
    }

    @Nested
    @DisplayName("the book")
    class Book {

        @Test
        @DisplayName("ends with its suffix chapter: mdBook refuses a list after one")
        void templateIsLast() {
            var summary = Repository.read("book/src/SUMMARY.md").strip();
            var lastLine = summary.substring(summary.lastIndexOf('\n') + 1);
            assertEquals("[Template](adr/0000-template.md)", lastLine);
        }
    }

    @Nested
    @DisplayName("the news")
    class News {

        @Test
        @DisplayName("is newest first, because the first entry is the pill above the headline")
        void newestFirst() {
            var dates = matches(NEWS_DATE, Repository.read("site/news.js")).stream()
                    .map(LocalDate::parse)
                    .toList();
            var outOfOrder = dates.stream()
                    .gather(Gatherers.windowSliding(2))
                    .filter(pair -> pair.getFirst().isBefore(pair.getLast()))
                    .toList();
            assertAll(
                    () -> assertTrue(!dates.isEmpty(), "site/news.js has no dated entry"),
                    () -> assertTrue(outOfOrder.isEmpty(), () -> "older entries above newer ones: " + outOfOrder));
        }
    }

    @Nested
    @DisplayName("pages.yml")
    class Workflow {

        private final String text = Repository.workflow("pages.yml");

        @Test
        @DisplayName("rebuilds on every input the page is made from")
        void watchesItsInputs() {
            assertAll(Stream.of("\"site/**\"", "\"book/**\"", "\"gradle.properties\"", "\".github/workflows/pages.yml\"")
                    .map(path -> () -> assertTrue(text.contains("- " + path), "pages.yml does not watch " + path)));
        }

        @Test
        @DisplayName("stamps the version gradle.properties declares, so it is typed once")
        void readsTheVersion() {
            var declared = Pattern.compile("(?m)^goldberryVersion=\\S+$");
            assertAll(
                    () -> assertTrue(text.contains("s/^goldberryVersion=//p' gradle.properties")),
                    () -> assertTrue(declared.matcher(Repository.read("gradle.properties")).find()));
        }

        @Test
        @DisplayName("pins mdBook and tells the book it lives under /docs/")
        void pinsMdBook() {
            Matcher version = Pattern.compile("MDBOOK_VERSION: \"(\\d+\\.\\d+\\.\\d+)\"").matcher(text);
            assertAll(
                    () -> assertTrue(version.find(), "MDBOOK_VERSION is not an exact x.y.z"),
                    () -> assertTrue(text.contains("MDBOOK_OUTPUT__HTML__SITE_URL: /docs/")),
                    () -> assertTrue(text.contains("mdbook build book -d \"$PWD/_site/docs\"")));
        }

        @Test
        @DisplayName("checks the links of both content files once the book is built")
        void checksBothContentFiles() {
            assertAll(CONTENT.stream()
                    .map(file -> () -> assertTrue(text.contains(file), "pages.yml does not check the links in " + file)));
        }

        @Test
        @DisplayName("deploys only from master, never from a pull request")
        void pullRequestsDoNotDeploy() {
            assertAll(
                    () -> assertTrue(text.contains("if: github.event_name != 'pull_request'")),
                    () -> assertTrue(text.contains("branches: [master]")),
                    () -> assertTrue(text.contains("pages: write")),
                    () -> assertTrue(text.contains("id-token: write")));
        }
    }
}
