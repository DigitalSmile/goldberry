package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.parse.TokenType;

/// `design-system.md` §1.7's rule 4, "nothing loops except explicit continuous
/// indicators", held against the toolkit's own stylesheets now that a stylesheet
/// can write a loop ([ADR-0353]).
///
/// The continuous indicators (`progress`, `spinner`, `skeleton`) are clock
/// functions in Java and write no keyframes (ADR-0081), so the rule as a check is
/// simply that no toolkit rule declares an infinite animation. An application's
/// sheets are the application's.
class ToolkitLoopsTest {

    @ParameterizedTest
    @EnumSource(Theme.class)
    @DisplayName("no toolkit stylesheet declares an animation that never ends")
    void nothingLoops(Theme theme) {
        var loops = new ArrayList<String>();
        for (var sheet : Controls.stylesheets(theme)) {
            for (var rule : sheet.rules()) {
                for (var declaration : rule.declarations()) {
                    if (!declaration.property().startsWith("animation")) {
                        continue;
                    }
                    var infinite = declaration.value().stream()
                            .anyMatch(token ->
                                    token.is(TokenType.IDENT) && token.text().equalsIgnoreCase("infinite"));
                    if (infinite) {
                        loops.add(rule.selectors() + " { " + declaration + " }");
                    }
                }
            }
        }
        assertEquals(List.of(), loops);
    }
}
