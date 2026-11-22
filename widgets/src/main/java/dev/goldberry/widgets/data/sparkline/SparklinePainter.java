package dev.goldberry.widgets.data.sparkline;

import java.util.Objects;

import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Painter;
import dev.goldberry.render.model.LogicalSize;

/// What a sparkline's box is drawn with: its series and its colour, as a value.
///
/// A record for the reason an icon's painter is one: the render tree compares
/// painters to decide whether a box changed, so a sparkline whose values and
/// colour are the ones it drew last frame must hand it an equal painter.
///
/// @param line the series, its fill and its marker, with no attributes: an
///             `id` or a class says nothing about what is drawn
/// @param argb the colour, `0xAARRGGBB`, not premultiplied
record SparklinePainter(Sparkline line, int argb) implements Painter {

    SparklinePainter {
        Objects.requireNonNull(line, "line");
    }

    @Override
    public void paint(Frame frame, LogicalSize size) {
        line.paint(frame, size, argb);
    }
}
