/**
 * Everything Goldberry publishes to Maven Central, written down once: the
 * libraries, the BOM and the umbrella artifact ({@code docs/ARCHITECTURE.md} §15,
 * ADR-0334, ADR-0336).
 *
 * <p>The BOM's constraints and the umbrella's dependencies are generated from this
 * list, and applying {@code goldberry.publish} to a project that is not on it fails the
 * build.
 *
 * <p>FFmpeg rides {@code goldberry-media} as classifier jars, and LGPL-2.1 has its
 * object code published only beside its complete source: {@link UpstreamSource}
 * takes that source from git at the pinned tags, and {@link CorrespondingSource}
 * refuses a publication without it (ADR-0508).
 */
package dev.goldberry.build.publish;
