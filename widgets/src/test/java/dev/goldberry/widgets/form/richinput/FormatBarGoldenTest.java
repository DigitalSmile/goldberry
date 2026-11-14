package dev.goldberry.widgets.form.richinput;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.icon.Icon;
import dev.goldberry.paint.BoxPainter;
import dev.goldberry.text.edit.keys.FormatCommand;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.TestFont;

/// The stock formatting bar with bold on: eight icon buttons on the popover's
/// surface, the lit one on the selection's wash.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class FormatBarGoldenTest {

    private final HashMap<String, Icon> icons = new HashMap<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        for (var button : FormatButton.defaults()) {
            icons.put(button.icon(), Icon.bundled(button.icon(), FormatPopups.ICON_SIZE));
        }
    }

    @AfterEach
    void tearDown() {
        icons.values().forEach(Icon::close);
    }

    private void paint(String name, Theme theme) {
        var bar = new FormatBar(
                FormatButton.defaults(), Set.of(new FormatCommand.Toggle(MarkKind.Style.BOLD)), icons, command -> {});
        var renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), theme.load()), TestFont.get());
        GoldenImage.assertMatches(
                name, 300, 44, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(bar))));
    }

    @Test
    @DisplayName("on dark")
    void dark() {
        paint("format-bar-dark", Theme.NORD_DARK);
    }

    @Test
    @DisplayName("on light")
    void light() {
        paint("format-bar-light", Theme.NORD_LIGHT);
    }
}
