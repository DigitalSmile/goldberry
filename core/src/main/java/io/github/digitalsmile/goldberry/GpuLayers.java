package io.github.digitalsmile.goldberry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.render.DamageRect;
import io.github.digitalsmile.goldberry.render.GpuContent;
import io.github.digitalsmile.goldberry.render.GpuPlacement;

/// Which GPU layers a window shows after a frame: the ones the frame placed,
/// and, where only part of it was repainted, the ones it did not reach
/// (ADR-0481).
///
/// A partial repaint walks only what the damage touches, so a video beside a
/// blinking caret is not painted on the caret's frames and places nothing. It
/// is still on screen: its hole is still in the retained frame, and the
/// compositor, which draws every layer on every present, has to be told it is
/// there. So a layer from the last frame is kept when the damage does not touch
/// it, and dropped when it does, since then the frame would have placed it
/// again had it still been there.
final class GpuLayers {

    private GpuLayers() {}

    /// The layers to show after a frame.
    ///
    /// Paint order is kept on both sides: what the frame placed stays in the
    /// order it was placed, and a kept layer stays where it was relative to the
    /// layers placed again. Where the two orders cannot both hold -- a frame
    /// that placed again, in a new order, layers with a kept one between them
    /// -- the frame's order wins.
    ///
    /// @param previous what the last frame showed
    /// @param painted  what this frame placed, in paint order
    /// @param damage   what this frame repainted, or null when it repainted
    ///                 everything, which makes `painted` the whole answer
    static List<GpuPlacement> merge(
            List<GpuPlacement> previous, List<GpuPlacement> painted, @Nullable List<DamageRect> damage) {
        if (damage == null || previous.isEmpty()) {
            return painted;
        }
        Set<GpuContent> placedAgain = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var placement : painted) {
            placedAgain.add(placement.content());
        }
        var shown = new ArrayList<GpuPlacement>(previous.size() + painted.size());
        var next = 0;
        for (var old : previous) {
            if (placedAgain.contains(old.content())) {
                // Where it was: everything the frame placed up to it, in the
                // frame's order, and then it.
                while (next < painted.size()) {
                    var placed = painted.get(next++);
                    shown.add(placed);
                    if (placed.content() == old.content()) {
                        break;
                    }
                }
            } else if (!touched(old, damage)) {
                shown.add(old);
            }
        }
        while (next < painted.size()) {
            shown.add(painted.get(next++));
        }
        return List.copyOf(shown);
    }

    private static boolean touched(GpuPlacement placement, List<DamageRect> damage) {
        for (var rect : damage) {
            if (placement.overlaps(rect)) {
                return true;
            }
        }
        return false;
    }
}
