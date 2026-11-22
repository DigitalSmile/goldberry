package dev.goldberry.widgets.core.icon;

import java.util.Objects;

import dev.goldberry.icon.Icon;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Painter;
import dev.goldberry.render.model.LogicalSize;

/// What an icon's box is drawn with: the icon and its colour, as a value.
///
/// A record, so that two frames that draw the same icon in the same colour
/// hand the render tree two **equal** painters. The tree compares painters to
/// decide whether a box changed, and a lambda minted on every render is never
/// equal to the last one: every icon on screen was repainted on every frame
/// anything else moved, and past a handful of them the whole window was.
///
/// @param icon the outline, compared by value
/// @param argb the colour, `0xAARRGGBB`, not premultiplied
record IconPainter(Icon icon, int argb) implements Painter {

    IconPainter {
        Objects.requireNonNull(icon, "icon");
    }

    @Override
    public void paint(Frame frame, LogicalSize size) {
        IconPaint.paint(frame, size, icon, argb);
    }
}
