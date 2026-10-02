/**
 * A Spotless step that puts back the imports a formatter removed because their only
 * use was a {@code ///} Markdown doc comment, which neither of Spotless's unused-import
 * removers reads.
 *
 * <p>Read more: <a href="https://goldberry.dev/docs/contributing/testing.html#static-analysis">Static
 * analysis</a>.
 */
package dev.goldberry.build.format;
