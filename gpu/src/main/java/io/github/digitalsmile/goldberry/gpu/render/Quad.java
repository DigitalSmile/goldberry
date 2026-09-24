package io.github.digitalsmile.goldberry.gpu.render;

/// Where [BuiltInShader#QUAD_VERTEX] puts a quad: the uniform block of eight
/// floats it reads, the destination in normalised device coordinates, then the
/// source in texture coordinates.
///
/// SDL_GPU's conventions on every backend: normalised device coordinates run
/// from -1 to 1 with y up, and a texture's coordinates from 0 to 1 from its top
/// left. So a rectangle in pixels, measured from the top left as the toolkit
/// measures everything, turns into both with one rule each, here, and nowhere
/// else.
///
/// @param destination where it is drawn, in normalised device coordinates
/// @param source      what of the texture it samples, in texture coordinates
public record Quad(Edges destination, Edges source) {

    /// How many vertices a quad is drawn with: two triangles.
    public static final int VERTICES = 6;

    /// Four edges, in whichever coordinates the owner says.
    ///
    /// @param left   the left edge
    /// @param top    the top edge
    /// @param right  the right edge
    /// @param bottom the bottom edge
    public record Edges(float left, float top, float right, float bottom) {}

    /// The whole of a texture, in texture coordinates.
    public static final Edges WHOLE_TEXTURE = new Edges(0, 0, 1, 1);

    /// The quad that covers pixels `x, y, width, height` of a target
    /// `targetWidth` by `targetHeight`, and samples the whole source.
    public static Quad of(int x, int y, int width, int height, int targetWidth, int targetHeight) {
        return of(x, y, width, height, targetWidth, targetHeight, WHOLE_TEXTURE);
    }

    /// The quad that covers pixels `x, y, width, height` of a target, and
    /// samples `source` of the texture, in texture coordinates.
    public static Quad of(int x, int y, int width, int height, int targetWidth, int targetHeight, Edges source) {
        if (width <= 0 || height <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException(
                    "quad " + width + "x" + height + " in a " + targetWidth + "x" + targetHeight + " target");
        }
        var destination = new Edges(
                2f * x / targetWidth - 1f,
                1f - 2f * y / targetHeight,
                2f * (x + width) / targetWidth - 1f,
                1f - 2f * (y + height) / targetHeight);
        return new Quad(destination, source);
    }

    /// The uniform block: destination, then source.
    public float[] uniforms() {
        return new float[] {
            destination.left(), destination.top(), destination.right(), destination.bottom(),
            source.left(), source.top(), source.right(), source.bottom()
        };
    }
}
