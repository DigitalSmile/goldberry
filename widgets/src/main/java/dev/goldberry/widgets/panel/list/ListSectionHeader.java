package dev.goldberry.widgets.panel.list;

import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.handler.Measured;
import dev.goldberry.input.hit.Extent;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The node a stylesheet calls `list-section-header`: one section's header,
/// above its first row, or pinned over the top of the viewport.
///
/// Not a row. It takes no focus and no selection, so the keyboard, the
/// typeahead and `Home` and `End` never land on it.
///
/// @param section  the section it heads
/// @param header   the application's widget for it
/// @param pinned   whether this is the copy laid over the viewport's top
/// @param top      where the pinned copy sits below the list's top; ignored
///                 for a header in the flow
/// @param onHeight told the header's height whenever it changes, for a list
///                 that counts it into its first row's block
record ListSectionHeader(String section, Widget header, boolean pinned, double top, DoubleConsumer onHeight)
        implements Widget.Leaf, Styled, Paints, Measured {

    /// The class the pinned copy carries.
    static final String PINNED = "pinned";

    private static final Set<String> PINNED_CLASSES = Set.of(PINNED);

    /// A header in the flow, telling `onHeight` what height it came out as.
    static ListSectionHeader inFlow(String section, Widget header, DoubleConsumer onHeight) {
        return new ListSectionHeader(section, header, false, 0, onHeight);
    }

    /// The pinned copy, `top` below the list's top.
    static ListSectionHeader pinnedAt(String section, Widget header, double top) {
        return new ListSectionHeader(section, header, true, top, _ -> {});
    }

    @Override
    public String cssType() {
        return "list-section-header";
    }

    @Override
    public Set<String> classes() {
        return pinned ? PINNED_CLASSES : Set.of();
    }

    /// Keyed by the section, so a header keeps its state across frames however
    /// the window moves; the pinned copy by a key of its own, so it is one node
    /// whichever section it shows.
    @Override
    public Object key() {
        return pinned ? "list-section-header:pinned" : "list-section-header:" + section;
    }

    @Override
    public List<Widget> children() {
        return List.of(header);
    }

    @Override
    public void measured(Extent bounds, Extent part) {
        onHeight.accept(bounds.height());
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        var box = Box.of().style(style).children(boxes.toArray(Box[]::new));
        if (!pinned) {
            return box;
        }
        // Out of the flow, so the column keeps the height the spacers and rows
        // add up to, and painted over the rows it sits on.
        return box.position(Position.ABSOLUTE)
                .inset(new Insets(Length.points((float) top), Length.points(0), Length.UNDEFINED, Length.points(0)))
                .elevated(true);
    }
}
