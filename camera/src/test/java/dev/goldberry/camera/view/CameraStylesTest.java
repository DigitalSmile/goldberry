package dev.goldberry.camera.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.cascade.CascadeLayer;

/// `camera.css`, and the descriptor that decides whether `:core` can read it
/// on the module path, which no class-path test can show.
@DisplayName("the camera stylesheet")
class CameraStylesTest {

    @Test
    @DisplayName("parses into the layer an application can override")
    void parses() {
        var sheet = CameraStyles.stylesheet();
        assertEquals(CascadeLayer.TOOLKIT_BASE, sheet.layer());
        assertFalse(sheet.rules().isEmpty());
    }

    @Test
    @DisplayName("is readable from :core on the module path, and nothing else is open")
    void openToCore() {
        var opens = descriptor().opens();
        assertEquals(
                List.of("dev.goldberry.camera.view"),
                opens.stream().map(ModuleDescriptor.Opens::source).toList());
        assertTrue(opens.iterator().next().targets().contains("dev.goldberry.core"));
    }

    private static ModuleDescriptor descriptor() {
        var source = CameraStylesTest.class.getProtectionDomain().getCodeSource();
        if (source == null) {
            fail("no code source for the test classes");
        }
        Path main;
        try {
            main = Path.of(source.getLocation().toURI()).resolveSibling("main");
        } catch (URISyntaxException e) {
            throw new AssertionError(source.getLocation().toString(), e);
        }
        var moduleInfo = main.resolve("module-info.class");
        assertTrue(Files.isRegularFile(moduleInfo), "expected the compiled descriptor at " + moduleInfo);
        try (var in = Files.newInputStream(moduleInfo)) {
            return ModuleDescriptor.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
