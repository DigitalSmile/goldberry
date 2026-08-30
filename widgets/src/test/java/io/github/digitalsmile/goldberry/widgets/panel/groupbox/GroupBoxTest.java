package io.github.digitalsmile.goldberry.widgets.panel.groupbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.css.StyleElement;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.select.Selector;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.panel.Described;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// `group-box` — §5's titled border group ([ADR-0164]).
///
/// The decision under test is the shape: a title **over** a bordered body rather
/// than a legend through the frame, which §10's subset cannot express. That makes
/// the frame a widget of its own, and the two assertions worth having are that
/// the author's children land inside it and that an absent title is absent rather
/// than empty.
class GroupBoxTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// Two parts, both selectable, because a border on the outer box would
    /// enclose the title as well — `tab-rule`'s argument, one widget later.
    @Test
    @DisplayName("a titled group box is a title over a bordered body")
    void titled() {
        var tree = new ElementTree(new GroupBox("Appearance", new Text("Theme")));

        assertEquals(1, Described.of(tree, GroupBox.GroupBoxTitle.class).size());
        assertEquals(1, Described.of(tree, GroupBox.GroupBoxBody.class).size());
        assertEquals(
                "Appearance",
                Described.first(tree, GroupBox.GroupBoxTitle.class).text());
    }

    /// §5's frame is 8px round with a 1px edge, and the header fills the top of
    /// it — so the header's top corners are the frame's less its border, and its
    /// bottom ones are square where the body carries on underneath. That is
    /// `border-radius: 7px 7px 0 0`, which the stylesheet has said since the
    /// widget shipped and which the engine dropped with a warning until
    /// ADR-0216: the header drew four square corners, and two of them spilled
    /// out of the frame's curve.
    @Test
    @DisplayName("the header's top corners follow the frame and its bottom ones do not")
    void headerCornersFollowTheFrame() {
        assertEquals(Corners.all(8), styleOf("group-box").decoration().corners(), "§5's frame");
        assertEquals(
                new Corners(7, 7, 0, 0),
                styleOf("group-box-title").decoration().corners(),
                "the frame's radius less its border on top, square underneath");
    }

    /// A node that exists only to be styled — `SegmentedTest`'s probe, for its
    /// reason: what is under test is a rule in the catalog, not a widget.
    private record Probe(String type) implements StyleElement {

        @Override
        public String id() {
            return null;
        }

        @Override
        public java.util.Set<String> classes() {
            return java.util.Set.of();
        }

        @Override
        public StyleElement parent() {
            return null;
        }

        @Override
        public boolean hasState(Selector.PseudoClass state) {
            return false;
        }
    }

    private static ComputedStyle styleOf(String type) {
        var resolver = new StyleResolver(Controls.stylesheets(Theme.NORD_DARK));
        return ComputedStyle.of(resolver.resolve(new Probe(type)), CssLength.Context.DEFAULT);
    }

    /// An empty heading with `gap` above it is a gap nobody asked for.
    @Test
    @DisplayName("no title means no title node, not an empty one")
    void untitled() {
        var tree = new ElementTree(new GroupBox(null, new Text("Just the frame")));

        assertTrue(Described.of(tree, GroupBox.GroupBoxTitle.class).isEmpty());
        assertEquals(1, Described.of(tree, GroupBox.GroupBoxBody.class).size());
    }

    @Test
    @DisplayName("a blank title is the same as none")
    void blankTitle() {
        assertFalse(new GroupBox("   ", new Text("x")).hasTitle());
        assertTrue(new GroupBox("Appearance", new Text("x")).hasTitle());
    }

    /// The whole reason the frame is a widget of its own.
    @Test
    @DisplayName("the author's children are inside the frame, not beside the title")
    void childrenAreInTheBody() {
        var tree = new ElementTree(new GroupBox("Appearance", new Text("Theme")));

        assertEquals(
                1, Described.first(tree, GroupBox.GroupBoxBody.class).children().size());
    }

    /// The title is a property and not the node's argument, because the argument
    /// position is where a container's *children* start.
    @Test
    @DisplayName("a group box inflates from markup, with the title as a property")
    void inflates() {
        var widget = Widgets.inflater()
                .inflate(KdlParser.parse("group-box title=\"Appearance\" { text \"Theme\" }")
                        .getFirst());
        var box = assertInstanceOf(GroupBox.class, widget);

        assertEquals("Appearance", box.title());
        assertEquals(1, box.content().size(), "the author's one child, not the two parts");
        assertEquals(2, box.children().size(), "which are the heading and the frame");
    }
}
