package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A native image carries only the resources it was told about, and the
/// showcase's are told about by hand, in `goldberry-example-manual`
/// (ADR-0160). The trace cannot do it: it records what a 120-frame run
/// touched, and the canvas screen's five sample images were touched by nobody
/// until a user clicked the tab in a Windows image and got
/// `canvas-sample.qoi is not on the classpath beside CanvasScreen`.
///
/// So the declaration is held to the disk. Every file under
/// `src/main/resources` outside `META-INF` has to match one of the module's
/// globs, and a resource added without one fails here rather than there.
@DisplayName("the showcase's declared resources")
class DeclaredResourcesTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    private static final Path MANUAL = RESOURCES.resolve(
            "META-INF/native-image/io.github.digitalsmile/goldberry-example-manual/reachability-metadata.json");

    private static final String MODULE = "io.github.digitalsmile.goldberry.example";

    /// One `resources` entry: an optional module, then a glob. The file is
    /// small and hand-written, so a regex over it is enough and keeps a JSON
    /// library out of the test classpath.
    private static final Pattern ENTRY =
            Pattern.compile("\\{\\s*(?:\"module\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*)?\"glob\"\\s*:\\s*\"([^\"]+)\"\\s*\\}");

    @Test
    @DisplayName("every resource on disk is covered by a glob registered against this module")
    void everyResourceIsDeclared() throws IOException {
        var globs = moduleGlobs();
        assertTrue(globs.size() >= 4, "expected the showcase's globs, found " + globs);

        var undeclared = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            for (var file : files.filter(Files::isRegularFile).toList()) {
                var name = slashed(RESOURCES.relativize(file));
                if (name.startsWith("META-INF/")) {
                    continue;
                }
                if (globs.stream().noneMatch(glob -> matches(glob, name))) {
                    undeclared.add(name);
                }
            }
        }
        assertEquals(List.of(), undeclared, "add a glob to " + MANUAL + " for each of these");
    }

    @Test
    @DisplayName("the canvas samples, all five formats, are among them")
    void canvasSamplesAreDeclared() throws IOException {
        var globs = moduleGlobs();
        var base = "io/github/digitalsmile/goldberry/example/ui/canvas-sample.";
        assertAll(Stream.of("png", "qoi", "webp", "gif", "jpg")
                .map(extension -> () -> assertTrue(
                        globs.stream().anyMatch(glob -> matches(glob, base + extension)), base + extension)));
    }

    @Test
    @DisplayName("a glob's star stops at a slash, and its dot is a dot")
    void globSemantics() {
        assertAll(
                () -> assertTrue(matches("a/*.kdl", "a/b.kdl")),
                () -> assertTrue(!matches("a/*.kdl", "a/b/c.kdl")),
                () -> assertTrue(!matches("a/*.kdl", "a/bxkdl")),
                () -> assertTrue(matches("a/**", "a/b/c.kdl")),
                () -> assertTrue(matches("a/canvas-sample.*", "a/canvas-sample.qoi")));
    }

    private static List<String> moduleGlobs() throws IOException {
        assertTrue(Files.isRegularFile(MANUAL), MANUAL.toString());
        var globs = new ArrayList<String>();
        Matcher matcher = ENTRY.matcher(Files.readString(MANUAL));
        while (matcher.find()) {
            if (MODULE.equals(matcher.group(1))) {
                globs.add(matcher.group(2));
            }
        }
        return globs;
    }

    /// The glob's `*` is anything but a separator and `**` is anything, which
    /// is what native-image reads them as. Done by hand rather than through a
    /// `PathMatcher`, because the names here are joined with `/` on every
    /// platform and a platform matcher would want its own separator.
    static boolean matches(String glob, String name) {
        var regex = new StringBuilder();
        for (var i = 0; i < glob.length(); i++) {
            var c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^/]*");
                }
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return name.matches(regex.toString());
    }

    private static String slashed(Path relative) {
        var joined = new StringBuilder();
        for (var element : relative) {
            if (!joined.isEmpty()) {
                joined.append('/');
            }
            joined.append(element);
        }
        return joined.toString();
    }
}
