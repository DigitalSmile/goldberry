package dev.goldberry.example.ui.controls;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import dev.goldberry.RendererRequirement;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// One gallery tab, mounted the way the window mounts it and driven through an
/// offscreen session: the application's own model, documents and stylesheets.
///
/// Tall enough that every card on the tab is drawn without scrolling, so a click
/// on any of them lands where a user's would.
public final class ChapterSession implements AutoCloseable {

    private final ShowcaseScene scene;
    private final Fonts fonts;
    private final Session session;

    private ChapterSession(String tab) {
        RendererRequirement.enforce();
        scene = new ShowcaseScene();
        fonts = Fonts.bundled();
        session = Offscreen.of(1280, 2600)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(scene.root(tab));
    }

    /// The tab called `tab`, mounted.
    public static ChapterSession open(String tab) {
        return new ChapterSession(tab);
    }

    public Session session() {
        return session;
    }

    public ShowcaseModel model() {
        return scene.model();
    }

    /// The value at `path` on the application's model.
    public Object value(String path) {
        return Models.observable(model(), path).get();
    }

    /// The element with this id, or a failure that names it.
    public Element element(String id) {
        return session.byId(id).orElseThrow(() -> new AssertionError("nothing with id " + id + " is on the screen"));
    }

    /// The widget with this id, as `type`.
    public <W extends Widget> W widget(String id, Class<W> type) {
        return type.cast(element(id).widget());
    }

    /// What the text with this id says now.
    public String text(String id) {
        return widget(id, Text.class).resolved();
    }

    /// The first element under `from` whose widget is a `type` that `test` accepts.
    public <W extends Widget> Optional<Element> find(Element from, Class<W> type, Predicate<W> test) {
        return walk(from)
                .filter(element -> type.isInstance(element.widget()) && test.test(type.cast(element.widget())))
                .findFirst();
    }

    /// The ids of the gallery cards on the screen `tab`, sorted.
    public Set<String> cardIds(String tab) {
        var ids = new TreeSet<String>();
        walk(element("screen-" + tab))
                .map(Element::widget)
                .filter(widget -> widget instanceof Card card
                        && card.attributes().classes().contains("wall-card"))
                .map(widget -> ((Card) widget).attributes().id())
                .forEach(ids::add);
        return ids;
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(ChapterSession::walk));
    }

    @Override
    public void close() {
        session.close();
        fonts.close();
        scene.close();
    }
}
