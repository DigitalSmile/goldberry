package dev.goldberry.example.ui.search;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The keys the palette answers while its field has the keyboard: the arrows,
/// `Enter` and `Tab`.
///
/// On the **capture** phase, which is the point of a wrapper: the field is
/// focused and would otherwise see the keys first, and `Enter` would reach the
/// dialog around the palette as a press of a button it does not have. A key
/// the handler takes is consumed; every other key, letters and `Esc` included,
/// goes on to the field and the dialog as before.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#what-a-custom-widget-implements).
///
/// @param child  the palette
/// @param onKey  answers whether it took a key that went down
record PaletteKeys(Widget child, Predicate<KeyEvent> onKey) implements Widget.Leaf, Styled, Paints, Handles {

    @Override
    public String cssType() {
        return "palette-keys";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return List.of(child);
    }

    @Override
    public void onKeyCapture(KeyEvent event) {
        if (event.kind() == KeyEvent.Kind.PRESSED && onKey.test(event)) {
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new)).direction(FlexDirection.COLUMN);
    }
}
