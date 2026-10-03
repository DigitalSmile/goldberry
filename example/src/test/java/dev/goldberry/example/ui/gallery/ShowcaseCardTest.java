package dev.goldberry.example.ui.gallery;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.docs.CardShape;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Link;
import dev.goldberry.widgets.text.Text;

@DisplayName("a showcase card")
class ShowcaseCardTest {

    private static final DocLink BUTTON = DocLink.to("components/buttons", "button");

    private static final ShowcaseCard CARD =
            new ShowcaseCard("button-card", "Buttons", "A button runs an action when pressed.", BUTTON);

    @Test
    @DisplayName("is its head, its summary, then what it shows")
    void hasTheShape() {
        var demo = new Button("Press", () -> {});
        var card = CARD.of(demo);

        var head = assertInstanceOf(Row.class, card.children().getFirst());
        var docs = assertInstanceOf(Link.class, head.children().getLast());
        var summary = assertInstanceOf(Text.class, card.children().get(1));
        assertAll(
                () -> assertEquals("button-card", card.attributes().id()),
                () -> assertEquals(Set.of("wall-card"), card.attributes().classes()),
                () -> assertEquals(BUTTON.url(), docs.href()),
                () -> assertEquals(
                        "Read about Buttons in the guide", docs.attributes().name()),
                () -> assertEquals("A button runs an action when pressed.", summary.content()),
                () -> assertEquals(demo, card.children().getLast()),
                () -> assertEquals(List.of(), CardShape.problems(card)),
                () -> assertEquals(Optional.of(BUTTON), CardShape.link(card)));
    }

    @Test
    @DisplayName("with nothing to click is a reference card")
    void referenceHasNoContent() {
        var card = CARD.reference();

        assertAll(
                () -> assertEquals(2, card.children().size()),
                () -> assertTrue(card.attributes().classes().containsAll(Set.of("wall-card", "reference-card"))),
                () -> assertEquals(List.of(), CardShape.problems(card)));
    }

    @Test
    @DisplayName("keeps the classes it is given beside its own")
    void classedAddsClasses() {
        var card = CARD.classed("wide").of();

        assertEquals(Set.of("wall-card", "wide"), card.attributes().classes());
    }

    @Test
    @DisplayName("refuses a summary that cites a decision record")
    void refusesACitation() {
        var refused = assertThrows(
                IllegalArgumentException.class,
                () -> new ShowcaseCard("x", "X", "Explained in " + "A" + "DR-0107.", BUTTON));
        assertTrue(refused.getMessage().contains("decision record"), refused.getMessage());
    }

    @Test
    @DisplayName("refuses a summary longer than a glance")
    void refusesALongSummary() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShowcaseCard("x", "X", "word ".repeat(Summaries.LIMIT), BUTTON));
    }

    @Test
    @DisplayName("refuses a card with no summary")
    void refusesAnEmptySummary() {
        assertThrows(IllegalArgumentException.class, () -> new ShowcaseCard("x", "X", " ", BUTTON));
    }
}
