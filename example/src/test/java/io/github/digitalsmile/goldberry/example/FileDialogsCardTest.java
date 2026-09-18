package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.example.ui.FileDialogsCard;
import io.github.digitalsmile.goldberry.render.dialog.FileChoice;
import io.github.digitalsmile.goldberry.render.dialog.FileDialogKind;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The Basic screen's dialogs card, pressed rather than photographed.
///
/// `GalleryGoldenTest` can show three buttons and a line of text. It cannot show
/// the card's actual subject, which is that **a dialog answers later** — the
/// reason this card is Java and not `basic.kdl`, and the reason it has a state at
/// all (ADR-0287).
///
/// So this presses the buttons, with a host that says what the user did.
class FileDialogsCardTest {

    private TourTestHost host;
    private ElementTree tree;
    private Fonts fonts;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        host = new TourTestHost(List.of());
        tree = new ElementTree(new FileDialogsCard(), host);
        render();
    }

    /// Closed, because a `Fonts` holds native faces.
    ///
    /// It was created in `render()` and never released: a suite that renders a
    /// card in every test opened one set of faces per test and closed none of
    /// them (the 2026-09-18 review, §6).
    @AfterEach
    void tearDown() {
        if (fonts != null) {
            fonts.close();
            fonts = null;
        }
    }

    private void render() {
        tree.flush();
        if (fonts == null) {
            fonts = Fonts.bundled();
        }
        new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), fonts).render(tree);
    }

    /// Presses the button with that id, the way its own key handler does.
    private void press(String id) {
        var button = find(tree.root(), id);
        assertNotNull(button, "nothing with id " + id + " is on the card");
        assertFalse(button.disabled(), id + " is disabled, so a user could not have pressed it");
        button.onPress().run();
        render();
    }

    private static Button find(Element element, String id) {
        if (element.widget() instanceof Button button
                && id.equals(button.attributes().id())) {
            return button;
        }
        for (var child : element.children()) {
            var found = find(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /// The line the card reports into.
    private String answer() {
        var found = new ArrayList<String>();
        collect(tree.root(), found);
        assertEquals(1, found.size(), "expected exactly one #dialog-result");
        return found.getFirst();
    }

    private static void collect(Element element, List<String> into) {
        if (element.widget() instanceof Text text
                && "dialog-result".equals(text.attributes().id())) {
            into.add(text.content());
        }
        for (var child : element.children()) {
            collect(child, into);
        }
    }

    @Test
    @DisplayName("says nothing has been chosen until something has")
    void startsIdle() {
        assertTrue(answer().startsWith("Nothing chosen yet"));
    }

    @Test
    @DisplayName("Open images asks for several existing files, filtered")
    void openAsksForImages() {
        host.dialogs().answerWith(FileChoice.of(List.of(Path.of("/tmp/a.png"), Path.of("/tmp/b.png"))));

        press("dialog-open");

        var spec = host.dialogs().shown().getFirst();
        assertEquals(FileDialogKind.OPEN_FILE, spec.kind());
        assertTrue(spec.allowMany(), "an import takes more than one picture");
        assertEquals(List.of("png", "jpg", "jpeg"), spec.filters().getFirst().extensions());
        assertTrue(answer().contains("a.png"), answer());
        assertTrue(answer().contains("b.png"), answer());
    }

    @Test
    @DisplayName("Save a board asks for one path that need not exist yet")
    void saveAsksForOnePath() {
        host.dialogs().answerWith(FileChoice.of(Path.of("/tmp/board.png")));

        press("dialog-save");

        var spec = host.dialogs().shown().getFirst();
        assertEquals(FileDialogKind.SAVE_FILE, spec.kind());
        assertFalse(spec.allowMany(), "a save dialog produces one path, and the spec refuses to say otherwise");
        assertTrue(spec.startingPoint().isPresent(), "a save dialog suggests a name");
        assertTrue(answer().contains("board.png"), answer());
    }

    @Test
    @DisplayName("Pick a folder asks for a directory, and for no filters")
    void folderAsksForNoFilters() {
        host.dialogs().answerWith(FileChoice.of(Path.of("/tmp/boards")));

        press("dialog-folder");

        var spec = host.dialogs().shown().getFirst();
        assertEquals(FileDialogKind.OPEN_FOLDER, spec.kind());
        assertEquals(List.of(), spec.filters(), "a folder dialog has nothing to filter");
    }

    @Test
    @DisplayName("a cancel is reported as a cancel and not as a failure")
    void cancelIsNotAFailure() {
        // Nothing scripted, so the headless dialogs cancel — which is what an
        // unprepared user does.
        press("dialog-open");

        assertTrue(answer().startsWith("Cancelled"), answer());
    }

    @Test
    @DisplayName("a platform that refuses says so, in its own words")
    void failureCarriesTheMessage() {
        host.dialogs().answerWith(FileChoice.failed("no portal and no zenity"));

        press("dialog-save");

        assertTrue(answer().contains("no portal and no zenity"), answer());
    }

    @Test
    @DisplayName("reports the filter the platform said was selected, when it says")
    void reportsTheFilter() {
        host.dialogs()
                .answerWith(new FileChoice.Chosen(
                        List.of(Path.of("/tmp/a.png")),
                        java.util.Optional.of(
                                io.github.digitalsmile.goldberry.render.dialog.FileFilter.of("Images", "png"))));

        press("dialog-open");

        assertTrue(answer().contains("Images"), answer());
    }

    @Test
    @DisplayName("the buttons are live, because a golden must be of the application that runs")
    void buttonsAreNotDisabled() {
        assertFalse(find(tree.root(), "dialog-open").disabled());
        assertFalse(find(tree.root(), "dialog-save").disabled());
        assertFalse(find(tree.root(), "dialog-folder").disabled());
    }

    @Test
    @DisplayName("a backend with no dialogs greys them out rather than failing on the click")
    void unsupportedGreysThemOut() {
        // A fresh tree rather than a re-render: `supported()` is read in `build`
        // and is a property of the backend, so nothing marks the card dirty when
        // it changes — and nothing needs to, because on a real backend it never
        // does.
        host.dialogs().supported(false);
        tree = new ElementTree(new FileDialogsCard(), host);
        render();

        assertTrue(find(tree.root(), "dialog-open").disabled());
        assertTrue(find(tree.root(), "dialog-save").disabled());
        assertTrue(find(tree.root(), "dialog-folder").disabled());
    }
}
