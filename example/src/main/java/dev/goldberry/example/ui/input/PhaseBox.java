package dev.goldberry.example.ui.input;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// A box that says when a press passes through it, and in which phase.
///
/// The smallest widget that takes part in input: a leaf with children that
/// paints its own box and implements [Handles]. It reports a press three ways —
/// on the way down in `onPointerCapture`, as the target, and on the way back up
/// in `onPointer` — and, when `consumes` is set, stops the press on its way up.
///
/// Read more: [How an event travels](https://goldberry.dev/docs/guide/input.html#how-an-event-travels).
///
/// @param name       what the trace calls this box
/// @param consumes   whether this box calls `consume()` when the press reaches it
///                   on the way up
/// @param trace      told one line per phase
/// @param content    what the box holds
/// @param attributes its id and classes
record PhaseBox(String name, boolean consumes, Consumer<String> trace, List<Widget> content, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles {

    PhaseBox {
        content = List.copyOf(content);
    }

    @Override
    public String cssType() {
        return "phase-box";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public List<Widget> children() {
        return content;
    }

    @Override
    public void onPointerCapture(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.PRESSED) {
            trace.accept("capture  " + name);
        }
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() != PointerEvent.Kind.PRESSED) {
            return;
        }
        var target = event.target().widget() == this;
        trace.accept((target ? "target   " : "bubble   ") + name + (consumes ? "  (consumed here)" : ""));
        if (consumes) {
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
