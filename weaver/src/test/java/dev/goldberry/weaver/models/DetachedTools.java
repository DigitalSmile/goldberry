package dev.goldberry.weaver.models;

import dev.goldberry.bind.Action;
import dev.goldberry.bind.runtime.Actions;

/// The methods that change [Detached], in a file of their own.
///
/// A different nest and the same package, so every assignment here is a
/// `putfield` in a class file the model knows nothing about — and the model's
/// setters have to be reachable from it.
@Actions
public record DetachedTools(Detached values) {

    @Action("detached.bump")
    public void bump() {
        values.count++;
    }

    @Action("detached.say")
    public void say(String text) {
        values.label = text;
    }
}
