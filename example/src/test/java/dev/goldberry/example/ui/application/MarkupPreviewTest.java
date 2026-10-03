package dev.goldberry.example.ui.application;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.example.ui.gallery.Summaries;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;

/// What a typed document comes to: widgets resolved against the application's
/// registries, or the toolkit's own refusal.
@DisplayName("a markup preview")
class MarkupPreviewTest {

    private static ChapterFixture fixture;

    private static MarkupPreview preview;

    @BeforeAll
    static void open() {
        RendererRequirement.enforce();
        fixture = new ChapterFixture();
        preview = MarkupPreview.of(fixture.context());
    }

    @AfterAll
    static void close() {
        if (fixture != null) {
            fixture.close();
        }
    }

    private static String refusal(String source) {
        return assertInstanceOf(MarkupPreview.Refused.class, preview.inflate(source))
                .message();
    }

    @Test
    @DisplayName("inflates one node to that node's widget")
    void inflatesOneNode() {
        var outcome =
                assertInstanceOf(MarkupPreview.Inflated.class, preview.inflate("button press=\"app.click\" \"March\""));
        assertAll(() -> assertEquals(1, outcome.nodes()), () -> assertInstanceOf(Button.class, outcome.widget()));
    }

    @Test
    @DisplayName("puts several top-level nodes in a column")
    void inflatesSeveralNodes() {
        var outcome = assertInstanceOf(MarkupPreview.Inflated.class, preview.inflate("text \"a\"\ntext \"b\""));
        assertAll(() -> assertEquals(2, outcome.nodes()), () -> assertInstanceOf(Column.class, outcome.widget()));
    }

    @Test
    @DisplayName("resolves names against the application's model, actions and icons")
    void resolvesTheApplicationsNames() {
        assertInstanceOf(
                MarkupPreview.Inflated.class,
                preview.inflate(
                        "row {\n  button icon=\"plus\" press=\"app.click\" \"\"\n  badge bind=\"app.clicks\"\n}"));
    }

    @Test
    @DisplayName("refuses an action nothing publishes, naming it")
    void refusesATypo() {
        assertTrue(refusal("button press=\"app.clik\" \"March\"").contains("app.clik"));
    }

    @Test
    @DisplayName("refuses a binding that is an expression")
    void refusesAnExpression() {
        assertTrue(refusal("text bind=\"!app.light\"").contains("is not a binding path"));
    }

    @Test
    @DisplayName("refuses text that does not parse, with its position")
    void refusesASyntaxError() {
        assertTrue(refusal("button \"unclosed").contains("line 1"));
    }

    @Test
    @DisplayName("refuses a bare true, which is not a KDL 2 keyword")
    void refusesABareTrue() {
        refusal("checkbox checked=true \"x\"");
    }

    @Test
    @DisplayName("refuses an unknown node")
    void refusesAnUnknownNode() {
        assertTrue(refusal("buton \"x\"").contains("unknown node \"buton\""));
    }

    @Test
    @DisplayName("refuses a document with nothing in it")
    void refusesNothing() {
        refusal("// only a comment");
    }

    @Test
    @DisplayName("shortens a long refusal to a card's width")
    void shortensALongMessage() {
        var message = refusal("buton \"x\"");
        assertAll(
                () -> assertTrue(message.length() <= MarkupPreview.MESSAGE_LIMIT, message),
                () -> assertTrue(message.endsWith("…"), message),
                () -> assertTrue(!Summaries.citesARecord(message), message));
    }

    @Test
    @DisplayName("starts each card's editor where its card says")
    void startingDocuments() {
        assertAll(
                () -> assertInstanceOf(
                        Column.class,
                        assertInstanceOf(
                                        MarkupPreview.Inflated.class,
                                        preview.inflate(ApplicationChapter.INFLATE_SOURCE))
                                .widget()),
                () -> assertInstanceOf(
                        Row.class,
                        assertInstanceOf(
                                        MarkupPreview.Inflated.class, preview.inflate(ApplicationChapter.SYNTAX_SOURCE))
                                .widget()),
                () -> assertTrue(refusal(ApplicationChapter.STRICT_SOURCE).contains("app.clik")),
                () -> assertTrue(refusal(ApplicationChapter.PATH_SOURCE).contains("!app.light")));
    }
}
