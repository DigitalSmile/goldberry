/// The Goldberry showcase.
///
/// A module, like everything else here: an application consuming Goldberry on
/// the module path is the case `--enable-native-access=<module>` is designed for
/// (ADR-0007), and it only works if the toolkit's own descriptors are right.
/// Building this on the classpath instead would leave that untested.
module io.github.digitalsmile.goldberry.example {
    requires io.github.digitalsmile.goldberry.core;

    /// JSpecify's nullness annotations, for the packages under NullAway.
    ///
    /// `static`, because they are compile-time only. `transitive`, because
    /// `@Nullable` appears on exported signatures and a consumer compiling
    /// against one has to read it (`docs/testing.md` §2).
    requires transitive static org.jspecify;
    requires io.github.digitalsmile.goldberry.widgets;

    /// The first content module an application opts into (ADR-0190), and **both** of
    /// its widgets. The Panels and Markdown screens name `markdown-view` in KDL and
    /// the HTML screen names `html-view`; this line is what puts either node on the
    /// path, and `Showcase.stylesheets()` adds the two stylesheets beside the
    /// toolkit's own (ADR-0298).
    ///
    /// Worth noticing what is **not** here: nothing in this module names a
    /// `MarkdownEvent` or an `MD_BLOCKTYPE`, because `:natives` exports md4c to
    /// `:html` and to nobody else (ADR-0294). An application gets a document and a
    /// widget, and the parser is somebody else's business.
    requires io.github.digitalsmile.goldberry.html;

    /// The emoji face, which is an artifact an application opts into rather than
    /// something `:core` carries: OpenMoji is CC BY-SA and wants attribution
    /// where the work is seen, so adding this line is also taking that on — the
    /// Emoji screen carries the credit, which is what an About box would do
    /// (ADR-0384, ADR-0386).
    ///
    /// Nothing in this module names a type of it. The face arrives through a
    /// service `:core` looks up, so what this line buys is a provider on the
    /// module path.
    requires io.github.digitalsmile.goldberry.emoji;
    requires org.slf4j;

    /// So the toolkit can read `showcase.css` and the seven KDL documents.
    ///
    /// JPMS encapsulates **resources** as well as classes: a file inside a
    /// package of a named module is invisible to other modules unless the package
    /// is open, and `exports` is not enough — it governs types, not bytes. So an
    /// application that keeps its stylesheet and its markup beside its code opens
    /// the package to whoever loads them
    /// (ADR-0093).
    ///
    /// **"Whoever loads them" is more than one module**, which this file used to
    /// say was exactly one. A stylesheet is read by `:core`; an `image` is read
    /// by `:widgets`, from its own module. A package opened only to `:core`
    /// therefore holds a picture nothing can see, and the failure reads as a
    /// missing file rather than as an encapsulated one ([ADR-0395]).
    ///
    /// Qualified rather than a bare `opens`, because the toolkit is the only
    /// thing that needs to read these and an unqualified open would hand the
    /// package's private types to every module on the path as well.
    ///
    /// It is also what lets this module's `@Model` and `@Actions` classes be bound
    /// **at run time**, which is what an ordinary jar does: the reflective binder
    /// needs private access to the fields, and JPMS is what grants it. A woven
    /// module needs neither -- the weaver works from inside the class -- so this
    /// line is the cost of not having to run a build step
    /// (ADR-0155).
    opens io.github.digitalsmile.goldberry.example to io.github.digitalsmile.goldberry.core;

    /// And the same for the panes' documents and the Canvas screen's sample
    /// image — one `opens` per package that keeps a resource, which is the
    /// granularity JPMS works at.
    ///
    /// Two modules here rather than one: `canvas-sample.jpg` sits beside these
    /// classes and is read by an `image` widget, which is `:widgets`' code
    /// ([ADR-0395]).
    opens io.github.digitalsmile.goldberry.example.ui to
            io.github.digitalsmile.goldberry.core,
            io.github.digitalsmile.goldberry.widgets;
}
