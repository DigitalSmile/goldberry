/**
 * The native toolchain a build script runs: how a tool is found, by override or by
 * search, and what a Windows machine needs before {@code :natives:cmakeBuild} may
 * run -- MSVC, and the environment that makes {@code cl} callable.
 *
 * <p>Read more: <a href="https://goldberry.dev/docs/contributing/building.html#windows">Windows</a>.
 */
package dev.goldberry.build.toolchain;
