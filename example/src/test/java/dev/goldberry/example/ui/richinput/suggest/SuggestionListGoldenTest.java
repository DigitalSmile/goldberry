package dev.goldberry.example.ui.richinput.suggest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.offscreen.Offscreen;

/// The composer's suggestions for `@a`, the second row marked: what the panel
/// over the query shows. Drawn on its own, because a headless session opens no
/// popup windows.
///
/// `./gradlew :example:test -Dgoldberry.golden.update=true` rewrites them.
class SuggestionListGoldenTest {

    private static final int WIDTH = 240;
    private static final int HEIGHT = 96;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static void paint(String name, Theme theme) {
        var list = new SuggestionList(Suggester.of(Directories.PEOPLE).suggest("a"), 1, _ -> {});
        try (var scene = new ShowcaseScene()) {
            var sheets = scene.stylesheets(theme);
            GoldenImage.assertMatches(
                    name,
                    WIDTH,
                    HEIGHT,
                    1.0f,
                    (size, scale) -> Offscreen.of(size)
                            .scale(scale)
                            .stylesheets(sheets)
                            .font(scene.font())
                            .render(list));
        }
    }

    @Test
    @DisplayName("on dark")
    void dark() {
        paint("mention-list-dark", Theme.NORD_DARK);
    }

    @Test
    @DisplayName("on light")
    void light() {
        paint("mention-list-light", Theme.NORD_LIGHT);
    }
}
