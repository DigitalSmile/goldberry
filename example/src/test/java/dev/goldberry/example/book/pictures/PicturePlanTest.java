package dev.goldberry.example.book.pictures;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the picture plan")
class PicturePlanTest {

    private static final List<String> CHAPTER = """
            # Buttons

            Prose with `button` in code marks, which is not a heading.

            ## `button`

            One sentence saying what it is.

            ```kdl
            row {
              button "Save"
            }
            ```

            ```java
            new Button("Save");
            ```

            ## `badge`

            ```kdl,ignore
            badge bind="app.count"
            ```

            ### `chip`

            Only prose, and then the next heading.

            ## Something else

            ```kdl
            text "a sample under a heading that is not a widget"
            ```

            ## `knob`

            ```java
            new Knob();
            ```
            """.lines().toList();

    @Test
    @DisplayName("pictures a heading whose first sample is a kdl document")
    void picturesAKdlSample() {
        var entries = PicturePlan.read("components/buttons.md", CHAPTER);
        var button =
                assertInstanceOf(PicturePlan.Pictured.class, entries.getFirst()).picture();
        assertAll(
                () -> assertEquals("button", button.name()),
                () -> assertEquals("components/buttons.md", button.chapter()),
                () -> assertEquals(9, button.line()),
                () -> assertEquals("row {\n  button \"Save\"\n}\n", button.markup()));
    }

    @Test
    @DisplayName(
            "skips a kdl,ignore sample, a heading with no sample and a sample in another language, each with its reason")
    void skipsWithReasons() {
        var entries = PicturePlan.read("components/buttons.md", CHAPTER);
        var skipped = entries.stream()
                .filter(PicturePlan.Skipped.class::isInstance)
                .map(PicturePlan.Skipped.class::cast)
                .toList();
        assertAll(
                () -> assertEquals(
                        List.of("badge", "chip", "knob"),
                        skipped.stream().map(PicturePlan.Skipped::name).toList()),
                () -> assertTrue(skipped.get(0).reason().contains("kdl,ignore")),
                () -> assertTrue(skipped.get(1).reason().contains("no fenced sample")),
                () -> assertTrue(skipped.get(2).reason().contains("java")));
    }

    @Test
    @DisplayName("reads only headings that are a name in code marks")
    void onlyWidgetHeadings() {
        var names = PicturePlan.read("components/buttons.md", CHAPTER).stream()
                .map(PicturePlan.Entry::name)
                .toList();
        assertEquals(List.of("button", "badge", "chip", "knob"), names);
    }

    @Test
    @DisplayName("names a file per shade")
    void filesPerShade() {
        var picture = new WidgetPicture("button", "components/buttons.md", 9, "button \"Save\"\n");
        assertAll(
                () -> assertEquals("button-light.webp", picture.file(Shade.LIGHT)),
                () -> assertEquals("button-dark.webp", picture.file(Shade.DARK)));
    }
}
