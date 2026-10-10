package dev.goldberry.build.ci;

import dev.goldberry.build.repository.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A change to {@code site/} alone runs {@code pages.yml} and nothing else, held as
 * text.
 *
 * <p>Every rule here fails quietly when broken. A workflow that loses its
 * {@code paths-ignore} builds three platforms and publishes a snapshot for a
 * typo on the landing page, and turns nothing red. A workflow that ignores
 * {@code book/} skips the guards that hold the book to the code. And
 * {@code pages.yml} that stops running {@code SiteTest} leaves a site-only pull
 * request with no check of the landing page's links into the book at all.
 */
@DisplayName("a change to the landing page alone")
class SiteOnlyChangeTest {

    private static final String SITE = "'site/**'";

    /**
     * The events under a workflow's top-level {@code on:}, each with the lines
     * indented beneath it.
     */
    record Triggers(Map<String, String> events) {

        private static final Pattern EVENT = Pattern.compile("^ {2}([a-z_]+):.*$");

        static Triggers of(String workflow) {
            var events = new LinkedHashMap<String, String>();
            var inOn = false;
            String event = null;
            for (var line : workflow.lines().toList()) {
                if (line.equals("on:")) {
                    inOn = true;
                    continue;
                }
                if (!inOn || line.isBlank() || line.stripLeading().startsWith("#")) {
                    continue;
                }
                if (!line.startsWith(" ")) {
                    break;
                }
                var matcher = EVENT.matcher(line);
                if (matcher.matches()) {
                    event = matcher.group(1);
                    events.put(event, "");
                } else if (event != null) {
                    events.merge(event, line.strip() + "\n", String::concat);
                }
            }
            return new Triggers(Map.copyOf(events));
        }

        Optional<String> event(String name) {
            return Optional.ofNullable(events.get(name));
        }

        /** Whether {@code body} is a filter GitHub applies to changed paths. */
        static boolean ignoresSite(String body) {
            return body.contains("paths-ignore:") && body.contains("- " + SITE);
        }

        /**
         * A push only of tags. GitHub does not evaluate a path filter for a tag,
         * so there is none to write.
         */
        static boolean tagsOnly(String body) {
            return body.contains("tags:") && !body.contains("branches:");
        }

        /** An allow-list that names directories other than {@code site/}. */
        static boolean allowListWithoutSite(String body) {
            return body.contains("paths:") && !body.contains("site/");
        }
    }

    @Nested
    @DisplayName("the trigger reader")
    class Reader {

        @Test
        @DisplayName("splits on: into its events and stops at the next top-level key")
        void readsEvents() {
            var triggers = Triggers.of("""
                    name: X

                    on:
                      pull_request:
                        # a comment
                        paths-ignore:
                          - 'site/**'
                      workflow_call:
                      push:
                        tags: ['v*']

                    jobs:
                      build:
                        steps: []
                    """);
            assertAll(
                    () -> assertEquals(List.of("pull_request", "push", "workflow_call"),
                            triggers.events().keySet().stream().sorted().toList()),
                    () -> assertTrue(Triggers.ignoresSite(triggers.event("pull_request").orElseThrow())),
                    () -> assertEquals("", triggers.event("workflow_call").orElseThrow()),
                    () -> assertTrue(Triggers.tagsOnly(triggers.event("push").orElseThrow())),
                    () -> assertTrue(triggers.event("jobs").isEmpty()));
        }

        @Test
        @DisplayName("does not take paths: for paths-ignore:, or the other way round")
        void tellsTheFiltersApart() {
            assertAll(
                    () -> assertFalse(Triggers.ignoresSite("paths:\n- 'site/**'\n")),
                    () -> assertFalse(Triggers.allowListWithoutSite("paths-ignore:\n- 'site/**'\n")),
                    () -> assertTrue(Triggers.allowListWithoutSite("paths:\n- 'media/**'\n")));
        }
    }

    @ParameterizedTest(name = "{0} on {1}")
    @CsvSource({
            "linux.yml, pull_request",
            "macos.yml, pull_request",
            "windows.yml, pull_request",
            "codeql.yml, pull_request",
            "qodana.yml, pull_request",
            "qodana.yml, push",
            "snapshot.yml, push",
    })
    @DisplayName("is ignored by every workflow that builds the library")
    void heavyWorkflowsIgnoreTheSite(String name, String event) {
        var body = Triggers.of(Repository.workflow(name)).event(event);
        assertTrue(body.isPresent(), name + " has no " + event + " trigger");
        assertTrue(Triggers.ignoresSite(body.orElseThrow()),
                name + " builds on a " + event + " that touches only site/; add paths-ignore: - " + SITE);
    }

    @Test
    @DisplayName("starts no workflow but pages.yml, however many are added")
    void onlyPagesRuns() {
        var offenders = Repository.workflowNames().stream()
                .filter(name -> !name.equals("pages.yml"))
                .flatMap(name -> {
                    var triggers = Triggers.of(Repository.workflow(name));
                    return List.of("push", "pull_request").stream()
                            .flatMap(event -> triggers.event(event).stream()
                                    .filter(body -> !Triggers.ignoresSite(body)
                                            && !Triggers.tagsOnly(body)
                                            && !Triggers.allowListWithoutSite(body))
                                    .map(body -> name + " on " + event));
                })
                .toList();
        assertEquals(List.of(), offenders,
                "these run on a change to site/ alone; ignore 'site/**' or filter to their own paths");
    }

    @Test
    @DisplayName("is never confused with a change to the book, which the Java build checks")
    void bookIsNeverIgnored() {
        var offenders = Repository.workflowNames().stream()
                .filter(name -> Triggers.of(Repository.workflow(name)).events().values().stream()
                        .anyMatch(body -> body.contains("paths-ignore:") && body.contains("book/")))
                .toList();
        assertEquals(List.of(), offenders,
                "BookTest and BookMarkupTest hold book/ to the code; a book-only change must still build");
    }

    @Test
    @DisplayName("still runs SiteTest, in pages.yml, on a pull request and a push to master")
    void pagesRunsSiteTest() {
        var text = Repository.workflow("pages.yml");
        var triggers = Triggers.of(text);
        assertAll(
                () -> assertTrue(triggers.event("pull_request").orElseThrow().contains("- \"site/**\"")),
                () -> assertTrue(triggers.event("push").orElseThrow().contains("- \"site/**\"")),
                () -> assertTrue(text.contains("./gradlew -p build-logic test --tests 'dev.goldberry.build.site.*'"),
                        "pages.yml no longer runs SiteTest, and nothing else does on a site-only change"),
                () -> assertTrue(text.contains("java-version: '25'"),
                        "pages.yml runs Gradle without the JDK the build needs"));
    }
}
