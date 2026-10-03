package dev.goldberry.example.ui.styling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.lint.Finding;
import dev.goldberry.css.lint.StyleLint;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.example.ShowcaseStyles;
import dev.goldberry.widgets.Controls;

/// `chapter-styling.css` is the demonstration on two screens, so a rule in it that
/// did nothing would be a card showing nothing. It is held to the strict parse
/// the toolkit's own sheets are, and the lint finds no declaration that does
/// nothing.
class StylingSheetTest {

    private static final String SHEET = "chapter-styling.css";

    @Test
    @DisplayName("parses strictly: every selector and at-rule is in the subset")
    void strict() {
        var sheet = Stylesheet.resource(CascadeLayer.APPLICATION, ShowcaseStyles.class, SHEET, ParseMode.STRICT);
        assertFalse(sheet.rules().isEmpty());
        assertEquals(List.of(), sheet.dropped());
    }

    @Test
    @DisplayName("has no declaration the engine applies nothing from")
    void noDeadDeclarations() {
        var inForce = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK));
        inForce.addAll(ShowcaseStyles.sheets());
        var mine = inForce.stream()
                .filter(sheet -> sheet.origin().endsWith(SHEET))
                .findFirst()
                .orElseThrow();
        var defects = new StyleLint(inForce)
                .check(mine).stream()
                        .filter(finding -> finding.kind().isDefect())
                        .map(Finding::toString)
                        .toList();
        assertEquals(List.of(), defects);
    }
}
