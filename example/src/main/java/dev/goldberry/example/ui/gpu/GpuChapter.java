package dev.goldberry.example.ui.gpu;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.overlay.hud.Hud;

/// The **GPU** screen: `canvas3d`, drawn by the showcase's own renderer and
/// shaders, every frame and on demand, and the readings of what the window's
/// composite costs.
///
/// How the window shows the canvases is its launch's choice:
/// `-Pgoldberry.gpu.composite=never` reads them back, and `-Pgoldberry.gpu=off`
/// shows what a canvas shows without a GPU.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html).
///
/// @param context what the screen is built from
public record GpuChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("components/gpu");

    @Override
    public Widget build(BuildContext buildContext) {
        return Wall.of(
                "gpu",
                "GPU",
                "With goldberry-gpu on the module path a window presents through the GPU, and a canvas3d is a"
                        + " box an application's own renderer draws into. Where there is no GPU, the window"
                        + " presents on the CPU as before.",
                CHAPTER,
                List.of(new SpinningCube(), new TurnedCube(), frames(), measured()));
    }

    /// The window's present readings, which say whether it went through the GPU.
    private static Widget frames() {
        return new ShowcaseCard(
                        "gpu-frames-card",
                        "How the window presents",
                        "The UI is painted on the CPU, and goldberry-gpu changes the last step: the frame's damage"
                                + " is uploaded and drawn on the window's swapchain. These readings are that"
                                + " composite, and read dashes where the window presents on the CPU.",
                        DocLink.to("components/gpu", "what-the-module-does-to-a-window"))
                .of(new Hud(Hud.PRESENT, Attributes.NONE.id("gpu-hud")));
    }

    /// Where the GPU path has been measured, and where it has not.
    private static Widget measured() {
        return new ShowcaseCard(
                        "gpu-measured-card",
                        "What is measured, and what is not yet",
                        "Built and measured on Metal; on Linux the composited path has run under X11 on one"
                                + " machine, and Direct3D 12 waits for a host. A device lost mid-render shows black"
                                + " rather than falling back.",
                        DocLink.to("components/gpu", "what-is-measured-and-what-is-not-yet"))
                .reference();
    }
}
