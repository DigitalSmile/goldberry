package dev.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.goldberry.css.Theme;
import dev.goldberry.css.parse.TokenType;

/// Nothing loops except an explicit continuous indicator. That is the design
/// system's motion rule, held against the toolkit's own stylesheets now that a
/// stylesheet can name keyframes and so can write a loop.
///
/// The continuous indicators (`progress`, `spinner`, `skeleton`) are clock
/// functions in Java and write no keyframes, so the rule as a check is simply
/// that no toolkit rule declares an infinite animation. An application's sheets
/// are the application's.
///
/// Read more:
/// [The design system: motion](https://goldberry.dev/docs/guide/design-system.html#motion).
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
