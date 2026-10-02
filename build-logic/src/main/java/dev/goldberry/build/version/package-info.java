/**
 * Goldberry's calendar versions and the version a build stamps.
 *
 * <p>A version is a year and a release of that year, with an optional patch. Whether a
 * build is a snapshot or a release is decided from {@code gradle.properties}, the release
 * flag and the tag; after a tag, {@code gradle.properties} moves to the next release
 * line.
 *
 * <p>Read more: <a href="https://goldberry.dev/docs/contributing/releasing.html#versions">Versions</a>.
 */
package dev.goldberry.build.version;
