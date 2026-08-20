package io.github.digitalsmile.goldberry.widgets.panel;

import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;

/// Walking a built element tree, for §5's container tests.
///
/// A widget's own `children()` says what it *would* describe; this says what the
/// element layer actually built — which is the difference the whole of
/// `collapse` turns on, since a closed section's body is absent from the tree
/// rather than present and hidden.
public final class Described {

    private Described() {
    }

    /// Every widget in the tree, depth first.
    public static List<Widget> in(ElementTree tree) {
        var found = new ArrayList<Widget>();
        collect(tree.root(), found);
        return List.copyOf(found);
    }

    /// Every widget of one kind, in order.
    public static <T> List<T> of(ElementTree tree, Class<T> type) {
        return in(tree).stream().filter(type::isInstance).map(type::cast).toList();
    }

    /// The first widget of one kind.
    public static <T> T first(ElementTree tree, Class<T> type) {
        return of(tree, type).stream().findFirst().orElseThrow(
                () -> new AssertionError("nothing of type " + type.getSimpleName()
                        + " was described; the tree holds " + types(tree)));
    }

    /// The element carrying the first widget of one kind, for firing input at it.
    public static Element elementOf(ElementTree tree, Class<?> type) {
        var found = find(tree.root(), type);
        if (found == null) {
            throw new AssertionError("nothing of type " + type.getSimpleName()
                    + " was described; the tree holds " + types(tree));
        }
        return found;
    }

    /// How many nodes of one CSS type the tree holds — the question a part asks,
    /// where the class itself may not be visible from the test's package.
    public static long counting(ElementTree tree, String cssType) {
        return in(tree).stream()
                .filter(widget -> widget instanceof Styled styled
                        && cssType.equals(styled.cssType()))
                .count();
    }

    private static List<String> types(ElementTree tree) {
        return in(tree).stream().map(widget -> widget.getClass().getSimpleName()).toList();
    }

    private static Element find(Element element, Class<?> type) {
        if (type.isInstance(element.widget())) {
            return element;
        }
        for (var child : element.children()) {
            var found = find(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void collect(Element element, List<Widget> into) {
        into.add(element.widget());
        for (var child : element.children()) {
            collect(child, into);
        }
    }
}
