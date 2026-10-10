package dev.goldberry.image.lottie;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// A whole Lottie document, read: its size, its timing, its layers and the
/// compositions its precomp layers draw.
///
/// @param width      the canvas, in the document's units
/// @param height     the canvas, in the document's units
/// @param frameRate  frames a second
/// @param inPoint    the first frame
/// @param outPoint   the frame after the last
/// @param layers     the top level's layers, the first drawn on top
/// @param precomps   the assets that are compositions, by `id`
record Composition(
        double width,
        double height,
        double frameRate,
        double inPoint,
        double outPoint,
        List<Layer> layers,
        Map<String, List<Layer>> precomps) {

    Composition {
        layers = List.copyOf(layers);
        var copied = new HashMap<String, List<Layer>>();
        precomps.forEach((id, list) -> copied.put(id, List.copyOf(list)));
        precomps = Map.copyOf(copied);
    }

    /// How many frames one pass is.
    double frames() {
        return outPoint - inPoint;
    }
}
