package io.github.digitalsmile.goldberry.widgets.nav.wizard;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The action bar — a **part**, and a dialog's bar in everything but its name.
///
/// The buttons are written in the canonical order, Back then Next, and the
/// direction is the theme's: `wizard-actions { flex-direction: row-reverse }`
/// is Windows' order, exactly as `dialog-actions` takes it. A widget that read
/// the operating system to lay itself out would be one whose golden images
/// differ per machine.
///
/// @param children the buttons
record WizardActions(List<Widget> children) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "wizard-actions";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
