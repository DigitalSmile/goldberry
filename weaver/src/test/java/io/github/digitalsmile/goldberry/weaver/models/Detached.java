package io.github.digitalsmile.goldberry.weaver.models;

import io.github.digitalsmile.goldberry.bind.Bind;
import io.github.digitalsmile.goldberry.bind.Model;

/// A model whose actions are **not** its nestmates.
///
/// [Split] is the encapsulated arrangement — values and actions nested in one
/// class, so a private setter is enough (ADR-0137). This is the other one: two
/// top-level classes beside each other in a package, which is what an
/// application gets when the two grow big enough to want files of their own.
/// The setters the weaver gives this one have to open up to the package, and
/// *when* it decides that is the whole of the incremental story.
@Model
public final class Detached {

    @Bind("detached.count")
    int count;

    @Bind("detached.label")
    String label = "idle";

    public int count() {
        return count;
    }

    public String label() {
        return label;
    }
}
