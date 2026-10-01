/// `:assets`' build-time half: fetching the pinned fonts and icon set, and
/// compiling SVG icons and glyph catalogs into the resources the runtime modules
/// package (ADR-0033).
///
/// A package of its own since ADR-0496. The build tool had shared its package
/// name with `:core`'s exported runtime `assets` package, which is also a
/// resource directory (ADR-0387). That was harmless only while the two never
/// met on one module path.
package dev.goldberry.assets.prepare;
