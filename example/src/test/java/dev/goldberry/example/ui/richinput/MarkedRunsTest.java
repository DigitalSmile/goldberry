package dev.goldberry.example.ui.richinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkKind.Style;
import dev.goldberry.widgets.text.Run;

/// Marked text drawn read-only: a run per stretch, classed by its kinds.
class MarkedRunsTest {

    @Test
    @DisplayName("a run per stretch of one set of marks, its classes the kinds' classes")
    void classes() {
        var runs = MarkedRuns.of(MarkedWriter.start()
                .plain("a ")
                .with("b", Style.BOLD, Style.ITALIC)
                .plain(" c")
                .text());
        assertEquals(List.of("a ", "b", " c"), runs.stream().map(Run::text).toList());
        assertEquals(Set.of("bold", "italic"), runs.get(1).attributes().classes());
    }

    @Test
    @DisplayName("a spoiler is covered, and styles alone are not pressable")
    void spoiler() {
        var runs = MarkedRuns.of(
                MarkedWriter.start()
                        .with("hidden", Style.SPOILER)
                        .plain(" ")
                        .with("bold", Style.BOLD)
                        .text(),
                _ -> {});
        assertTrue(runs.getFirst().spoiler());
        assertNull(runs.getLast().onPress(), "nothing to open in a bold word");
    }

    @Test
    @DisplayName("a press on a link, a mention or a custom kind reports the kind")
    void presses() {
        var pressed = new ArrayList<MarkKind>();
        var mention = new MarkKind.Mention("user:ann");
        var runs = MarkedRuns.of(
                MarkedWriter.start()
                        .with("site", Style.BOLD, new MarkKind.Link(Samples.SITE))
                        .plain(" ")
                        .with("@Ann", mention)
                        .plain(" ")
                        .with("#x", Samples.HIGHLIGHT)
                        .text(),
                pressed::add);
        for (var run : runs) {
            if (run.onPress() != null) {
                run.onPress().run();
            }
        }
        assertEquals(List.of(new MarkKind.Link(Samples.SITE), mention, Samples.HIGHLIGHT), pressed);
        assertNotNull(runs.getFirst().onPress());
    }
}
