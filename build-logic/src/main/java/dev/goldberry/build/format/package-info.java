/**
 * A Spotless step that puts back the imports a formatter removed because their only
 * use was a {@code ///} Markdown doc comment, which neither of Spotless's unused-import
 * removers reads.
 */
package dev.goldberry.build.format;
