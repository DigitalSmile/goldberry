package dev.goldberry.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.ListResourceBundle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the toolkit's labels")
class LabelsTest {

    @AfterEach
    void reset() {
        Labels.reset();
    }

    @Test
    @DisplayName("are English until told otherwise, and stay English where a translation has a gap")
    void translate() {
        assertEquals("Select all", Labels.text(ToolkitLabel.SELECT_ALL));

        Labels.use(new ListResourceBundle() {
            @Override
            protected Object[][] getContents() {
                return new Object[][] {{"goldberry.copy", "Копировать"}, {"goldberry.paste", " "}};
            }
        });
        assertEquals("Копировать", Labels.text(ToolkitLabel.COPY));
        assertEquals("Paste", Labels.text(ToolkitLabel.PASTE), "a blank translation is no translation");
        assertEquals("Cut", Labels.text(ToolkitLabel.CUT), "a missing one either");

        Labels.reset();
        assertEquals("Copy", Labels.text(ToolkitLabel.COPY));
    }

    @Test
    @DisplayName("each have a key of their own under goldberry., and some English")
    void keys() {
        var labels = ToolkitLabel.values();
        assertEquals(
                labels.length,
                Arrays.stream(labels).map(ToolkitLabel::key).distinct().count(),
                "no two labels share a key");
        for (var label : labels) {
            assertTrue(label.key().startsWith("goldberry."), label + ": " + label.key());
            assertFalse(label.english().isBlank(), label + " has English");
        }
    }

    @Test
    @DisplayName("come from a function too, which answers null for a word it has not got")
    void function() {
        Labels.use(label -> label == ToolkitLabel.CLOSE ? "Закрыть" : null);
        assertEquals("Закрыть", Labels.text(ToolkitLabel.CLOSE));
        assertEquals("Dismiss", Labels.text(ToolkitLabel.DISMISS));
    }
}
