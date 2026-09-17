package io.github.digitalsmile.goldberry.example.motion;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.digitalsmile.goldberry.css.value.CssColor;
import io.github.digitalsmile.goldberry.motion.Easing;
import io.github.digitalsmile.goldberry.paint.CanvasStyle;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.paint.Path;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;

/// A floor of glazed tiles that settles into place and then slowly re-glazes
/// itself, drawn on a `canvas` (`docs/gaps.md` G41, ADR-0354).
///
/// ## The choreography
///
/// 1. **The settle.** Every tile drops in from 20 pixels above, turned up to
///    four degrees, and lands flat. Each one waits 45 ms per place of distance
///    from the **focus**, so the floor lands as a ripple spreading out from one
///    tile. [Settle] is that arithmetic.
/// 2. **The glaze.** Once the last tile has landed, one tile every 1.3 seconds
///    takes the glaze of a neighbouring band, cross-fading through OKLCH over
///    400 ms. Which tile is decided by a counter, so the sequence is the same on
///    every run.
///
/// Pressing a tile makes it the focus and plays the settle again from there.
///
/// ## Who keeps the frames coming
///
/// Two different things, because the floor needs two different kinds of
/// frame. **While something moves**, the canvas asks for the next frame itself
/// through `Canvas.animating`, with [#isMoving] as the question, so the settle
/// and a fade run at the display's rate and stop asking the moment they end.
/// **Between swaps** nothing moves for 900 ms, and a canvas that kept asking
/// would repaint a still picture fifty times. A timer on the host starts the
/// next swap instead ([#swap()]) and the canvas asks again only for the fade.
///
/// ## Reduced motion
///
/// The floor is drawn at rest and each swap is a cut rather than a fade, so
/// [#isMoving] never asks for a frame. The glaze still changes, because the change
/// carries information (the floor is alive) and only the movement is dropped.
///
/// Confined to the UI thread, like the state that owns it.
public final class TileFloor {

    /// How long a glaze takes to cross-fade.
    public static final double FADE_MILLIS = 400;

    /// How long after one swap the next one starts.
    public static final long SWAP_EVERY_MILLIS = 1300;

    /// The gap between tiles, and around the floor.
    private static final double GAP = 4;

    private static final double RADIUS = 3;

    /// Nord's aurora and frost, one per band, top to bottom.
    private static final List<Integer> BANDS = List.of(0xFF88C0D0, 0xFF81A1C1, 0xFFA3BE8C, 0xFFEBCB8B, 0xFFD08770);

    private final int columns;
    private final int rows;
    private final Settle settle;
    private final int[] glazes;

    private int focusColumn;
    private int focusRow;

    /// When the settle started, on the frame clock, or NaN until the next paint
    /// reads the clock. A replay sets it back to NaN: whoever asked for it has no
    /// frame time, and the painter does.
    private double startedAt = Double.NaN;

    private int swaps;
    private int swapTile = -1;
    private int swapFrom;
    private int swapTo;

    /// When the current fade started, NaN before the paint that starts it, or
    /// negative infinity when nothing has been swapped yet.
    private double swapStartedAt = Double.NEGATIVE_INFINITY;

