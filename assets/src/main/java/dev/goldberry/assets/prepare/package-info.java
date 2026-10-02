/// `:assets`' build-time half: fetching the pinned fonts and icon set, and
/// compiling SVG icons and glyph catalogs into the resources the runtime modules
/// package. Nothing it produces is committed: every asset is fetched by version,
/// checked against its checksum and compiled during the build.
///
/// A package of its own, apart from `:core`'s exported runtime `assets` package.
/// That package is also a resource directory, and a resource directory is a
/// package to the module system, so two modules sharing the name would fail to
/// load together on one module path.
///
/// Read more: [Building from source](https://goldberry.dev/docs/contributing/building.html).
package dev.goldberry.assets.prepare;
