/**
 * Making a CI failure readable from outside: failed tests and failed builds written
 * as GitHub Actions error annotations, which the public API serves to anyone
 * without signing in (ADR-0338).
 *
 * <p>Active only on a runner; locally the console already shows the failure.
 */
package io.github.digitalsmile.goldberry.build.ci;
