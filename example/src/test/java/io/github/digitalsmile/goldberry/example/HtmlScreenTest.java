package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.bind.runtime.Models;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.example.ui.HtmlScreen;
import io.github.digitalsmile.goldberry.example.ui.Panes;
import io.github.digitalsmile.goldberry.html.view.HtmlStyles;
import io.github.digitalsmile.goldberry.html.view.HtmlView;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.markdown.view.MarkdownStyles;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;

/// That the HTML screen is **live**, and that its links reach the application.
///
/// [MarkdownScreenTest]'s twin, and it exists for the same reason: a golden of a
/// screen nobody has typed into looks the same whether the binding works or was
/// quietly dropped. The second half is what this screen has and the other does not —
/// an anchor that a reader can press, whose `href` arrives at a valued action the
/// document named and no Java in this application wired (ADR-0296, ADR-0298).
class HtmlScreenTest {

    private final Showcase showcase = new Showcase();

    private ShowcaseModel model;
    private Fonts fonts;
    private ElementTree tree;
    private WidgetRenderer renderer;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        model = showcase.models().stream()
                .filter(ShowcaseModel.class::isInstance)
                .map(ShowcaseModel.class::cast)
                .findFirst()
                .orElseThrow();
        fonts = Fonts.bundled();
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(MarkdownStyles.stylesheet());
        sheets.add(HtmlStyles.stylesheet());
        sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));
        renderer = new WidgetRenderer(sheets, fonts);
        var inflater = Widgets.inflater(
                model.named(), Icons.lenient(), showcase.models().toArray());
        tree = new ElementTree(new HtmlScreen(Panes.html(inflater)));
        frame();
    }

    @AfterEach
    void tearDown() {
        if (fonts != null) {
            fonts.close();
        }
    }

    /// One frame: rebuild whatever is dirty, then render — which is what a window does
    /// and what makes the *next* assertion about the screen rather than about the
    /// widget tree.
    private void frame() {
        tree.flush();
        renderer.render(tree);
    }

    private <T> T first(Class<T> type) {
        var found = new ArrayList<T>();
        collect(tree.root(), type, found);
        assertFalse(found.isEmpty(), "no " + type.getSimpleName() + " on the HTML screen");
        return found.getFirst();
    }

    private static <T> void collect(Element element, Class<T> type, List<T> found) {
        if (type.isInstance(element.widget())) {
            found.add(type.cast(element.widget()));
        }
        element.children().forEach(child -> collect(child, type, found));
    }

    /// The editor's box, which is the node that hears the keyboard.
    private Handles editor() {
        var found = new ArrayList<Handles>();
        collect(tree.root(), Handles.class, found);
        return found.stream()
                .filter(handles -> handles.getClass().getSimpleName().equals("TextAreaBox"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the HTML screen has no editor"));
    }

    /// Every link in the rendered page — a `button.link`, which is what an anchor
    /// becomes.
    private List<Button> links() {
        var buttons = new ArrayList<Button>();
        collect(tree.root(), Button.class, buttons);
        return buttons.stream()
                .filter(button -> button.attributes().classes().contains("html-a"))
                .toList();
    }

    @Test
    @DisplayName("types on the left and the page on the right is the next frame")
    void typingChangesThePreview() {
        var before = first(HtmlView.class).resolved();

        editor().onText(new TextEvent("\n<h2>Typed just now</h2>\n", null));
        frame();

        var after = first(HtmlView.class).resolved();
        assertNotEquals(before, after, "the preview should not be showing the page it was showing");

        // The editor wrote the model -- through `change=`, which is an action --
        var source = String.valueOf(Models.observable(model, "html.source").get());
        assertTrue(source.endsWith("<h2>Typed just now</h2>\n"), "the model holds what was typed, at the caret");
        // -- and the preview parsed it back, through `bind=`. Those two are the whole
        // mechanism; there is no third thing in between.
        assertEquals(
                List.of("Typed just now"),
                after.find("h2").stream()
                        .map(io.github.digitalsmile.goldberry.html.model.Element::text)
                        .filter(text -> text.equals("Typed just now"))
                        .toList(),
                "the preview parses what was typed");
    }

    @Test
    @DisplayName("reads and writes one property, which is what makes it live")
    void oneProperty() {
        var preview = first(HtmlView.class);

        assertNotNull(preview.binding(), "a preview that follows nothing is a screenshot");
        assertSame(Models.observable(model, "html.source"), preview.binding());
    }

    @Test
    @DisplayName("an anchor in the page is a button a reader can press")
    void anchorsAreButtons() {
        var links = links();

        assertFalse(links.isEmpty(), "the sample page has anchors, and each should be a button.link");
        assertTrue(
                links.getFirst().attributes().classes().contains("link"),
                "ADR-0293's variant, so a link in a sentence reads as a link");
    }

    @Test
    @DisplayName("and pressing it hands the href to the action the document named")
    void pressingALinkReachesTheModel() {
        var link = links().getFirst();
        assertNotNull(link.onPress(), "link= in html.kdl is what wires this, and nothing in Java does");

        link.onPress().run();
        frame();

        var followed = String.valueOf(Models.observable(model, "html.followed").get());
        assertTrue(
                followed.startsWith("Followed: http"),
                () -> "the application should have been handed the href, and was handed: " + followed);
    }
}
