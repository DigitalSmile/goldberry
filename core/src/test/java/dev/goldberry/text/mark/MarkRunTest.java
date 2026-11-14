package dev.goldberry.text.mark;

import static dev.goldberry.text.mark.MarkKind.Style.BOLD;
import static dev.goldberry.text.mark.MarkKind.Style.ITALIC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Cutting a line into runs of one unchanging set of kinds.
class MarkRunTest {

    @Test
    @DisplayName("runs cover every char, cut wherever a mark starts or ends")
    void cuts() {
        var runs = MarkRun.of(10, List.of(new Mark(2, 6, BOLD), new Mark(4, 8, ITALIC)));
        assertEquals(
                List.of(
                        new MarkRun(0, 2, Set.of()),
                        new MarkRun(2, 4, Set.of(BOLD)),
                        new MarkRun(4, 6, Set.of(BOLD, ITALIC)),
                        new MarkRun(6, 8, Set.of(ITALIC)),
                        new MarkRun(8, 10, Set.of())),
                runs);
    }

    @Test
    @DisplayName("no marks is one plain run, and no text is none")
    void edges() {
        assertEquals(List.of(new MarkRun(0, 3, Set.of())), MarkRun.of(3, List.of()));
        assertEquals(List.of(), MarkRun.of(0, List.of()));
    }
}
