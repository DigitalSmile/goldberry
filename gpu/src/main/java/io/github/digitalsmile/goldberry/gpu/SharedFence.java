package io.github.digitalsmile.goldberry.gpu;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuFence;

/// A submitted frame's fence, shared by its readbacks and released when the
/// last of them no longer needs it.
final class SharedFence {

    private final SdlGpuFence fence;
    private int holders;
    private boolean signalled;

    SharedFence(SdlGpuFence fence, int holders) {
        this.fence = fence;
        this.holders = holders;
    }

    /// Blocks until the GPU has finished the frame. Waits once; later calls
    /// return at once.
    ///
    /// @throws io.github.digitalsmile.goldberry.natives.sdl.SdlException when
    ///         the wait fails: a lost device
    void await() {
        if (!signalled) {
            fence.await();
            signalled = true;
        }
    }

    /// One holder is done with it; the last releases it.
    void release() {
        if (holders > 0 && --holders == 0) {
            fence.close();
        }
    }
}
