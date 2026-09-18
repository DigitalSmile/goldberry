package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The showcase's `opens` are held to what actually reads its resources —
/// [ADR-0395].
///
/// ## Why this cannot be an ordinary test
///
/// JPMS encapsulates resources, and **only on the module path**. This suite runs
/// on the class path, where every resource is visible to everything, so a screen
/// whose picture nothing can read renders perfectly here and is blank in the
/// application. That is exactly what happened: `canvas-sample.jpg` sat beside
/// `CanvasScreen` with a package opened to `:core`, the `image` widget that reads
/// it is in `:widgets`, and four views logged *"no image resource"* about a file
/// that was right there.
///
/// `DeclaredResourcesTest` is the same shape of test for the same reason — a
/// failure a run of the suite cannot see — and this is its sibling. Both read the
/// repository rather than the running JVM.
///
/// ## The rule
///
/// A package under `src/main/resources` is opened to the module that **reads**
/// what is in it: `:core` parses stylesheets and markup, `:widgets` loads images.
/// A package holding both is opened to both.
@DisplayName("the showcase's open resource packages")
class OpenResourcePackagesTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    private static final Path MODULE_INFO = Path.of("src/main/java/module-info.java");

    private static final String CORE = "io.github.digitalsmile.goldberry.core";

    private static final String WIDGETS = "io.github.digitalsmile.goldberry.widgets";

    /// What an `image` widget reads. `qoi` is in the list because the Canvas
    /// screen ships one, even though only the `jpg` goes through `ImageSource`.
    private static final Set<String> IMAGES = Set.of("png", "jpg", "jpeg", "webp", "gif", "qoi");

    /// What `:core` reads — a stylesheet and a markup file.
    private static final Set<String> DOCUMENTS = Set.of("css", "kdl");

    /// `opens a.b.c to x, y;` across however many lines it is written on.
    private static final Pattern OPENS = Pattern.compile("opens\\s+([\\w.]+)\\s+to\\s+([^;]+);", Pattern.DOTALL);

    @Test
    @DisplayName("every package holding an image opens to the module that loads one")
    void imagePackagesOpenToWidgets() throws IOException {
        var opens = declaredOpens();
        var missing = new ArrayList<String>();

        for (var entry : resourcePackages().entrySet()) {
            var pkg = entry.getKey();
            if (entry.getValue().stream().noneMatch(IMAGES::contains)) {
                continue;
            }
            if (!opens.getOrDefault(pkg, Set.of()).contains(WIDGETS)) {
                missing.add(pkg);
            }
        }
        assertEquals(
                List.of(),
                missing,
                "these packages hold an image and are not open to " + WIDGETS + ", so `ImageSource.resource`"
                        + " reads nothing from them on the module path. Add it to their `opens` in "
                        + MODULE_INFO);
    }

    @Test
    @DisplayName("and every package holding a stylesheet or markup opens to the module that parses one")
    void documentPackagesOpenToCore() throws IOException {
        var opens = declaredOpens();
        var missing = new ArrayList<String>();

        for (var entry : resourcePackages().entrySet()) {
            var pkg = entry.getKey();
            if (entry.getValue().stream().noneMatch(DOCUMENTS::contains)) {
                continue;
            }
            if (!opens.getOrDefault(pkg, Set.of()).contains(CORE)) {
                missing.add(pkg);
            }
        }
        assertEquals(List.of(), missing, "these packages hold a stylesheet or markup and are not open to " + CORE);
    }

    @Test
    @DisplayName("the sample image's own package is open to both, which is the case that found this")
    void theCanvasSampleIsReachable() throws IOException {
        var opens = declaredOpens().getOrDefault("io.github.digitalsmile.goldberry.example.ui", Set.of());

        assertTrue(opens.contains(CORE), "the panes' documents are parsed by :core");
        assertTrue(opens.contains(WIDGETS), "canvas-sample.jpg is read by an `image`, which is :widgets");
    }

    /// Every package under `src/main/resources` that holds a file, with the
    /// extensions found in it.
    ///
    /// `META-INF` is skipped: it is not a legal package name, so JPMS never
    /// encapsulates it and no `opens` could name it.
    private static Map<String, Set<String>> resourcePackages() throws IOException {
        var found = new LinkedHashMap<String, Set<String>>();
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            for (var file : files.filter(Files::isRegularFile).toList()) {
                var relative = RESOURCES.relativize(file);
                if (relative.startsWith("META-INF")) {
                    continue;
                }
                var directory = relative.getParent();
                if (directory == null) {
                    continue;
                }
                var name = file.getFileName().toString();
                var dot = name.lastIndexOf('.');
                if (dot < 0) {
                    continue;
                }
                found.computeIfAbsent(directory.toString().replace('/', '.').replace('\\', '.'), _ -> new TreeSet<>())
                        .add(name.substring(dot + 1).toLowerCase(Locale.ROOT));
            }
        }
        assertTrue(found.size() >= 2, "expected the showcase's resource packages, found " + found.keySet());
        return found;
    }

    /// The `opens` in `module-info.java`, read as text.
    ///
    /// The source rather than the runtime descriptor, because this test runs on
    /// the class path and there is no module here to ask.
    private static Map<String, Set<String>> declaredOpens() throws IOException {
        assertTrue(Files.isRegularFile(MODULE_INFO), MODULE_INFO.toString());
        var declared = new LinkedHashMap<String, Set<String>>();
        var matcher = OPENS.matcher(Files.readString(MODULE_INFO));
        while (matcher.find()) {
            var targets = new TreeSet<String>();
            for (var target : matcher.group(2).split(",")) {
                var trimmed = target.strip();
                if (!trimmed.isEmpty()) {
                    targets.add(trimmed);
                }
            }
            declared.put(matcher.group(1), targets);
        }
        assertTrue(!declared.isEmpty(), "no `opens` found in " + MODULE_INFO);
        return declared;
    }
}
