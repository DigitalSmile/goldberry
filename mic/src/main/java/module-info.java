/// Goldberry's microphone module: recording through SDL, which `libgoldberry`
/// already links, and the levels, spectra and outlines drawn from it.
///
/// `dev.goldberry.mic.backend` is not exported: SDL's vocabulary stops there.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
module dev.goldberry.mic {

    requires dev.goldberry.common;

    requires dev.goldberry.natives;

    requires transitive static org.jspecify;

    exports dev.goldberry.mic;

    exports dev.goldberry.mic.source;

    exports dev.goldberry.mic.analysis;
}
