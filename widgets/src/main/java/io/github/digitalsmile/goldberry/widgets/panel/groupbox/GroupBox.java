package io.github.digitalsmile.goldberry.widgets.panel.groupbox;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Markup;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// A titled cluster of settings — `docs/core-widgets.md` §5's `group-box`,
/// "titled border group for settings clusters".
///
/// ```kdl
/// group-box title="Appearance" {
///     row { text "Theme"; spacer; select bind="theme" { option "Dark" } }
///     row { text "Density"; spacer; segmented bind="density" { option "Regular" } }
/// }
/// ```
///
/// ## The frame encloses the title
///
/// A `fieldset` puts its legend **through** the border, with the frame broken
/// behind the words. That needs a box painting over its parent's edge, which
/// means either a notch (nothing in §10's subset expresses one) or the page's own
/// background painted behind the title — wrong the moment a `group-box` sits on
/// anything but the page.
///
/// The first cut put the title **above** the frame instead. That was a mistake and
/// it was reported as one: "what is the purpose of group box? I thought I should
/// group elements with title and border." A heading floating over a bordered box
/// is a heading and a `panel`. Nothing about it says the two belong together, and
/// an untitled one is indistinguishable from a `card`.
///
/// So the border goes round **both**: the title is a header row *inside* the
/// frame, with a rule under it. That is a titled group with one look and no
/// ambiguity, it needs nothing the subset has not got, and it wraps at a narrow
/// width where a legend through a border would break the frame ([ADR-0166]).
///
/// ## The parts
///
/// Two children, both selectable:
///
///   - `group-box-title` — the header row, absent entirely when there is no
///     title rather than present and empty. An empty box with a rule under it is
///     a line nobody asked for.
///   - `group-box-body` — what the author put in.
///
/// ## `content`, not `children`
///
/// The author's widgets are the record's `content`, because [#children()] is
/// overridden to describe the *parts* — the heading and the frame. A component
/// called `children` would have made the accessor and the override the same
/// method, and an author asking a `group-box` what is in it would have got its
/// chrome. Caught by a test that inflated one node and was told it had two.
///
/// @param title      the heading, or null for an untitled frame
/// @param content    what goes inside the frame
/// @param attributes the `id` and classes, which land on the `group-box` node
@Markup("group-box")
public record GroupBox(String title, List<Widget> content, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<GroupBox> {

    public GroupBox(String title, Widget... kids) {
        this(title, List.of(kids), Attributes.NONE);
    }

    public GroupBox {
        content = List.copyOf(content == null ? List.of() : content);
        attributes = attributes == null ? Attributes.NONE : attributes;
        // Blank and absent are the same thing: a heading of one space would take
        // a line and say nothing.
        title = title == null || title.isBlank() ? null : title;
    }

    /// Whether this frame has a heading over it.
    public boolean hasTitle() {
        return title != null;
    }

    @Override
    public String cssType() {
        return "group-box";
    }

    @Override
    public GroupBox withAttributes(Attributes value) {
        return new GroupBox(title, content, value);
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    /// The header row, if there is one, and the body — both **inside** the
    /// frame, which is this node.
    @Override
    public List<Widget> children() {
        var parts = new ArrayList<Widget>(2);
        if (title != null) {
            parts.add(new GroupBoxTitle(title));
        }
        parts.add(new GroupBoxBody(content));
        return List.copyOf(parts);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// Builds a `group-box` from markup.
    ///
    /// The title is a property rather than the node's argument, because the
    /// argument position is where a container's *children* start and a
    /// `group-box "Appearance" { … }` would read as a group box containing the
    /// word "Appearance".
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new GroupBox(node.stringProperty("title"), children, Attributes.of(node));
    }

    /// The header row at the top of the frame.
    record GroupBoxTitle(String text) implements Widget.Leaf, Styled, Paints {

        GroupBoxTitle {
            Objects.requireNonNull(text, "text");
        }

        @Override
        public String cssType() {
            return "group-box-title";
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style)
                    .children(Box.text(context.paragraph(style, text), style.color()));
        }
    }

    /// What the author put in, under the header row.
    record GroupBoxBody(List<Widget> children) implements Widget.Leaf, Styled, Paints {

        GroupBoxBody {
            children = List.copyOf(children == null ? List.of() : children);
        }

        @Override
        public String cssType() {
            return "group-box-body";
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }
}
