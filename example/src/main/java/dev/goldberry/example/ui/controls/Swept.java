package dev.goldberry.example.ui.controls;

import dev.goldberry.bind.runtime.Models;

/// An action a Java card calls directly, followed by a sweep of the model it
/// changed.
///
/// An action reached through a document's registry is swept for free, and a
/// woven model notifies on the write itself. A method reference called straight
/// from Java on an unwoven model is neither, so its change would wait for the
/// next frame's sweep, or for none where nothing asks for frames. One call to
/// [Models#refresh] says it now.
///
/// Read more: [The sweep](https://goldberry.dev/docs/weaving.html#the-sweep-and-the-one-line-it-sometimes-costs).
final class Swept {

    private Swept() {}

    /// `action`, then a sweep of `model`.
    static Runnable after(Object model, Runnable action) {
        return () -> {
            action.run();
            Models.refresh(model);
        };
    }
}
