/// Goldberry native layer: jextract-generated FFM bindings for Blend2D, Yoga,
/// HarfBuzz, SDL3, and libxkbcommon, plus the thin owning wrappers around them.
///
/// This module exports nothing yet, and that is the point. Per
/// `docs/ARCHITECTURE.md` §3.1, raw `MemorySegment` must never escape this
/// module; the module graph -- not a naming convention -- is what enforces it.
/// When M0 lands, the exports added here will be the wrapper packages only, and
/// the generated binding packages will stay unexported.
///
/// This is also the module named in `--enable-native-access` (JEP 472): Java 25
/// warns on restricted native access from the unnamed module, and a later
/// release makes it an error.
module io.github.digitalsmile.goldberry.natives {
}
