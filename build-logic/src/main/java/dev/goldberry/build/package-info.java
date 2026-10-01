/**
 * Helpers the build scripts call around the native superbuild: finding {@code cmake},
 * {@code ninja} and {@code meson} by absolute path, and naming the Linux development packages
 * the superbuild needs to a package manager.
 *
 * <p>Build-time only, like the rest of {@code build-logic}. The packages beside this one
 * group the other helpers by the job they do.
 */
package dev.goldberry.build;
