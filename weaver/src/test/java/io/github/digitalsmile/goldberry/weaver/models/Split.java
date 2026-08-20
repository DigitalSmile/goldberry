package io.github.digitalsmile.goldberry.weaver.models;

import io.github.digitalsmile.goldberry.bind.Action;
import io.github.digitalsmile.goldberry.bind.Bind;
import io.github.digitalsmile.goldberry.bind.Model;

/// A model split the way an application is meant to split one: values in one
/// class, the methods that change them in another beside it.
///
/// The values are a **class**, because a record's components are final and a
/// bound field has to be assignable. The actions are a **record**, because they
/// hold one thing and hold it immutably (ADR-0136, ADR-0138).
///
/// Both are nested in this one, which is not decoration: they are then
/// **nestmates**, so `Actions.bump()` may call the private setter the weaver
/// synthesises on `Values` and nothing about the split has to open up
/// ([ADR-0137](../../../../../../book/src/adr/0137-a-model-keeps-its-fields.md)).
public final class Split {

    private Split() {
    }

    @Model
    public static final class Values {

        @Bind("split.count") private int count;
        @Bind("split.label") private String label = "idle";
        @Bind(value = "split.quiet", repaint = false) private int quiet;
    }

    @io.github.digitalsmile.goldberry.bind.Actions
    public record Actions(Values values) {

        @Action("split.bump")
        public void bump() {
            values.count++;
        }

        @Action("split.say")
        public void say(String text) {
            values.label = text;
        }

        @Action("split.tick")
        public void tick() {
            values.quiet++;
        }

        /// Two fields in one call, to prove the rewrite is per instruction and
        /// not per method.
        @Action("split.both")
        public void both() {
            values.count++;
            values.label = "moved";
        }
    }
}
