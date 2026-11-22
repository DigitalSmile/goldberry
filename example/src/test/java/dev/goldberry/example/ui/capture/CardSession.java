package dev.goldberry.example.ui.capture;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import dev.goldberry.css.StyleElement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.text.Text;

/// One card of the Capture screen in a session of its own, with the showcase's
/// stylesheets, so it is laid out as the window lays it out.
final class CardSession implements AutoCloseable {

    private final ShowcaseScene scene = new ShowcaseScene();

    final Session session;

    CardSession(Widget card) {
        session = Offscreen.of(640, 1600)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .session(card);
        session.frame();
    }

    /// Every text under the node with this id, in tree order.
    List<String> texts(String id) {
        var found = new ArrayList<String>();
        walk(session.byId(id).orElseThrow(() -> new AssertionError("no #" + id)))
                .forEach(element -> {
                    if (element.widget() instanceof Text text) {
                        found.add(text.content());
                    }
                });
        return found;
    }

    /// The last text under the node with this id: a line's value, a caption.
    String text(String id) {
        return texts(id).getLast();
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

    /// The state above the node with this id that is a `type`: a card's, found
    /// from the card's own id.
    <S> S state(String id, Class<S> type) {
        StyleElement at = session.byId(id).orElseThrow(() -> new AssertionError("no #" + id));
        while (at instanceof Element element) {
            var state = element.state().orElse(null);
            if (type.isInstance(state)) {
                return type.cast(state);
            }
            at = element.parent();
        }
        throw new AssertionError("no " + type.getSimpleName() + " above #" + id);
    }

    /// Every widget of `type` under the node with this id, in tree order.
    <T> List<T> all(String id, Class<T> type) {
        return walk(session.byId(id).orElseThrow(() -> new AssertionError("no #" + id)))
                .map(Element::widget)
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(CardSession::walk));
    }

    @Override
    public void close() {
        session.close();
        scene.close();
    }
}
