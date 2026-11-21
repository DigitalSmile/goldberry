/// Goldberry's microphone module: recording through SDL, which `libgoldberry`
/// already links, the levels, spectra and outlines drawn from it, and the
/// widgets that show them.
///
/// `dev.goldberry.mic.backend` is not exported: SDL's vocabulary stops there.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
module dev.goldberry.mic {

    requires dev.goldberry.common;

    requires dev.goldberry.natives;

    /// The widgets `level-meter`, `waveform-view` and `spectrum-view` are.
    /// `transitive` because a [dev.goldberry.mic.view.LevelMeter] is a widget.
    requires transitive dev.goldberry.widgets;

    requires transitive static org.jspecify;

    exports dev.goldberry.mic;

    exports dev.goldberry.mic.source;

    exports dev.goldberry.mic.analysis;

    /// The widgets: `level-meter`, `waveform-view`, `spectrum-view`, and the
    /// stylesheet that gives them their size and colours.
    exports dev.goldberry.mic.view;

    /// What the widgets draw, for an application that draws a reading it holds
    /// itself: a recorded clip's outline, say.
    exports dev.goldberry.mic.view.draw;

    /// Opened to `:core`, which reads `mic.css` out of this package.
    opens dev.goldberry.mic.view to
            dev.goldberry.core;

    /// The catalog the weaver generates. The `provides` line is patched into the
    /// compiled descriptor by the build.
    uses dev.goldberry.widgets.markup.WidgetCatalog;
}
