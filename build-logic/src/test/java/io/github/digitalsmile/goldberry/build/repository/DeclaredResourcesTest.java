package io.github.digitalsmile.goldberry.build.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every resource a module ships is declared to {@code native-image} by some
 * module's {@code reachability-metadata.json} (ADR-0160, ADR-0453).
 *
 * <p>The guard that would have caught two bugs at once. {@code :emoji} shipped
 * the OpenMoji face and declared nothing, so the showcase's native image died
 * on the first paragraph with an emoji in it; {@code :html} shipped two
 * stylesheets and declared nothing, and got away with it only because the
 * showcase traces a run that happens to open both of those screens. One is a
 * red job and the other is a trap, and they are the same mistake.
 *
 * <p>Repository-wide rather than per module, because per module is what was
 * already being done and what was forgotten twice: a new module, or a resource
 * that moves between modules, is covered by this the day it lands.
 *
 * <p>Read as text. No GraalVM runs here, and the property is an agreement
 * between files rather than anything only the image builder can answer.
 */
@DisplayName("declared resources")
class DeclaredResourcesTest {

    /** Where a module's declarations live, if it has any. */
    private static final String METADATA = "src/main/resources/META-INF/native-image";

    /**
     * A resource that travels in a jar but is never asked for by name, so no
     * image needs it registered.
     *
     * <p>{@code logback.xml} is the logging backend's own and is read by
     * Logback's configurator, which the example declares for itself; the
     * service files under {@code META-INF/services} are the module system's
     * and are handled by native-image without being registered.
     */
    private static final List<String> NOT_A_RESOURCE_TO_REGISTER =
            List.of("META-INF/services/", "META-INF/MANIFEST.MF");

    @Test
    @DisplayName("every shipped resource is declared by the module that ships it")
    void everyShippedResourceIsDeclaredByItsOwnModule() {
        var undeclared = new TreeSet<String>();
        var checked = 0;
        for (var module : modules()) {
            var globs = globsDeclaredBy(module);
            for (var resource : shippedBy(module)) {
                checked++;
                if (globs.stream().noneMatch(glob -> glob.matcher(resource).matches())) {
                    undeclared.add(module.getFileName() + ": " + resource);
                }
            }
        }
        assertFalse(checked == 0, "no shipped resources found at all, so this checked nothing");
        assertEquals(
                new TreeSet<String>(),
                undeclared,
                "these ship in a module's jar and are read from it, but that module declares none of them."
                        + " Another module declaring them does not count and is the trap this checks for:"
                        + " :html's stylesheets were covered only by the *traced* metadata :example ships,"
                        + " so they reached the showcase's image and would not reach anyone else's."
                        + " Add a glob under <module>/" + METADATA + "/. See ADR-0160 and ADR-0453.");
    }

    /**
     * Every file this module ships as a resource, as the slash-separated path a
     * glob is written against, excluding the metadata directory itself.
     */
    private static List<String> shippedBy(Path module) {
        var resources = module.resolve("src/main/resources");
        if (!Files.isDirectory(resources)) {
            return List.of();
        }
        var found = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(resources)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                var path = resources.relativize(file).toString().replace('\\', '/');
                if (path.startsWith("META-INF/native-image/")) {
                    return;
                }
                if (NOT_A_RESOURCE_TO_REGISTER.stream().anyMatch(path::startsWith)) {
                    return;
                }
                found.add(path);
            });
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + resources, e);
        }
        return found;
    }

    /** Every glob this module declares, as a regex over a resource path. */
    private static List<Pattern> globsDeclaredBy(Path module) {
        var directory = module.resolve(METADATA);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        var globs = new ArrayList<Pattern>();
        try (Stream<Path> files = Files.walk(directory)) {
            for (var file : files.filter(f -> f.getFileName().toString().endsWith(".json"))
                    .toList()) {
                globs.addAll(globsIn(read(file)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + directory, e);
        }
        return globs;
    }

    /**
     * The {@code "glob": "..."} values of one metadata file.
     *
     * <p>Read with a regex rather than a JSON parser: build-logic has no JSON
     * dependency, these files are written by hand in one shape, and a glob that
     * this cannot see is a glob that fails the test rather than one that
     * silently passes it.
     */
    private static List<Pattern> globsIn(String json) {
        var globs = new ArrayList<Pattern>();
        var matcher = GLOB.matcher(json);
        while (matcher.find()) {
            globs.add(asRegex(matcher.group(1)));
        }
        return globs;
    }

    private static final Pattern GLOB = Pattern.compile("\"glob\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * A native-image glob as a regex: {@code **} crosses directory boundaries,
     * {@code *} does not, and everything else is literal.
     */
    private static Pattern asRegex(String glob) {
        var regex = new StringBuilder();
        // Literal runs are quoted whole. Quoting character by character is what
        // the first attempt did, and \\Q x \\E around every letter matches
        // nothing recognisable -- it reported every resource in the repository
        // as undeclared, including the ones that plainly were.
        var literal = new StringBuilder();
        for (var i = 0; i < glob.length(); i++) {
            var c = glob.charAt(i);
            if (c != '*') {
                literal.append(c);
                continue;
            }
            if (!literal.isEmpty()) {
                regex.append(Pattern.quote(literal.toString()));
                literal.setLength(0);
            }
            if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                regex.append(".*");
                i++;
            } else {
                regex.append("[^/]*");
            }
        }
        if (!literal.isEmpty()) {
            regex.append(Pattern.quote(literal.toString()));
        }
        return Pattern.compile(regex.toString());
    }

    /** The Gradle modules of this repository: every directory with a build file. */
    private static List<Path> modules() {
        var root = Repository.root();
        try (Stream<Path> entries = Files.list(root)) {
            return entries.filter(Files::isDirectory)
                    .filter(directory -> Files.isRegularFile(directory.resolve("build.gradle")))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + root, e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }
}
