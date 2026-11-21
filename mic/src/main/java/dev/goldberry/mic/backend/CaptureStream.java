package dev.goldberry.mic.backend;

import java.nio.ByteBuffer;

/// One open recording: interleaved f32 at the format it was opened with.
public interface CaptureStream extends AutoCloseable {

    /// Copies whole frames recorded so far into `into`, from its position to
    /// its limit, and moves the position past them. Never waits.
    ///
    /// @return the frames read, zero when nothing is there yet
    /// @throws CaptureLostException when the device can no longer record
    int read(ByteBuffer into);

    /// Stops recording, keeping what was recorded.
    void pause();

    /// Starts or resumes recording.
    void resume();

    /// Closes the stream and its device.
    @Override
    void close();
}
