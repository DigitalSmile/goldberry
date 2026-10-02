package dev.goldberry.build.book;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.build.repository.Repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// A doc comment is written for the reader of the published javadoc, who has a
/// browser and not the repository. So a source explains the object and links
/// the guide at `goldberry.dev/docs`, and never cites the decision log, whose
/// pages are read on GitHub and are not where a user wants to land.
///
/// Three rules, each read as text:
///
/// - no record number in a Java, Gradle or workflow source, outside the test
///   that manages the log;
/// - every guide link in a Java source lands on a chapter the summary lists
///   and, when it names a heading, on a heading that chapter has;
/// - every `package-info.java` of a published module links the guide, so no
///   page of the javadoc is more than a click from it;
/// - no Java source names a working document under `docs/` by file: those are
///   notes in the repository, not pages a reader of the javadoc has.
///
/// The house style is `book/src/contributing/doc-comments.md`.
@DisplayName("a doc comment")
class SourceDocsTest {

    /// A record cited by number, with or without the hyphen, or by the bare word.
    private static final Pattern RECORD = Pattern.compile("\\bADR-?\\s?\\d{3,4}\\b|\\bADRs?\\b");

    /// The sources that may say "ADR": the test that holds the log to its own
    /// rules builds the heading it looks for, and this test says what it forbids.
    private static final Set<String> MAY_CITE = Set.of(
            "build-logic/src/test/java/dev/goldberry/build/repository/DecisionLogTest.java",
            "build-logic/src/test/java/dev/goldberry/build/book/SourceDocsTest.java");

    /// A working document under `docs/`, named by file.
    private static final Pattern WORKING_DOCUMENT = Pattern.compile("\\bdocs/[a-z0-9-]+\\.md\\b");

    /// A module applies the publishing conventions, so its javadoc is shipped.
    private static final Pattern PUBLISHED = Pattern.compile("id\\s+'goldberry\\.publish'");

    @Test
    @DisplayName("does not cite a record, which its reader cannot follow")
    void citesNoRecord() {
        var citing = new TreeSet<String>();
        for (var source : sources()) {
            var relative = relative(source);
            if (MAY_CITE.contains(relative)) {
                continue;
            }
            var lines = read(source).lines().toList();
            for (var number = 0; number < lines.size(); number++) {
                if (RECORD.matcher(lines.get(number)).find()) {
                    citing.add(relative + ":" + (number + 1));
                }
            }
        }
        assertTrue(citing.isEmpty(), () -> citing.size() + " lines cite a record; say the rule and link the guide"
                + " (book/src/contributing/doc-comments.md). The first of them:\n  "
                + String.join("\n  ", citing.stream().limit(40).toList()));
    }

    @Test
    @DisplayName("links the guide at a page and a heading that exist")
    void linksLandOnTheGuide() {
        var listed = Book.chapters().stream().map(Book.Chapter::path).collect(Collectors.toSet());
        var malformed = new TreeSet<String>();
        var missingPage = new TreeSet<String>();
        var missingHeading = new TreeSet<String>();
        for (var source : javaSources()) {
            var relative = relative(source);
            var text = read(source);
            GuideLink.IN_TEXT.matcher(text).results().map(match -> match.group()).forEach(href -> {
                var parsed = GuideLink.parse(href);
                if (parsed.isEmpty()) {
                    malformed.add(relative + " -> " + href);
                    return;
                }
                var link = parsed.orElseThrow();
                if (link.page().isEmpty()) {
                    return;
                }
                var page = link.page().orElseThrow();
                if (!Book.exists(page) || !listed.contains(page)) {
                    missingPage.add(relative + " -> " + href);
                } else if (link.fragment().isPresent()
                        && !Book.anchors(page).contains(link.fragment().orElseThrow())) {
                    missingHeading.add(relative + " -> " + href);
                }
            });
        }
        assertAll(
                () -> assertEquals(Set.of(), malformed,
                        "guide links that name no page mdBook writes: use the .html the book builds"),
                () -> assertEquals(Set.of(), missingPage, "guide links to a chapter the summary does not list"),
                () -> assertEquals(Set.of(), missingHeading, "guide links to a heading the chapter does not have"));
    }

    @Test
    @DisplayName("does not name a working document under docs/, which its reader does not have")
    void namesNoWorkingDocument() {
        var naming = new TreeSet<String>();
        for (var source : javaSources()) {
            var lines = read(source).lines().toList();
            for (var number = 0; number < lines.size(); number++) {
                if (WORKING_DOCUMENT.matcher(lines.get(number)).find()) {
                    naming.add(relative(source) + ":" + (number + 1));
                }
            }
        }
        assertTrue(naming.isEmpty(), () -> naming.size() + " lines name a file under docs/; say the rule and link"
                + " the guide (book/src/contributing/doc-comments.md). The first of them:\n  "
                + String.join("\n  ", naming.stream().limit(40).toList()));
    }

    @Test
    @DisplayName("of a published package points at the guide")
    void everyPublishedPackageLinksTheGuide() {
        var unlinked = new TreeSet<String>();
        var checked = 0;
        for (var info : publishedPackageInfos()) {
            checked++;
            if (!read(info).contains(GuideLink.BASE)) {
                unlinked.add(relative(info));
            }
        }
        assertTrue(checked > 200, "found only " + checked + " published packages; is the walk wrong?");
        assertEquals(Set.of(), unlinked, "package-info.java files with no link to " + GuideLink.BASE);
    }

    /// Every Java file in every module's source sets, plus the Gradle scripts and
    /// the workflows: the files whose comments a reader of the repository meets.
    private static List<Path> sources() {
        var root = Repository.root();
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> !file.toString().contains("/build/") && !file.toString().contains("/.gradle/"))
                    .filter(file -> {
                        var relative = root.relativize(file).toString().replace('\\', '/');
                        return isJavaSource(relative) || isGradleScript(relative) || isWorkflow(relative);
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// The Java sources only, where a guide link is a doc comment's.
    private static List<Path> javaSources() {
        return sources().stream().filter(file -> file.toString().endsWith(".java")).toList();
    }

    /// `<module>/src/<set>/java/**.java`, in any source set of any module.
    private static boolean isJavaSource(String relative) {
        return relative.endsWith(".java") && relative.matches("[^/]+/src/[^/]+/java/.*");
    }

    private static boolean isGradleScript(String relative) {
        return relative.endsWith(".gradle") && (!relative.contains("/") || relative.matches("[^/]+/build\\.gradle")
                || relative.startsWith("build-logic/src/main/groovy/"));
    }

    private static boolean isWorkflow(String relative) {
        return relative.startsWith(".github/workflows/") && relative.endsWith(".yml");
    }

    /// The `package-info.java` of every package under `src/main/java` of a module
    /// whose build script applies the publishing conventions.
    private static List<Path> publishedPackageInfos() {
        var root = Repository.root();
        try (var modules = Files.list(root)) {
            return modules.filter(module -> Files.isRegularFile(module.resolve("build.gradle")))
                    .filter(module -> PUBLISHED.matcher(read(module.resolve("build.gradle"))).find())
                    .map(module -> module.resolve("src/main/java"))
                    .filter(Files::isDirectory)
                    .flatMap(SourceDocsTest::packageInfosUnder)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<Path> packageInfosUnder(Path sourceRoot) {
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            return files.filter(file -> file.getFileName().toString().equals("package-info.java")).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String relative(Path file) {
        return Repository.root().relativize(file).toString().replace('\\', '/');
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
