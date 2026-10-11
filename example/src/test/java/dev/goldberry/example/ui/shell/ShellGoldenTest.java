package dev.goldberry.example.ui.shell;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.example.ui.home.HomeScreen;
import dev.goldberry.example.ui.search.SearchCatalog;
import dev.goldberry.example.ui.search.SearchIndex;
import dev.goldberry.example.ui.search.SearchPalette;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.input.key.Key;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.text.font.Fonts;

/// The window's own parts, one picture each: Home, the sidebar as a rail, and
/// the search palette with a query in it.
///
/// Taken like the gallery's: the one-font renderer, at the gallery's size, so
/// a change to the shell shows here before it shows on thirty screens.
/// `./gradlew :example:test -Dgoldberry.golden.update=true` retakes them.
///
/// Read more: [Goldens](https://goldberry.dev/docs/contributing/testing.html#goldens).
@DisplayName("the shell")
class ShellGoldenTest {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 900;

    private ShowcaseScene scene;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        scene = new ShowcaseScene();
    }

    @AfterEach
    void tearDown() {
        if (scene != null) {
            scene.close();
        }
    }

    @Test
    @DisplayName("Home, with a tile per section")
    void home() {
        var root = scene.root(HomeScreen.NAME);
        var sheets = scene.stylesheets(Theme.NORD_DARK);
        GoldenImage.assertMatches(
                "shell-home",
                WIDTH,
                HEIGHT,
                1.0f,
                (size, scale) -> Offscreen.of(size)
                        .scale(scale)
                        .stylesheets(sheets)
                        .font(scene.font())
                        .render(root));
    }

    @Test
    @DisplayName("the sidebar folded to a rail of icons")
    void rail() {
        scene.actions().toggleSidebar();
        var root = scene.root("buttons");
        var sheets = scene.stylesheets(Theme.NORD_LIGHT);
        GoldenImage.assertMatches(
                "shell-rail",
                WIDTH,
                HEIGHT,
                1.0f,
                (size, scale) -> Offscreen.of(size)
                        .scale(scale)
                        .stylesheets(sheets)
                        .font(scene.font())
                        .render(root));
    }

    /// At one scale, and through a **font book**: a session shapes against a
    /// book, so this is the one picture here whose text is set at the sizes the
    /// cascade picks. What is pinned is the list a query gives rather than how
    /// the dialog's fade samples at four densities.
    @Test
    @DisplayName("the search palette, with a query typed and the second result marked")
    void palette() {
        var root = scene.root("forms");
        var sheets = scene.stylesheets(Theme.NORD_DARK);
        var entries = SearchCatalog.entries(SearchIndex.shipped(), List.of());
        try (var fonts = Fonts.bundled()) {
            GoldenImage.assertMatchesAtOneScale("shell-search", WIDTH, HEIGHT, 1.0f, (size, scale) -> {
                try (var session = Offscreen.of(size)
                        .scale(scale)
                        .stylesheets(sheets)
                        .fonts(fonts)
                        .session(root)) {
                    new SearchPalette().open(session.host(), entries, entry -> {});
                    session.advance(Duration.ofMillis(400));
                    session.type("slider");
                    session.key(Key.DOWN);
                    session.advance(Duration.ofMillis(400));
                    return session.frame();
                }
            });
        }
    }
}
