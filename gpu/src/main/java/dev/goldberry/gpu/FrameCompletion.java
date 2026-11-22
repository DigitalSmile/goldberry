package dev.goldberry.gpu;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.sdl.SdlException;

/// When a frame has run on the GPU, for whatever recorded into it a copy that
/// reads memory the CPU writes again later: a raster texture painted in place.
/// One per frame, made the first time it is asked for, and one holder of the
/// frame's fence, as each readback is.
///
/// [#awaitIfSubmitted] is what the next paint calls. Before the frame is
/// submitted the GPU has not begun reading, so it answers at once; once the
/// frame is submitted it waits for the fence and lets go of it.
final class FrameCompletion {

    private enum State {
        RECORDING,
        SUBMITTED,
        DONE
    }

    private State state = State.RECORDING;
    private @Nullable SharedFence fence;

    /// Its frame was submitted, and `submitted` signals when it has run.
    void submitted(SharedFence submitted) {
        fence = submitted;
        state = State.SUBMITTED;
    }

    /// Its frame was discarded: nothing it recorded will run.
    void discarded() {
        if (state == State.RECORDING) {
            state = State.DONE;
        }
    }

    /// Waits for the frame to have run, and answers true; answers false at
    /// once while the frame is still recording; true at once when it has run
    /// or was discarded.
    ///
    /// @throws GpuException when the wait fails: a lost device
    boolean awaitIfSubmitted() {
        return switch (state) {
            case RECORDING -> false;
            case DONE -> true;
            case SUBMITTED -> {
                var current = fence;
                fence = null;
                state = State.DONE;
                if (current != null) {
                    try {
                        current.await();
                    } catch (SdlException e) {
                        throw GpuException.of(e);
                    } finally {
                        current.release();
                    }
                }
                yield true;
            }
        };
    }

    /// Whether the frame is still recording, for the tests.
    boolean isRecording() {
        return state == State.RECORDING;
    }
}
