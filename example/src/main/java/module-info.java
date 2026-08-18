/// The Goldberry showcase.
///
/// A module, like everything else here: an application consuming Goldberry on
/// the module path is the case `--enable-native-access=<module>` is designed for
/// (ADR-0007), and it only works if the toolkit's own descriptors are right.
/// Building this on the classpath instead would leave that untested.
module io.github.digitalsmile.goldberry.example {
    requires io.github.digitalsmile.goldberry.core;
    requires io.github.digitalsmile.goldberry.widgets;
    requires org.slf4j;

    /// So the toolkit can read `showcase.css` and `badges.kdl`.
    ///
    /// JPMS encapsulates **resources** as well as classes: a file inside a
    /// package of a named module is invisible to other modules unless the package
    /// is open, and `exports` is not enough — it governs types, not bytes. So an
    /// application that keeps its stylesheet and its markup beside its code opens
    /// the package to whoever loads them, which is exactly one module
    /// (ADR-0093).
    ///
    /// Qualified rather than a bare `opens`, because the toolkit is the only
    /// thing that needs to read these and an unqualified open would hand the
    /// package's private types to every module on the path as well.
    opens io.github.digitalsmile.goldberry.example to io.github.digitalsmile.goldberry.core;

    /// And the same for the panes' documents — one `opens` per package that
    /// keeps a resource, which is the granularity JPMS works at.
    opens io.github.digitalsmile.goldberry.example.ui to io.github.digitalsmile.goldberry.core;
}
