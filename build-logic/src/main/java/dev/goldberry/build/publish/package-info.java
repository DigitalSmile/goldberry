/**
 * Everything Goldberry publishes to Maven Central, written down once: the
 * libraries, the BOM and the umbrella artifact.
 *
 * <p>The BOM's constraints and the umbrella's dependencies are generated from this
 * list, and applying {@code goldberry.publish} to a project that is not on it fails the
 * build.
 *
 * <p>{@code goldberry-media}'s natives ride it as {@code natives-<target>}
 * classifier jars; every upstream in them is BSD or CC0, so nothing more than
 * the notice travels with them.
 *
 * <p>Read more: <a href="https://goldberry.dev/docs/contributing/releasing.html#where-things-go">Where
 * things go</a>.
 */
package dev.goldberry.build.publish;
