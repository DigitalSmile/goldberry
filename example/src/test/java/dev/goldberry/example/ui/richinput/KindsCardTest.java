package dev.goldberry.example.ui.richinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.widgets.form.richinput.RichInput;

/// The kinds card: one read-only field holding every kind.
class KindsCardTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("the sample holds every style, a link, a mention and a custom kind")
    void everyKind() {
        var kinds = new HashSet<>(KindsCard.kinds());
        assertTrue(kinds.containsAll(Arrays.asList(MarkKind.Style.values())), "every style: " + kinds);
        assertTrue(kinds.stream().anyMatch(MarkKind.Link.class::isInstance), "a link");
        assertTrue(kinds.stream().anyMatch(MarkKind.Mention.class::isInstance), "a mention");
        assertTrue(kinds.stream().anyMatch(MarkKind.Custom.class::isInstance), "a kind of the showcase's own");
        assertTrue(
                Samples.EVERY_KIND.marks().stream()
                        .anyMatch(one -> Samples.EVERY_KIND.marks().stream()
                                .anyMatch(other ->
                                        other != one && other.start() == one.start() && other.end() == one.end())),
                "two kinds over the same words");
    }

    @Test
    @DisplayName("the field is read-only, and the caption names each class once")
    void readOnly() {
        try (var card = new CardSession(new KindsCard())) {
            var field = card.widget(KindsCard.FIELD, RichInput.class);
            assertTrue(field.readOnly());
            assertEquals(Samples.EVERY_KIND, field.value());
            assertEquals(
                    "Classes: bold, italic, underline, strike, code, spoiler, link, mention, topic",
                    card.text("rich-kinds-classes"));
        }
    }
}
