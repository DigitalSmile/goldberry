package dev.goldberry.media.gpu;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.GpuFrame;
import dev.goldberry.gpu.GpuTexture;
import dev.goldberry.gpu.RenderTarget;
import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.gpu.TextureView;
import dev.goldberry.gpu.video.VideoImage;
import dev.goldberry.gpu.video.VideoLayer;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.picture.Picture;
import dev.goldberry.media.picture.PictureForm;
import dev.goldberry.media.picture.VideoPicture;
import dev.goldberry.media.picture.VideoPlanes;
import dev.goldberry.media.view.gpu.Pictures;
import dev.goldberry.render.model.PhysicalRect;

/// Draws a player's [Picture]s into a texture of the application's own, for a
/// shader of its own to sample: what `video-view` shows in a window, as a
/// texture instead.
///
/// The target is a whole [GpuTexture], or one level of one layer of one
/// ([TextureView]), in [TextureFormat#B8G8R8A8_UNORM] and made as a colour
/// target. The picture is stretched over the whole of it, so a renderer per
/// layer of one array texture gives a shader one texture to index:
///
/// ```java
/// // On the UI thread, in the frame that samples the slots.
/// player.shownPicture().ifPresent(picture -> renderers.get(slot).render(frame, picture, slots.layer(slot)));
/// ```
///
/// [VideoPlanes] are converted from Y'CbCr in a shader, with the picture's
/// matrix and range, and a [VideoPicture] is drawn as it is. A player hands out
/// planes while every view attached to it asks for them
/// ([MediaPlayer#attachView(PictureForm)]).
///
/// **Borrowing.** A picture lives under [Picture]'s rule: its bytes are the
/// player's, and stay put until two more pictures have been handed out after
/// it. A picture new to the renderer is uploaded inside [#render], copied out
/// before the call returns, so a picture rendered as soon as it is taken from
/// [MediaPlayer#shownPicture()] is safe, and nothing of it is read afterwards.
/// The same picture rendered again is not uploaded again: pictures are told
/// apart by identity, as the player hands them out.
///
/// What it makes on the device, textures for the planes and the pipelines that
/// convert them, is its own, kept between renders and made again on a new
/// device. Confined to the thread the frame records on, and closed when done.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html#video-view).
public final class PictureRenderer implements AutoCloseable {

    private final VideoLayer layer = new VideoLayer();
    private @Nullable Picture picture;
    private @Nullable VideoImage image;
    private boolean closed;

    /// A renderer that has made nothing yet: it makes what it needs on the
    /// first frame's device.
    public PictureRenderer() {}

    /// Draws the whole of `picture` over the whole of `target`, recording into
    /// `frame`.
    ///
    /// @throws IllegalArgumentException when `target` is not a
    ///                                  [TextureFormat#B8G8R8A8_UNORM] colour
    ///                                  target of `frame`'s device
    /// @throws IllegalStateException    when this renderer is closed, or a pass
    ///                                  of `frame` is open
    public void render(GpuFrame frame, Picture picture, RenderTarget target) {
        requireOpen();
        Objects.requireNonNull(picture, "picture");
        render(frame, picture, new PhysicalRect(0, 0, picture.width(), picture.height()), target);
    }

    /// Draws `source`, a part of `picture` in its pixels, stretched over the
    /// whole of `target`: a crop, as `object-fit: cover` makes one.
    ///
    /// @throws IllegalArgumentException when `source` is empty or reaches
    ///                                  outside the picture, or `target` is as
    ///                                  [#render(GpuFrame, Picture, RenderTarget)]
    ///                                  refuses
    /// @throws IllegalStateException    as that method
    public void render(GpuFrame frame, Picture picture, PhysicalRect source, RenderTarget target) {
        requireOpen();
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        layer.show(image(picture), source);
        layer.render(frame, target);
    }

    /// The layer's image of `shown`: made once for each new picture, so the
    /// layer uploads a picture once however often it is drawn.
    VideoImage image(Picture shown) {
        Objects.requireNonNull(shown, "picture");
        var made = image;
        if (shown != picture || made == null) {
            made = Pictures.image(shown);
            picture = shown;
            image = made;
        }
        return made;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("this picture renderer is closed");
        }
    }

    /// Releases what it made on the device and lets go of the last picture.
    /// Rendering afterwards throws. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        layer.close();
        picture = null;
        image = null;
    }

    @Override
    public String toString() {
        return "PictureRenderer[" + (closed ? "closed" : layer.toString()) + "]";
    }
}
