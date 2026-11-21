package dev.goldberry.mic.view;

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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.mic.analysis.LevelTracker;
import dev.goldberry.mic.analysis.Levels;
import dev.goldberry.mic.view.draw.LevelBar;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.core.Column;

/// `mic.css`, and the descriptor that decides whether `:core` can read it: on
/// the class path every resource is readable, so only the compiled
/// `module-info` says whether an application on the module path can.
@DisplayName("the microphone stylesheet")
class MicStylesTest {

    @Test
    @DisplayName("parses into the layer an application can override")
    void parses() {
        var sheet = MicStyles.stylesheet();
        assertEquals(CascadeLayer.TOOLKIT_BASE, sheet.layer());
        assertFalse(sheet.rules().isEmpty());
    }

    @Test
    @DisplayName("draws a meter in the theme's colours, through the --gb-mic-* tokens")
    void tokensFollowTheTheme() {
        RendererRequirement.enforce();
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(MicStyles.stylesheet());
        var full = new LevelBar(new LevelTracker.Reading(new Levels(0.1f, 0.1f), Levels.FLOOR_DECIBELS));
        var image = Offscreen.of(120, 8).stylesheets(sheets).render(new Column(full));
        // Nord's aurora green, --gb-success: what --gb-mic-level points at.
        assertEquals(0xFFA3BE8C, image.argb(20, 4));

        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, "level-meter { --gb-mic-level: #ff0000; }"));
        var recoloured = Offscreen.of(120, 8).stylesheets(sheets).render(new Column(full));
        assertEquals(0xFFFF0000, recoloured.argb(20, 4), "an application's rule wins");
    }

    @Test
    @DisplayName("is readable from :core on the module path, and nothing else is open")
    void openToCore() {
        var opens = descriptor().opens();
        assertEquals(
                List.of("dev.goldberry.mic.view"),
                opens.stream().map(ModuleDescriptor.Opens::source).toList());
        assertTrue(opens.iterator().next().targets().contains("dev.goldberry.core"));
    }

    private static ModuleDescriptor descriptor() {
        var source = MicStylesTest.class.getProtectionDomain().getCodeSource();
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
