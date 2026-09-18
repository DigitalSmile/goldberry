package io.github.digitalsmile.goldberry.widgets.core.qrcode;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.paint.Path;
import io.github.digitalsmile.goldberry.qr.QrMatrix;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;

/// Where the modules go, in whole device pixels, and how they are painted.
///
/// This is the load-bearing half of the widget. A QR code is read by a camera
/// that thresholds a photograph and then samples the middle of each module; a
/// module whose edge falls between two device pixels is drawn half-dark down one
/// side, and enough of those turn a code into one a phone gives up on. So the
/// arithmetic is done in **device pixels and integers**, from the size of a
/// module down to where the whole thing sits.
///
/// ## The rule
///
/// A module is quantized **twice**, and the order matters.
///
/// 1. In logical pixels: the largest whole number of them that fits the box.
/// 2. Into device pixels: that number times the display scale, floored.
///
/// The code is then centred in the box with whatever is left over. A module is
/// always a whole number of device pixels, which is the promise; and it is
/// always the *same* code at the same size on the same box however the display
/// is scaled, which is ADR-0157's invariant and is why the logical step comes
/// first.
///
/// Quantizing straight into device pixels — the largest whole number of *those*
/// that fits — was the first attempt and breaks the second half. A 108-pixel box
/// holding 37 modules gets 2 device pixels a module at 100% and 5 at 200%,
/// because 5 is not 2 doubled: the code grows by a quarter when the window moves
/// to a retina display, which is a widget changing size with the scale. Going
/// through the logical number first gives 2 and 4, and 4 is 2 doubled.
///
/// The cost is slack at a fractional scale: at 150% a module of 3 logical pixels
/// is 4 device pixels rather than 4.5, so the code is a few per cent smaller
/// than the box could hold. That is the right way round. A code a few per cent
/// smaller is a code; a code with soft edges is not.
///
/// ## Why it draws a path and not rectangles
///
/// [Frame#fillRect] takes floats, and a float is not enough. A module boundary
/// at device pixel 152 is logical 101.333… at 150%, and the nearest `float` to
/// that multiplied back by 1.5 is not 152 — it is 152.000004, which Blend2D
/// dutifully draws as a pixel of `#1b1b1b` beside a run of `#1a1a1a`. One pixel,
/// invisible, and exactly the defect this widget exists to avoid. [Path] carries
/// **doubles**, whose round trip is off by a part in 10¹⁴ and lands on the pixel
/// Blend2D would have picked anyway.
///
/// Setting the frame's transform to `1 / factor` and drawing in whole device
/// pixels would be the obvious answer. It was unreachable when this was written
/// -- [Frame#transform] *replaces* the transform rather than composing with it
/// (ADR-0068), and what it would replace is the translation to this box's own
/// corner -- and [Frame#concat] closed that on the same day ([ADR-0390]). This
/// still draws a path, because the path is verified down to the decoded module
/// and a composed transform would be a second way to be right rather than a
/// better one.
///
/// The corner has to be on the pixel grid for any of this to be worth anything,
/// and it is: layout runs with Yoga's point scale factor set to the display
/// scale, so every box edge is rounded to a device pixel before a painter ever
/// sees it.
///
/// ## When there is no room
///
/// A code that cannot have at least one **logical** pixel per module draws
/// **nothing** rather than something. A 21-module code in a 16-pixel box is not
/// a small QR code, it is a grey square, and drawing it would be claiming a
/// scanner could read it. Logical rather than device for the reason above: a box
/// that drew nothing at 100% and something at 200% would be a widget that
/// appeared when the window moved to another display.
final class QrModules {

    private QrModules() {}

    /// Where a code sits, in device pixels from the box's own corner.
    ///
    /// @param module how many device pixels one module is
    /// @param left   the device pixels of slack to the left of the code
    /// @param top    the device pixels of slack above it
    /// @param extent how many device pixels the whole code is, both ways
    record Placement(int module, int left, int top, int extent) {}

    /// The placement of a code of `count` modules across inside `size`, or null
    /// when there is no room for one **logical** pixel per module.
    ///
    /// @param count  the modules across, quiet zone included
    /// @param size   the box, in logical pixels
    /// @param factor the display scale
    static @Nullable Placement place(int count, LogicalSize size, float factor) {
        var logicalModule = (int) Math.floor(Math.min(size.width(), size.height()) / count);
        if (logicalModule < 1) {
            return null;
        }
        // Floor at both steps. A box 99.6 device pixels wide has 99 of them to
        // draw in, and rounding up anywhere would put the last module's edge
        // outside the clip.
        var module = (int) Math.floor(logicalModule * (double) factor);
        var across = (int) Math.floor((double) size.width() * factor);
        var down = (int) Math.floor((double) size.height() * factor);
        var drawn = module * count;
        return new Placement(module, (across - drawn) / 2, (down - drawn) / 2, drawn);
    }

    /// Paints `matrix` into `size` with `quietZone` light modules around it.
    ///
    /// The paper is one fill under the whole thing, quiet zone included: §6.3's
    /// quiet zone is **part of the code**, and a code drawn straight onto a
    /// themed surface is a code with no quiet zone at all wherever the surface is
    /// not white.
    ///
    /// The ink is one subpath per **run** of dark modules along a row rather
    /// than one per module, and the whole code is one fill. A version 40 code is
    /// 31 329 modules and about half of them are dark; fifteen thousand
    /// rectangles a frame is a cost with nothing to show for it, because the
    /// runs are exact and the picture is identical either way.
    static void paint(Frame frame, LogicalSize size, QrMatrix matrix, int quietZone, int ink, int paper) {
        var factor = (double) frame.scale().factor();
        var count = matrix.size() + 2 * quietZone;
        var placement = place(count, size, (float) factor);
        if (placement == null) {
            return;
        }

        var left = placement.left() / factor;
        var top = placement.top() / factor;
        var right = (placement.left() + placement.extent()) / factor;
        var bottom = (placement.top() + placement.extent()) / factor;
        frame.fillPath(
                Path.builder()
                        .moveTo(left, top)
                        .lineTo(right, top)
                        .lineTo(right, bottom)
                        .lineTo(left, bottom)
                        .close()
                        .build(),
                paper);

        var module = placement.module();
        var dark = Path.builder();
        var runs = 0;
        for (var row = 0; row < matrix.size(); row++) {
            // Both edges of every module are the device pixel divided by the
            // scale, so a run's right edge and the next run's left edge are the
            // same number and two rows that touch leave no seam.
            var rowTop = (placement.top() + (row + quietZone) * module) / factor;
            var rowBottom = (placement.top() + (row + 1 + quietZone) * module) / factor;
            var start = -1;
            for (var column = 0; column <= matrix.size(); column++) {
                var here = column < matrix.size() && matrix.isDark(column, row);
                if (here && start < 0) {
                    start = column;
                } else if (!here && start >= 0) {
                    var runLeft = (placement.left() + (start + quietZone) * module) / factor;
                    var runRight = (placement.left() + (column + quietZone) * module) / factor;
                    dark.moveTo(runLeft, rowTop)
                            .lineTo(runRight, rowTop)
                            .lineTo(runRight, rowBottom)
                            .lineTo(runLeft, rowBottom)
                            .close();
                    runs++;
                    start = -1;
                }
            }
        }
        if (runs > 0) {
            frame.fillPath(dark.build(), ink);
        }
    }
}
