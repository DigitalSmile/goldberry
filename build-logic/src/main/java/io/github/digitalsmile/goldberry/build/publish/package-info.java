/**
 * Everything Goldberry publishes to Maven Central, written down once: the
 * libraries, the BOM and the umbrella artifact ({@code docs/ARCHITECTURE.md} §15,
 * ADR-0334, ADR-0336).
 *
 * <p>The BOM's constraints and the umbrella's dependencies are generated from this
 * list, and applying {@code goldberry.publish} to a project that is not on it fails the
 * build.
 */
package io.github.digitalsmile.goldberry.build.publish;
