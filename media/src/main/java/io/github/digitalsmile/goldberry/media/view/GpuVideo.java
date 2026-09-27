package io.github.digitalsmile.goldberry.media.view;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;

/// Whether `:gpu` is here to show video with, and the [VideoPresenter] that
/// does (`docs/gpu-plan.md`, phase 6; ADR-0484).
///
/// This module `requires static` `:gpu`: a video plays without it, drawn on the
/// CPU, and an application that ships no GPU module ships no GPU code. So `:gpu`
/// is looked for once, by a class of its that this module reads, before any
/// class that uses it is loaded. [GpuVideoPresenter] is the only class here that
/// names `:gpu`'s types, and it is loaded only through [#presenter], only when
/// [#available()] said yes.
final class GpuVideo {

    private static final Logger LOG = Logs.of(GpuVideo.class);

    /// A class of `:gpu`'s video package, which this module reads when `:gpu`
    /// was resolved into the application's module graph.
    private static final String PROBE = "io.github.digitalsmile.goldberry.gpu.video.VideoLayer";

    private static final boolean AVAILABLE = probe();

    private GpuVideo() {}

    /// Whether `:gpu` is here, and this module reads it.
    static boolean available() {
        return AVAILABLE;
    }

    /// A presenter that calls `shownOnGpu` with whether each paint placed the
    /// picture on the GPU, when that changes; or null without `:gpu`.
    static @Nullable VideoPresenter presenter(Consumer<Boolean> shownOnGpu) {
        return AVAILABLE ? new GpuVideoPresenter(shownOnGpu) : null;
    }

    private static boolean probe() {
        try {
            var type = Class.forName(PROBE, false, GpuVideo.class.getClassLoader());
            var readable = GpuVideo.class.getModule().canRead(type.getModule());
            if (!readable) {
                LOG.debug(
                        "{} is present and not read by {}; video is drawn on the CPU",
                        PROBE,
                        GpuVideo.class.getModule());
            }
            return readable;
        } catch (ClassNotFoundException | LinkageError e) {
            LOG.debug("no GPU module; video is drawn on the CPU");
            return false;
        }
    }
}
