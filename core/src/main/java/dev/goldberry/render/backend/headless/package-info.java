/// The backend with no platform under it: windows, popups, a tray, file dialogs and
/// a clipboard that exist only as state.
///
/// It is what golden-image tests render against, identically on every OS, and it
/// needs no native library at all, so everything above the backend SPI is testable
/// without one. Every rule the SPI states — UI-thread confinement, frame
/// coalescing, damage bounds — is enforced here rather than assumed, so a real
/// backend that breaks one fails the same tests. Its doubles keep what was
/// presented and let a test script what the user does.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Architecture](https://goldberry.dev/docs/overview/architecture.html#the-backend-spi).
@NullMarked
package dev.goldberry.render.backend.headless;

import org.jspecify.annotations.NullMarked;
