package dev.goldberry.example.ui.windows;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.text.Text;

/// One card of the windows screen in a session of its own, with the showcase's
/// stylesheets, so it is laid out as the window lays it out.
final class CardSession implements AutoCloseable {

    private final ShowcaseScene scene = new ShowcaseScene();

    final Session session;

    CardSession(Widget card) {
        session = Offscreen.of(720, 900)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .session(card);
    }

    /// The content of the text with this id.
    String text(String id) {
        var element = session.byId(id).orElseThrow();
        if (element.widget() instanceof Text text) {
            return text.content();
        }
        throw new AssertionError(
                "#" + id + " is a " + element.widget().getClass().getSimpleName() + ", not a text");
    }

    /// Every text under the node with this id, in tree order.
    List<String> texts(String id) {
        var found = new ArrayList<String>();
        walk(session.byId(id).orElseThrow()).forEach(element -> {
            if (element.widget() instanceof Text text) {
                found.add(text.content());
            }
        });
        return found;
    }

    /// Where the node with this id was painted, in the window's coordinates.
    LogicalRect rect(String id) {
        var element = session.byId(id).orElseThrow();
        return session.regions().stream()
                .filter(region -> region.owner() == element)
                .findFirst()
                .orElseThrow(() -> new AssertionError("#" + id + " was not painted"))
                .painted();
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
