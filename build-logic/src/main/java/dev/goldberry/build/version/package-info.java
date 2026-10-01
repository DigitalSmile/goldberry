/**
 * Goldberry's calendar versions and the version a build stamps (ADR-0333).
 *
 * <p>A version is a year and a release of that year, with an optional patch. Whether a
 * build is a snapshot or a release is decided from {@code gradle.properties}, the release
 * flag and the tag; after a tag, {@code gradle.properties} moves to the next release
 * line.
 */
package dev.goldberry.build.version;
