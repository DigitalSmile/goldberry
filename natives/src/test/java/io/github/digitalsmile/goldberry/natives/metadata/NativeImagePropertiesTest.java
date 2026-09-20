package io.github.digitalsmile.goldberry.natives.metadata;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.glib.GlibLibrary;
import io.github.digitalsmile.goldberry.natives.webview.WebviewLibrary;

/// The image arguments this module ships, held to the module itself.
///
/// `native-image.properties` is the one file in `:natives` that nothing compiles,
/// nothing runs and nothing else reads: it is a list of package names typed out
/// by hand, consumed only by a tool that is not part of this build. So it drifted.
/// `md4c.calls` and `webp.calls` were added to the module and never added here,
/// and there was no way to notice — an image builds, links and runs perfectly
/// well with a holder initialised at run time. It is simply a third of the speed
/// on every call through that holder, which is the 450x ADR-0161 measured and
/// `Downcalls` describes.
///
/// This is the test that notices. It reads the shipped resource rather than the
/// source file, so a build that fails to package it fails here too.
@DisplayName("the image arguments")
class NativeImagePropertiesTest {

    private static final String RESOURCE =
            "/META-INF/native-image/io.github.digitalsmile/goldberry-natives/native-image.properties";

    private static final String BUILD_TIME = "--initialize-at-build-time=";
    private static final String RUN_TIME = "--initialize-at-run-time=";

    /// The holder package that must **not** be initialised in the builder, and
    /// the only exception this file has.
    ///
    /// `desktop.calls` looks like every other holder package and is not one:
    /// `PortalSettings` binds a dozen libdbus functions in a **static**
    /// initialiser and keeps their addresses in a static field. Initialising that
    /// while the image is being built either fails the build outright — a
    /// `MemorySegment` reached from the image heap is not something GraalVM will
    /// write out — or, on a builder that does have libdbus, bakes the build
    /// machine's D-Bus into an image that will run somewhere else. Every other
    /// `…calls` package holds unbound handles and nothing else, which is exactly
    /// why the rule can be stated per package at all.
    private static final Set<String> NOT_IN_THE_BUILDER =
            Set.of("io.github.digitalsmile.goldberry.natives.desktop.calls");

    /// The `Args` line, split into arguments.
    ///
    /// Through [Properties], because the file's line continuations are Java
    /// property continuations and re-implementing them here is how a test comes
    /// to pass against a file the tool reads differently.
    private static Set<String> arguments() {
        try (var in = NativeImagePropertiesTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "this module ships " + RESOURCE + "; without it an image is built with no arguments");
            var properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            var args = properties.getProperty("Args");
            assertNotNull(args, "no Args line in " + RESOURCE);
            return new TreeSet<>(Arrays.asList(args.split("\\s+")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> valuesOf(String option) {
        return arguments().stream()
                .filter(argument -> argument.startsWith(option))
                .map(argument -> argument.substring(option.length()))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /// The packages that hold holders, found from the module rather than listed.
    private static Set<String> holderPackages() {
        var packages = new TreeSet<String>();
        for (var name : ForeignSurface.holderClassNames()) {
            packages.add(name.substring(0, name.lastIndexOf('.')));
        }
        return packages;
    }

    @Nested
    @DisplayName("--initialize-at-build-time")
    class BuildTime {

        @Test
        @DisplayName("names every package that holds a holder, and no other")
        void coversTheHolderPackages() {
            var expected = new TreeSet<>(holderPackages());
            expected.removeAll(NOT_IN_THE_BUILDER);
            expected.add(Downcalls.class.getName());

            assertEquals(
                    expected,
                    new TreeSet<>(valuesOf(BUILD_TIME)),
                    "a holder package missing from native-image.properties is not a broken image, it is a "
                            + "slow one: every handle in it links at run time and every call through it runs "
                            + "interpreted (ADR-0161, ADR-0173). An entry here that is not a holder package "
                            + "is initialisation the image does not need and may not survive.");
        }

        @Test
        @DisplayName("keeps the one package that binds in a class initialiser out of the builder")
        void leavesThePortalOut() {
            var listed = valuesOf(BUILD_TIME);

            assertAll(
                    () -> assertTrue(
                            holderPackages().containsAll(NOT_IN_THE_BUILDER),
                            () -> NOT_IN_THE_BUILDER + " is excluded from the image arguments and no longer exists; "
                                    + "the exclusion, and the comment explaining it, are describing nothing"),
                    () -> assertTrue(
                            NOT_IN_THE_BUILDER.stream().noneMatch(listed::contains),
                            () -> "PortalSettings binds libdbus in a static initialiser, so " + NOT_IN_THE_BUILDER
                                    + " cannot be initialised in the builder"));
        }

        @Test
        @DisplayName("names Downcalls, which every holder's initialiser calls")
        void namesDowncalls() {
            assertTrue(
                    valuesOf(BUILD_TIME).contains(Downcalls.class.getName()),
                    "a holder initialised at build time whose Linker is not is a holder initialised at run time");
        }
    }

    @Test
    @DisplayName("keeps every library loader out of the builder, because they dlopen")
    void nativeLibraryIsInitialisedAtRunTime() {
        // Two since ADR-0441, and the second one matters more than the first.
        // `NativeLibrary` at build time is a wrong *address* baked into the image,
        // which fails loudly. `WebviewLibrary` at build time is a wrong *answer*:
        // the image would carry whether the BUILD machine had WebKit, so one built
        // on a developer's desktop would claim Capability.WEB_VIEW on a server
        // with no WebKit at all, and one built in a bare CI container would deny
        // it for ever on machines that do. Both are silent.
        //
        // Three since ADR-0443, and `GlibLibrary` is the second kind again: it
        // answers whether this machine has a GLib, and a builder's answer baked
        // into the image is a log bridge that is silently never installed on
        // every desktop that would have wanted it.
        assertEquals(
                Set.of(NativeLibrary.class.getName(), WebviewLibrary.class.getName(), GlibLibrary.class.getName()),
                valuesOf(RUN_TIME),
                "a library has to be mapped by the process that runs, not by the one that builds");
    }
}
