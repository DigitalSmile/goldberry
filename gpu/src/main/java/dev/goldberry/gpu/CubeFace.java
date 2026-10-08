package dev.goldberry.gpu;

/// A face of a cube texture, in the order every driver SDL runs on lays them
/// out as layers: +X, -X, +Y, -Y, +Z, -Z.
///
/// A `TextureCube` sampled along a direction reads the face that direction's
/// largest component points at: (1, 0, 0) reads [#POSITIVE_X], (0, -1, 0)
/// [#NEGATIVE_Y].
public enum CubeFace {
    /// The face +X points at: layer 0.
    POSITIVE_X(0),
    /// The face -X points at: layer 1.
    NEGATIVE_X(1),
    /// The face +Y points at: layer 2.
    POSITIVE_Y(2),
    /// The face -Y points at: layer 3.
    NEGATIVE_Y(3),
    /// The face +Z points at: layer 4.
    POSITIVE_Z(4),
    /// The face -Z points at: layer 5.
    NEGATIVE_Z(5);

    private final int layer;

    CubeFace(int layer) {
        this.layer = layer;
    }

    /// The layer of the cube this face is.
    public int layer() {
        return layer;
    }

    /// The face that is layer `layer` of a cube.
    ///
    /// @throws IllegalArgumentException when `layer` is not 0 to 5
    public static CubeFace ofLayer(int layer) {
        for (var face : values()) {
            if (face.layer == layer) {
                return face;
            }
        }
        throw new IllegalArgumentException("a cube has six faces, layers 0 to 5, not " + layer);
    }
}
