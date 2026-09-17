package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// `keep-alive`: a tab shown once keeps its state while another is selected
/// ([ADR-0366]).
class TabsKeepAliveTest {

    /// A widget whose state is the thing that must survive.
    private record Probe(String name) implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new ProbeState();
        }

        @Override
        public Object key() {
            return name;
        }
    }

    private static final class ProbeState extends State<Probe> {

        @Override
        public Widget build(BuildContext context) {
            return new Text(widget().name());
        }
    }

    private static Tabs strip(String selected, boolean keepAlive) {
        return new Tabs(selected, new Tab("a", "Alpha", new Probe("in-a")), new Tab("b", "Beta", new Probe("in-b")))
                .keepAlive(keepAlive);
    }

    private static List<Element> byType(Element from, Class<?> widget) {
        var out = new ArrayList<Element>();
        collect(from, widget, out);
        return out;
    }

    private static void collect(Element from, Class<?> widget, List<Element> out) {
        if (widget.isInstance(from.widget())) {
            out.add(from);
        }
        for (var child : from.children()) {
            collect(child, widget, out);
        }
    }

    private static State<?> probe(ElementTree tree, String name) {
        return byType(tree.root(), Probe.class).stream()
                .filter(element -> ((Probe) element.widget()).name().equals(name))
                .findFirst()
                .orElseThrow()
                .state()
                .orElseThrow();
    }

    private static ElementTree roundTrip(boolean keepAlive, List<State<?>> seen) {
        var tree = new ElementTree(strip("a", keepAlive));
        seen.add(probe(tree, "in-a"));
        tree.update(strip("b", keepAlive));
        tree.flush();
        tree.update(strip("a", keepAlive));
        tree.flush();
        seen.add(probe(tree, "in-a"));
        return tree;
    }

    @Test
    @DisplayName("with keep-alive, a tab's content comes back with the state it left with")
    void kept() {
        var seen = new ArrayList<State<?>>();
        roundTrip(true, seen);

        assertSame(seen.get(0), seen.get(1));
    }

    @Test
    @DisplayName("without it, the content is built again, which is §5's lazy default")
    void lazyByDefault() {
        var seen = new ArrayList<State<?>>();
        roundTrip(false, seen);

        assertNotSame(seen.get(0), seen.get(1));
    }

    @Test
    @DisplayName("every shown tab is a page, only the selected one visible, and an unvisited tab is not built")
    void pages() {
        var tree = new ElementTree(strip("a", true));
        assertEquals(1, byType(tree.root(), TabPage.class).size(), "b has never been shown");

        tree.update(strip("b", true));
        tree.flush();
        var pages = byType(tree.root(), TabPage.class).stream()
                .map(element -> (TabPage) element.widget())
                .toList();

        assertEquals(List.of("a", "b"), pages.stream().map(TabPage::value).toList());
        assertTrue(pages.get(0).isHidden());
        assertTrue(!pages.get(1).isHidden());
    }

    @Test
    @DisplayName("a closed tab is let go")
    void closedIsDropped() {
        var tree = new ElementTree(strip("a", true));
        tree.update(strip("b", true));
        tree.flush();

        tree.update(new Tabs("b", new Tab("b", "Beta", new Probe("in-b"))).keepAlive(true));
        tree.flush();

        assertEquals(
                List.of("b"),
                byType(tree.root(), TabPage.class).stream()
                        .map(element -> ((TabPage) element.widget()).value())
                        .toList());
    }

    @Test
    @DisplayName("markup says keep-alive")
    void markup() {
        var tabs = (Tabs) Widgets.inflater()
                .inflate(KdlParser.parse("tabs value=\"a\" keep-alive=#true { tab value=\"a\" \"A\" }")
                        .getFirst());

        assertTrue(tabs.keepAlive());
    }

    @Test
    @DisplayName("a hidden page is not rendered: one page box, the selected one's")
    void hiddenIsNotRendered() {
        RendererRequirement.enforce();
        var tree = new ElementTree(strip("a", true));
        tree.update(strip("b", true));
        tree.flush();
        var renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());

        var pageBoxes = new ArrayList<Box>();
        collectBoxes(renderer.render(tree), pageBoxes);

        assertEquals(1, pageBoxes.size());
        assertEquals("b", ((TabPage) ((Element) pageBoxes.getFirst().owner()).widget()).value());
    }

    private static void collectBoxes(Box box, List<Box> into) {
        if (box.owner() instanceof Element element && element.widget() instanceof TabPage) {
            into.add(box);
        }
        for (var child : box.children()) {
            collectBoxes(child, into);
        }
    }
}