    /// A floor of `columns` by `rows`, settling from `focus`.
    public TileFloor(int columns, int rows, Settle settle) {
        if (columns <= 0 || rows <= 0) {
            throw new IllegalArgumentException("a floor has at least one tile, not " + columns + "x" + rows);
        }
        this.columns = columns;
        this.rows = rows;
        this.settle = Objects.requireNonNull(settle, "settle");
        this.glazes = new int[columns * rows];
        for (var i = 0; i < glazes.length; i++) {
            glazes[i] = BANDS.get(bandOf(i / columns));
        }
        this.focusColumn = columns / 3;
        this.focusRow = rows / 2;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    /// The glaze tile `index` has settled on, ignoring any fade in progress.
    public int glaze(int index) {
        return swapTile == index ? swapTo : glazes[index];
    }

    /// How long tile `index` waits before it moves.
    public double delayOf(int index) {
        var column = index % columns;
        var row = index / columns;
        return settle.delayFor(Math.hypot(column - focusColumn, row - focusRow));
    }

    /// How long after the start the last tile lands.
    public double settledAfter() {
        var latest = 0.0;
        for (var i = 0; i < glazes.length; i++) {
            latest = Math.max(latest, delayOf(i));
        }
        return latest + settle.durationMillis();
    }

    /// Which way, and how far, tile `index` is turned when it starts — the same
    /// every run, and different enough between neighbours to read as scattered.
    static double turnOf(int index) {
        var hash = index * 0x9E3779B1;
        return ((hash >>> 16) & 0xFF) / 127.5 - 1;
    }

    /// Plays the settle again with the tile at (`column`, `row`) as the focus.
    public void replay(int column, int row) {
        focusColumn = Math.clamp(column, 0, columns - 1);
        focusRow = Math.clamp(row, 0, rows - 1);
        startedAt = Double.NaN;
    }

    /// Starts the next glaze swap. The fade starts on the next paint, where there
    /// is a frame time to start it at.
    public void swap() {
        if (swapTile >= 0) {
            glazes[swapTile] = swapTo;
        }
        var index = Math.floorMod(swaps * 7 + 3, glazes.length);
        var band = bandOf(index / columns);
        var neighbour = band == BANDS.size() - 1 ? band - 1 : band + 1;
        swapTile = index;
        swapFrom = glazes[index];
        swapTo = BANDS.get(glazes[index] == BANDS.get(neighbour) ? band : neighbour);
        swapStartedAt = Double.NaN;
        swaps++;
    }

    /// Whether anything on the floor is moving at `now`: a tile still settling, or
    /// a glaze still fading. What the canvas's `animating` predicate asks.
    public boolean isMoving(double now, boolean reducedMotion) {
        if (reducedMotion) {
            return false;
        }
        if (Double.isNaN(startedAt) || Double.isNaN(swapStartedAt)) {
            // A replay or a swap waiting for its first frame, which asking for
            // the frame is what brings.
            return true;
        }
        return now - startedAt < settledAfter() || now - swapStartedAt < FADE_MILLIS;
    }

    /// Whether the settle has finished at `now` — when the timer may start
    /// swapping.
    public boolean hasSettled(double now) {
        return !Double.isNaN(startedAt) && now - startedAt >= settledAfter();
    }

    /// A tile's place on the floor.
    public record Place(int column, int row) {}

    /// Which tile's place (`x`, `y`) is in, in a canvas of `size` — nothing off
    /// the floor. A point in the gap after a tile counts as that tile, so a press
    /// never falls between two.
    public Optional<Place> tileAt(double x, double y, LogicalSize size) {
        var width = tileWidth(size);
        var height = tileHeight(size);
        if (width <= 0 || height <= 0) {
            return Optional.empty();
        }
        var column = (int) Math.floor((x - GAP) / (width + GAP));
        var row = (int) Math.floor((y - GAP) / (height + GAP));
        if (column < 0 || column >= columns || row < 0 || row >= rows) {
            return Optional.empty();
        }
        return Optional.of(new Place(column, row));
    }

    /// Draws the floor as it is at `style.nowMillis()`.
    public void paint(Frame frame, LogicalSize size, CanvasStyle style) {
        var now = style.nowMillis();
        if (Double.isNaN(startedAt)) {
            startedAt = now;
        }
        if (Double.isNaN(swapStartedAt)) {
            swapStartedAt = now;
        }
        var width = tileWidth(size);
        var height = tileHeight(size);
        if (width <= 0 || height <= 0) {
            return;
        }
        var reduced = style.reducedMotion();
        var fade = reduced ? 1 : Math.clamp((now - swapStartedAt) / FADE_MILLIS, 0, 1);
        for (var i = 0; i < glazes.length; i++) {
            var pose = reduced ? Settle.Pose.REST : settle.at(now - startedAt, delayOf(i), turnOf(i));
            if (pose.opacity() <= 0) {
                continue;
            }
            // Whole places first, then pixels: the row is an integer division
            // on purpose.
            int column = i % columns;
            int row = i / columns;
            var x = GAP + column * (width + GAP);
            var y = GAP + row * (height + GAP);
            var tile = Rotated.of(
                    Path.roundRect(x, y, width, height, RADIUS),
                    x + width / 2,
                    y + height / 2,
                    pose.radians(),
                    0,
                    pose.offsetY());
            var colour = i == swapTile ? CssColor.mix(swapFrom, swapTo, Easing.EASE_ENTER.at(fade)) : glazes[i];
            frame.fillPath(tile, withAlpha(colour, pose.opacity()));
        }
    }

    private double tileWidth(LogicalSize size) {
        return (size.width() - GAP * (columns + 1)) / columns;
    }

    private double tileHeight(LogicalSize size) {
        return (size.height() - GAP * (rows + 1)) / rows;
    }

    private int bandOf(int row) {
        return Math.min(BANDS.size() - 1, row * BANDS.size() / rows);
    }

    private static int withAlpha(int argb, double opacity) {
        var alpha = (int) Math.round(((argb >>> 24) & 0xFF) * opacity);
        return alpha << 24 | (argb & 0x00FFFFFF);
    }

    @Override
    public String toString() {
        return "TileFloor[" + columns + "x" + rows + ", focus " + focusColumn + "," + focusRow + ", glazes "
                + Arrays.hashCode(glazes) + "]";
    }
}
