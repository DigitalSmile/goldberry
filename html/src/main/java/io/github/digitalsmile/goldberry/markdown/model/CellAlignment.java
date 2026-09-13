package io.github.digitalsmile.goldberry.markdown.model;

/// How a table column is aligned — what the `:---:` row asked for.
///
/// The toolkit's own word for md4c's `MD_ALIGN`, so that nothing outside this
/// module names an md4c type (ADR-0294).
public enum CellAlignment {

    /// No colon on either side. The renderer decides, which for `markdown-view`
    /// means the reading direction's start edge.
    DEFAULT,

    START,

    CENTER,

    END
}
