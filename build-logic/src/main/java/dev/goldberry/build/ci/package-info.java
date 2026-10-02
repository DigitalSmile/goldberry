/**
 * Making a CI failure readable from outside: failed tests and failed builds written
 * as GitHub Actions error annotations, which the public API serves to anyone
 * without signing in.
 *
 * <p>Active only on a runner; locally the console already shows the failure.
 *
 * <p>Read more: <a href="https://goldberry.dev/docs/contributing/testing.html#the-ci-matrix">The CI matrix</a>.
 */
package dev.goldberry.build.ci;
