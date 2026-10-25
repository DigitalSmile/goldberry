package dev.goldberry.media.view.gpu;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

import dev.goldberry.gpu.video.PlaneStaging;
import dev.goldberry.gpu.video.StagedPlanes;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.picture.PlaneBuffer;
import dev.goldberry.media.picture.PlaneStore;

/// A [PlaneStore] over a video layer's upload memory ([PlaneStaging]): what
/// GPU present offers its player, so that each picture's planes are copied
/// once, where they are decoded, and uploaded from there.
///
/// A picture with alpha is never staged: the layer draws opaque planes, and
/// such a picture is converted on the CPU anyway.
///
/// Public for [Pictures]'s reason: this package is not exported.
public final class StagedPlaneStore implements PlaneStore {

    private final PlaneStaging staging;

    /// A store over `staging`.
    public StagedPlaneStore(PlaneStaging staging) {
        this.staging = Objects.requireNonNull(staging, "staging");
    }

    @Override
    public Optional<PlaneBuffer> obtain(PixelFormat format, int width, int height) {
        if (format.hasAlpha()) {
            return Optional.empty();
        }
        return staging.obtain(Pictures.layout(format), width, height).map(planes -> new Staged(format, planes));
    }

    /// One picture's staged planes, as the player's SPI sees them.
    ///
    /// @param format the frame contract's layout
    /// @param planes the layer's staged planes
    record Staged(PixelFormat format, StagedPlanes planes) implements PlaneBuffer {

        @Override
        public int width() {
            return planes.width();
        }

        @Override
        public int height() {
            return planes.height();
        }

        @Override
        public int stride(int index) {
            return planes.stride(index);
        }

        @Override
        public ByteBuffer plane(int index) {
            return planes.plane(index);
        }

        @Override
        public void release() {
            planes.release();
        }
    }

    @Override
    public String toString() {
        return "StagedPlaneStore[" + staging + "]";
    }
}
