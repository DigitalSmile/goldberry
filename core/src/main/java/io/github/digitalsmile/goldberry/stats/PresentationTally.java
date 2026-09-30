package io.github.digitalsmile.goldberry.stats;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedSet;

import io.github.digitalsmile.goldberry.render.window.Presentation;

/// How many of a window's frames went through the GPU and how many on the CPU,
/// for the line the launcher logs at exit (ADR-0492).
///
/// Every presented frame, including one with nothing new to upload, because the
/// question is where the window was, not what the GPU was asked to do: a still
/// window on the GPU is on the GPU. [PresentSummary] is the other half, the cost
/// of the presents that carried new pixels.
///
/// Confined to the UI thread, like the frame loop that feeds it.
public final class PresentationTally {

    private long gpuFrames;
    private long cpuFrames;
    private final SequencedSet<String> drivers = new LinkedHashSet<>();

    /// An empty tally. A window keeps one; a test can make its own.
    public PresentationTally() {}

    /// Counts one presented frame.
    public void count(Presentation presentation) {
        Objects.requireNonNull(presentation, "presentation");
        switch (presentation) {
            case Presentation.Gpu(var driver) -> {
                gpuFrames++;
                drivers.add(driver);
            }
            case Presentation.Cpu _ -> cpuFrames++;
        }
    }

    /// Frames presented through the GPU.
    public long gpuFrames() {
        return gpuFrames;
    }

    /// Frames presented on the CPU.
    public long cpuFrames() {
        return cpuFrames;
    }

    /// `400 frame(s) through the GPU (vulkan), 0 on the CPU`.
    public String describe() {
        var through = drivers.isEmpty() ? "" : " (" + String.join(", ", drivers) + ")";
        return gpuFrames + " frame(s) through the GPU" + through + ", " + cpuFrames + " on the CPU";
    }
}
