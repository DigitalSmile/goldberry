package io.github.digitalsmile.goldberry.widgets;

import java.util.Set;

import io.github.digitalsmile.goldberry.widget.attr.Attributes;

/// The three names a widget carries, from the one name a test cares about.
///
/// ## Why a test wants this at all
///
/// [Attributes] holds an `id`, a set of classes and the reconciler's key, and a
/// test that builds a widget in Java has to supply all three. What it *means* is
/// almost always "call it this, and let the reconciler pair it by the same
/// word" — so the third argument repeats the first, every time, and a test that
/// mistypes one of the two gets a widget the stylesheet matches and the
/// reconciler replaces on every build, which is a bug no assertion here is
/// looking for.
///
/// Thirty widget tests wrote that three-argument constructor out for themselves,
/// in two shapes that differed only in whether they took classes. One shape
/// covers both: `Set.of()` of nothing is the same set the shorter form passed,
/// so `id("plot")` and `id("chip", "selected")` are the same call.
///
/// ## Why it is `public` in the root package
///
/// The tests that want it are under `controls`, `panel`, `data`, `form`, `nav`,
/// `overlay`, `menu` and `core`, so package-private would reach none of them.
/// This is a test source and nothing ships it — the same argument [TestHost] and
/// [CatalogMarkup] make from the same package.
public final class TestAttributes {

    private TestAttributes() {}

    /// A widget named `id`, keyed by the same word, in `classes`.
    ///
    /// The key is the id on purpose: the reconciler pairs two builds by key, and
    /// a test that rebuilds a tree wants the second build's widget matched to the
    /// first's. Anything that wants a key of its own is not this call.
    ///
    /// @param id      the `id`, which is also the key
    /// @param classes the CSS classes, if any
    public static Attributes id(String id, String... classes) {
        return new Attributes(id, Set.of(classes), id);
    }
}
