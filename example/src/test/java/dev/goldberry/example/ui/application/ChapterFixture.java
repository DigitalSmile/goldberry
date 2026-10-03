package dev.goldberry.example.ui.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import dev.goldberry.bind.runtime.Models;
import dev.goldberry.css.Theme;
import dev.goldberry.example.Showcase;
import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.example.docs.CardShape;
import dev.goldberry.example.ui.gallery.Documents;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.icon.Icon;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.Widgets;
import dev.goldberry.widgets.controls.badge.Badge;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// What the chapter tests share: the application's own model and actions, a
/// [GalleryContext] over them, and a session that draws a widget with the
/// window's stylesheets.
///
/// Confined to the thread that made it, and closed once.
public final class ChapterFixture implements AutoCloseable {

    private final ShowcaseScene scene = new ShowcaseScene();
    private final Showcase showcase = new Showcase();
    private final ShowcaseModel model = only(ShowcaseModel.class);
    private final ShowcaseModel.Actions actions = only(ShowcaseModel.Actions.class);
    private final Icon plus = Icon.bundled("plus", 16);
    private final Fonts fonts = Fonts.bundled();

    private <T> T only(Class<T> type) {
        return showcase.models().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    /// The model the context reads.
    public ShowcaseModel model() {
        return model;
    }

    /// A context over this fixture's model, with no documents of its own.
    public GalleryContext context() {
        return new GalleryContext(model, actions, new Documents(Widgets.inflater()), plus, () -> {});
    }

    /// A session drawing `root` at `width` by `height`, styled as the window is.
    public Session session(Widget root, int width, int height) {
        return Offscreen.of(width, height)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(root);
    }

    /// The window switched to the tab called `tab`, drawn as the gallery test
    /// draws it.
    public Session window(String tab) {
        return session(scene.root(tab), 1280, 900);
    }

    /// What the window does at the top of every frame: notices a change to the
    /// model made outside a document's action, and lets `session` draw it.
    public void refresh(Session session) {
        Models.refresh(model);
        session.advance(Duration.ofMillis(20));
    }

    /// The id of every gallery card under `root`, in tree order.
    public static List<String> cardIds(Element root) {
        var ids = new ArrayList<String>();
        walk(root).forEach(element -> {
            if (element.widget() instanceof Card card && CardShape.isGalleryCard(card)) {
                ids.add(card.attributes().id());
            }
        });
        return ids;
    }

    /// What the text or badge with this id says now.
    public static String says(Session session, String id) {
        return switch (session.byId(id).orElseThrow().widget()) {
            case Text text -> text.resolved();
            case Badge badge -> badge.resolved();
            case Widget other ->
                throw new AssertionError(id + " is a " + other.getClass().getSimpleName());
        };
    }

    /// `element` and everything under it.
    public static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(ChapterFixture::walk));
    }

    @Override
    public void close() {
        fonts.close();
        plus.close();
        scene.close();
    }
}
