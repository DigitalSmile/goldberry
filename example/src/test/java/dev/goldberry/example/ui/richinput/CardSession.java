package dev.goldberry.example.ui.richinput;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import dev.goldberry.css.StyleElement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.text.Text;

/// One card of the Rich input screen in a session of its own, with the
/// showcase's stylesheets, so its fields have the widths and faces the window
/// gives them.
final class CardSession implements AutoCloseable {

    private final ShowcaseScene scene = new ShowcaseScene();

    final Session session;

    CardSession(Widget card) {
        this(card, Theme.NORD_DARK);
    }

    CardSession(Widget card, Theme theme) {
        session = Offscreen.of(560, 1200).stylesheets(scene.stylesheets(theme)).session(card);
        session.frame();
    }

    /// The content of the text with this id.
    String text(String id) {
        var element = session.byId(id).orElseThrow(() -> new AssertionError("no #" + id));
        if (element.widget() instanceof Text text) {
            return text.content();
        }
        throw new AssertionError(
                "#" + id + " is a " + element.widget().getClass().getSimpleName() + ", not a text");
    }

    /// The widget with this id, as a `type`: the element found, or the nearest
    /// one above it that is one, since a widget's box carries its id too.
    <T> T widget(String id, Class<T> type) {
        return widget(session, id, type);
    }

    /// [#widget(String, Class)] in any session.
    static <T> T widget(Session session, String id, Class<T> type) {
        StyleElement at = session.byId(id).orElseThrow(() -> new AssertionError("no #" + id));
        while (at instanceof Element element) {
            if (type.isInstance(element.widget())) {
                return type.cast(element.widget());
            }
            at = element.parent();
        }
        throw new AssertionError("#" + id + " is not a " + type.getSimpleName());
    }

    /// Every widget of `type` under the node with this id, in tree order.
    <T> List<T> all(String id, Class<T> type) {
        return walk(session.byId(id).orElseThrow(() -> new AssertionError("no #" + id)))
                .map(Element::widget)
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    /// Where the node with this id, or the first painted node under it, was
    /// painted, in the window's coordinates.
    LogicalRect rect(String id) {
        var under = new HashSet<Object>(walk(session.byId(id).orElseThrow()).toList());
        return session.regions().stream()
                .filter(region -> under.contains(region.owner()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("#" + id + " was not painted"))
                .painted();
    }

    static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(CardSession::walk));
    }

    @Override
    public void close() {
        session.close();
        scene.close();
    }
}
