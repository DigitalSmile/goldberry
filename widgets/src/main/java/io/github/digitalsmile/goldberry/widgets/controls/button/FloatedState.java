package io.github.digitalsmile.goldberry.widgets.controls.button;

import java.util.HashSet;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// A [Floated]'s one piece of state: the overlay it put its button in.
final class FloatedState extends State<Floated> {

    private @Nullable Overlay overlay;

    /// What is floating now, so a rebuild with the same button does not take
    /// it down and put it back — which would restart its hover and its focus.
    private @Nullable Floated floating;

    @Override
    public Widget build(BuildContext context) {
        var host = context.host().orElse(null);
        var wanted = widget();
        current = wanted;
        if (host != null && (floating == null || !presentsAs(wanted, floating))) {
            attach(host, wanted);
        }
        // Nothing in the tree: the button is in the overlay layer, and a
        // placeholder here would take the space the floating button is meant
        // not to.
        return Widget.nothing();
    }

    /// What was last handed to the tree, so the floating button's press runs
    /// the handler of *this* build rather than the one it was attached with.
    private @Nullable Floated current;

    /// Whether two descriptions float the same button: the same word, icon,
    /// disablement, attributes and corner. **Not the handler** — a lambda is a
    /// new object on every build, so an equality that included it would take
    /// the button down and put it back on every frame, losing its hover and
    /// its focus each time. The handler is read at the press instead.
    static boolean presentsAs(Floated a, Floated b) {
        var x = a.button();
        var y = b.button();
        return a.corner() == b.corner()
                && x.label().equals(y.label())
                && x.icon() == y.icon()
                && x.disabled() == y.disabled()
                && x.attributes().equals(y.attributes());
    }

    private void attach(Host host, Floated wanted) {
        detach();
        var button = wanted.button();
        var classes = new HashSet<>(button.attributes().classes());
        classes.add(Button.FLOAT);
        var floated = new Button(button.label(), button.icon(), this::press, button.disabled(), button.attributes())
                .withAttributes(button.attributes().classes(classes.toArray(String[]::new)));
        overlay = host.overlay(floated, wanted.corner());
        floating = wanted;
    }

    /// The press, forwarded to the latest description's handler.
    private void press() {
        var latest = current;
        if (latest != null && latest.button().onPress() != null) {
            latest.button().onPress().run();
        }
    }

    private void detach() {
        if (overlay != null) {
            overlay.remove();
            overlay = null;
        }
        floating = null;
    }

    @Override
    protected void dispose() {
        detach();
        super.dispose();
    }

    /// The overlay this state holds, for a test.
    @Nullable
    Overlay overlay() {
        return overlay;
    }
}
