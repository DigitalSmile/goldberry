package io.github.digitalsmile.goldberry.weaver.models;

import io.github.digitalsmile.goldberry.bind.Action;
import io.github.digitalsmile.goldberry.bind.Bind;
import io.github.digitalsmile.goldberry.bind.Model;
import io.github.digitalsmile.goldberry.bind.Property;
import java.util.List;

/// The models the weaver refuses, one nested class per rule.
///
/// They all **compile**: that is the point. Nothing in javac knows what `@Bind`
/// means any more, so every rule the old annotation processor enforced has to be
/// enforced by the weaver instead — and a rule with no test is a rule that
/// quietly stopped applying.
public final class Refused {

    private Refused() {
    }

    @Model
    public static final class StaticField {
        @Bind("a.b") static int shared;

        @Action("a.act") void act() {
            shared++;
        }
    }

    @Model
    public static final class FinalField {
        @Bind("a.b") final int fixed = 1;

        @Action("a.act") void act() {
        }
    }

    @Model
    public static final class ArrayField {
        @Bind("a.b") int[] values = new int[2];

        @Action("a.act") void act() {
            values = new int[3];
        }
    }

    @Model
    public static final class BadPath {
        @Bind("not a path!") int value;

        @Action("a.act") void act() {
            value++;
        }
    }

    @Model
    public static final class DuplicatePath {
        @Bind("a.b") int one;
        @Bind("a.b") int two;

        @Action("a.act") void act() {
            one++;
            two++;
        }
    }

    @Model
    public static final class PathClashesWithAction {
        @Bind("a.b") int one;

        @Action("a.b") void act() {
            one++;
        }
    }

    @Model
    public static final class TwoArgumentAction {
        @Bind("a.b") int value;

        @Action("a.act") void act(String first, String second) {
            value = first.length() + second.length();
        }
    }

    @Model
    public static final class UnparseableArgument {
        @Bind("a.b") int value;

        @Action("a.act") void act(List<String> values) {
            value = values.size();
        }
    }

    @Model
    public static final class StaticAction {
        @Bind("a.b") int value;

        @Action("a.act") static void act() {
        }
    }

    @Model
    public static final class Empty {
        int value;
    }

    @Model
    public abstract static class Abstract {
        @Bind("a.b") int value;

        @Action("a.act") void act() {
            value++;
        }
    }

    /// A `Property` cannot ask for a restyle: the weaver rewires no writes to
    /// it, so there is nowhere to put the call.
    @Model
    public static final class RestylingProperty {
        @Bind(value = "a.b", restyle = true) final Property<String> held = Property.of("x");

        @Action("a.act") void act() {
            held.set("y");
        }
    }

    /// `@Actions` on a class that holds values — that is a `@Model`.
    @io.github.digitalsmile.goldberry.bind.Actions
    public static final class ActionsWithValues {
        @Bind("a.b") int value;

        @Action("a.act") void act() {
            value++;
        }
    }

    /// `@Actions` on a class with no actions.
    @io.github.digitalsmile.goldberry.bind.Actions
    public static final class NoActions {
        @SuppressWarnings("unused")
        private int value;

        void act() {
            value++;
        }
    }

    /// Both markers. A class holds values or it does not.
    @Model
    @io.github.digitalsmile.goldberry.bind.Actions
    public static final class BothMarkers {
        @Bind("a.b") int value;

        @Action("a.act") void act() {
            value++;
        }
    }

    /// A class in another package writing to a model's field — refused, because
    /// the synthesised setter is package-private and the call would not verify.
    @Model
    public static final class Reachable {
        @Bind("r.value") int value;

        @Action("r.act") void act() {
            value++;
        }
    }

    /// A model extending a model — refused, because each would get its own
    /// listener store and the subclass's would shadow the superclass's, so the
    /// inherited `@Bind` field would notify nobody. The one mistake here that
    /// would otherwise be silent.
    @Model
    public static class Base {
        @Bind("base.value") int value;

        @Action("base.act") void act() {
            value++;
        }
    }

    @Model
    public static final class Derived extends Base {
        @Bind("derived.value") int other;

        @Action("derived.act") void otherAct() {
            other++;
        }
    }

    /// Not annotated at all — the weaver has to leave it exactly as it is.
    public static final class NotAModel {
        @SuppressWarnings("unused")
        private int value;

        void act() {
            value++;
        }
    }

    /// A `Property` field is already observable, so `final` is right and the
    /// weaver binds it without rewiring anything.
    @Model
    public static final class PropertyOnly {
        @Bind("a.b") final Property<String> held = Property.of("x");

        @Action("a.act") void act() {
            held.set("y");
        }
    }
}
