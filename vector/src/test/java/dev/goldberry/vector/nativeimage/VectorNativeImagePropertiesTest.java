package dev.goldberry.vector.nativeimage;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.vector.thorvg.Downcalls;
import dev.goldberry.vector.thorvg.ThorvgEngine;
import dev.goldberry.vector.thorvg.VectorDescriptors;
import dev.goldberry.vector.thorvg.VectorNatives;

/// What `native-image.properties` tells an image about class initialisation:
/// the holders at build time, so a downcall is a direct call; whatever opens
/// the library or starts a thread at run time.
@DisplayName("goldberry-vector's native-image.properties")
class VectorNativeImagePropertiesTest {

    private static final String RESOURCE =
            "/META-INF/native-image/dev.goldberry/goldberry-vector/native-image.properties";

    @Test
    @DisplayName("initialises the holders' package and Downcalls at build time")
    void buildTime() throws IOException {
        var args = args();
        var holderPackage = VectorDescriptors.CALLS.getFirst().getPackageName();
        assertAll(
                () -> assertTrue(args.contains("--initialize-at-build-time=" + holderPackage), args::toString),
                () -> assertTrue(
                        args.contains("--initialize-at-build-time=" + Downcalls.class.getName()), args::toString),
                () -> VectorDescriptors.CALLS.forEach(calls -> assertEquals(holderPackage, calls.getPackageName())));
    }

    @Test
    @DisplayName("leaves the loader and the engine, with its Cleaner, to run time")
    void runTime() throws IOException, ClassNotFoundException {
        var args = args();
        var holder = Class.forName(VectorNatives.class.getName() + "$Holder");
        assertAll(
                () -> assertTrue(args.contains("--initialize-at-run-time=" + VectorNatives.class.getName())),
                () -> assertTrue(args.contains("--initialize-at-run-time=" + holder.getName())),
                () -> assertTrue(args.contains("--initialize-at-run-time=" + ThorvgEngine.class.getName())));
    }

    @Test
    @DisplayName("names nothing at run time inside the package it builds at build time")
    void noOverlap() throws IOException {
        var holderPackage = VectorDescriptors.CALLS.getFirst().getPackageName() + ".";
        args().stream()
                .filter(arg -> arg.startsWith("--initialize-at-run-time="))
                .forEach(arg -> assertTrue(!arg.substring(arg.indexOf('=') + 1).startsWith(holderPackage), arg));
    }

    private static List<String> args() throws IOException {
        try (var in = VectorNativeImagePropertiesTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, RESOURCE);
            var properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            var args = properties.getProperty("Args");
            assertNotNull(args, "no Args line in " + RESOURCE);
            return Arrays.stream(args.trim().split("\\s+")).toList();
        }
    }
}
