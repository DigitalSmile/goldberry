package dev.goldberry.css.conformance;

/// What the engine does with one row of the conformance table.
///
/// The words are the support analysis's, so a row reads the same in the table
/// as in the document it came from.
enum Outcome {

    /// A declaration the engine reads and acts on.
    APPLIED(Kind.DECLARATION, true),

    /// A declaration the engine reads, but which comes out exactly as its
    /// reference does: `cubic-bezier(…)` running as `ease-enter`, say. The part
    /// that differs was skipped.
    IGNORED(Kind.DECLARATION, false),

    /// A declaration of a property the engine has, with a value it will not
    /// read. The rest of the rule applies.
    DROPPED(Kind.DECLARATION, false),

    /// A declaration of a property the engine does not have at all.
    UNKNOWN(Kind.DECLARATION, false),

    /// A sheet that parses, strict and lenient, with nothing dropped.
    PARSED(Kind.SHEET, true),

    /// A sheet that asks for something outside the subset: a lenient parse drops
    /// the rule, a strict one refuses the sheet as unsupported.
    REFUSED(Kind.SHEET, false),

    /// A sheet that neither mode can read. Lenient refuses it whole.
    MALFORMED(Kind.SHEET, false);

    private final Kind kind;
    private final boolean supported;

    Outcome(Kind kind, boolean supported) {
        this.kind = kind;
        this.supported = supported;
    }

    /// Whether a row of `kind` can come out this way.
    ///
    /// `MALFORMED` is the one outcome both kinds share: a declaration that does
    /// not parse takes its probe sheet down with it.
    boolean fits(Kind kind) {
        return this == MALFORMED || this.kind == kind;
    }

    /// Whether this is the outcome of a feature the subset has, which is what a
    /// row with no plan item must come out as.
    boolean supported() {
        return supported;
    }

    /// What a row probes: one declaration, or a whole sheet.
    enum Kind {
        DECLARATION,
        SHEET
    }
}
