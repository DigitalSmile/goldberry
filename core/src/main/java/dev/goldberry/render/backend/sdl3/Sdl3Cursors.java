package dev.goldberry.render.backend.sdl3;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.sdl.desktop.SdlColorCursor;
import dev.goldberry.natives.sdl.desktop.SdlCursors;
import dev.goldberry.natives.sdl.desktop.SdlSystemCursor;
import dev.goldberry.natives.sdl.window.SdlIconImage;
import dev.goldberry.render.Cursor;
import dev.goldberry.render.cursor.CursorPicture;
import dev.goldberry.render.cursor.CursorPictures;

/// What the pointer looks like on SDL: the application's picture for a shape
/// when it gave one, and the platform's own shape otherwise.
///
/// SDL's cursor is process-global — one pointer, one shape — so there is one of
/// these per backend, and the window that asks is by definition the one the
/// pointer is in.
///
/// ## Which size of a picture
///
/// SDL draws a picture cursor at a display scale in one of two ways. On
/// Wayland and macOS, and on Windows with the hint [SdlCursors] sets, the
/// surface it is given is the cursor at 100% and the larger sizes hung off it
/// are picked from by the display's scale, so every size goes in. On X11 the
/// surface is drawn pixel for pixel and the alternates are not read, so the
/// size the window's scale wants goes in alone.
final class Sdl3Cursors implements AutoCloseable {

    private static final Logger LOG = Logs.of(Sdl3Cursors.class);

    /// Whether SDL draws a picture cursor's pixels as they are, whatever the
    /// display's scale.
    private final boolean pixelForPixel;

    /// The cursors, created on first use.
    ///
    /// Lazy and optional, for the reason `SdlVideo.optionalDowncall` is: a
    /// `libgoldberry` built before the cursor symbols were exported would
    /// otherwise stop opening windows at all, to enable a nicety. An application
    /// that never sets a cursor never creates one either.
    private @Nullable SdlCursors cursors;

    private boolean unavailable;

    private Map<Cursor, CursorPictures> pictures = Map.of();

    /// @param videoDriver the video driver SDL is running, by SDL's name for it
    Sdl3Cursors(String videoDriver) {
        this.pixelForPixel = drawsPixelForPixel(videoDriver);
    }

    /// Whether `videoDriver` draws a picture cursor pixel for pixel, rather than
    /// choosing a size by the display's scale.
    static boolean drawsPixelForPixel(String videoDriver) {
        return "x11".equals(videoDriver);
    }

    /// Gives shapes the application's pictures, replacing any given before.
    void pictures(List<CursorPictures> given) {
        Objects.requireNonNull(given, "pictures");
        var byShape = new EnumMap<Cursor, CursorPictures>(Cursor.class);
        for (var picture : given) {
            byShape.put(picture.shape(), picture);
        }
        pictures = Map.copyOf(byShape);
        if (cursors != null) {
            cursors.forgetPictures();
        }
    }

    /// Shows `shape` on a window at a display scale of `scale`.
    void show(Cursor shape, double scale) {
        Objects.requireNonNull(shape, "shape");
        var sdl = cursors();
        if (sdl == null) {
            return;
        }
        var picture = pictures.get(shape);
        if (picture != null) {
            var sizes = sizes(picture, pixelForPixel, scale);
            var key = new PictureKey(shape, sizes.getFirst().width());
            if (sdl.set(key, () -> toSdl(sizes))) {
                return;
            }
            // Said once: SdlCursors remembers a picture it could not make, and
            // the platform's shape below is what the pointer shows meanwhile.
            LOG.trace("the {} cursor's picture could not be shown; showing the platform's", shape);
        }
        sdl.set(systemShape(shape));
    }

    /// The sizes of `picture` that go to SDL, the one it draws at 100% first.
    static List<CursorPicture> sizes(CursorPictures picture, boolean pixelForPixel, double scale) {
        return pixelForPixel ? List.of(picture.nearest(scale)) : picture.sizes();
    }

    /// The platform shape for a toolkit one, when the application gave it no
    /// picture.
    ///
    /// Two of the toolkit's shapes have no system cursor anywhere: `grab` and
    /// `grabbing` are a CSS invention that X11's cursor font, Win32's `IDC_*`
    /// set and `SDL_SystemCursor` all lack. Without a picture they fall back to
    /// `move`, which says "this can be dragged" less precisely rather than
    /// saying nothing.
    static SdlSystemCursor systemShape(Cursor cursor) {
        return switch (cursor) {
            case DEFAULT -> SdlSystemCursor.DEFAULT;
            case POINTER -> SdlSystemCursor.POINTER;
            case TEXT -> SdlSystemCursor.TEXT;
            case MOVE, GRAB, GRABBING -> SdlSystemCursor.MOVE;
            case WAIT -> SdlSystemCursor.WAIT;
            case PROGRESS -> SdlSystemCursor.PROGRESS;
            case CROSSHAIR -> SdlSystemCursor.CROSSHAIR;
            case NOT_ALLOWED -> SdlSystemCursor.NOT_ALLOWED;
            case EW_RESIZE -> SdlSystemCursor.EW_RESIZE;
            case NS_RESIZE -> SdlSystemCursor.NS_RESIZE;
            case NESW_RESIZE -> SdlSystemCursor.NESW_RESIZE;
            case NWSE_RESIZE -> SdlSystemCursor.NWSE_RESIZE;
        };
    }

    /// The picture cursor shown, or null when it is a platform shape or the
    /// cursors are not made yet: what a test reads.
    @Nullable
    PictureKey shownPicture() {
        return cursors != null && cursors.currentPicture() instanceof PictureKey key ? key : null;
    }

    @Override
    public void close() {
        if (cursors != null) {
            cursors.close();
            cursors = null;
        }
    }

    private @Nullable SdlCursors cursors() {
        if (unavailable) {
            return null;
        }
        if (cursors == null) {
            try {
                cursors = new SdlCursors();
            } catch (UnsatisfiedLinkError e) {
                unavailable = true;
                LOG.debug("libgoldberry exports no cursor calls; the pointer keeps its default shape", e);
                return null;
            }
        }
        return cursors;
    }

    private static SdlColorCursor toSdl(List<CursorPicture> sizes) {
        var images = sizes.stream()
                .map(size -> new SdlIconImage(
                        size.image().pixels(),
                        size.image().size().width(),
                        size.image().size().height()))
                .toList();
        var base = sizes.getFirst();
        return new SdlColorCursor(images, base.hotX(), base.hotY());
    }

    /// One picture cursor SDL made: a shape at the width of the picture it was
    /// made from first.
    record PictureKey(Cursor shape, int width) {}
}
