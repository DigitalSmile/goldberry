package io.github.digitalsmile.goldberry.qr;

import java.util.Arrays;
import java.util.Objects;

/// A finished QR code: a square of dark and light modules, and nothing about how
/// big it is on a screen.
///
/// The output of [QrEncoder] and an immutable value, so the same code can be
/// drawn at three sizes, cached across a rebuild and handed between threads
/// without anyone copying it. A module is a **module**, not a pixel: turning
/// this into something with edges is the widget's problem and the reason the two
/// live in different modules.
///
/// The quiet zone is **not** here. §6.3 requires four light modules around a
/// code, and that is a property of where the code is put rather than of the
/// code: a matrix with a quiet zone baked in could not be drawn on a light
/// background it already matched, and would have to be un-padded by anything
/// that wanted the grid itself.
public final class QrMatrix {

    private final int version;
    private final Level level;
    private final int mask;
    private final int size;
    private final boolean[] modules;

    QrMatrix(int version, Level level, int mask, int size, boolean[] modules) {
        this.version = version;
        this.level = level;
        this.mask = mask;
        this.size = size;
        this.modules = modules.clone();
    }

    /// Which of the forty sizes this is, 1 to 40.
    public int version() {
        return version;
    }

    /// The error correction level it was encoded at.
    public Level level() {
        return level;
    }

    /// Which of the eight masks the penalty rules chose, 0 to 7.
    public int mask() {
        return mask;
    }

    /// How many modules across and down — `version × 4 + 17`.
    public int size() {
        return size;
    }

    /// Whether the module at `(x, y)` is dark, with `(0, 0)` at the top left.
    ///
    /// Outside the grid is **light**, not an exception: a caller drawing a quiet
    /// zone is asking about modules that are not there, and answering "light" is
    /// what the quiet zone is.
    public boolean isDark(int x, int y) {
        if (x < 0 || x >= size || y < 0 || y >= size) {
            return false;
        }
        return modules[y * size + x];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QrMatrix matrix
                && version == matrix.version
                && level == matrix.level
                && mask == matrix.mask
                && Arrays.equals(modules, matrix.modules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, level, mask, Arrays.hashCode(modules));
    }

    @Override
    public String toString() {
        return "QrMatrix[version=" + version + ", level=" + level + ", mask=" + mask + ", size=" + size + "]";
    }
}
