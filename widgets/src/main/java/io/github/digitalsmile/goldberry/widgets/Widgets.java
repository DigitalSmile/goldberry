package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.kdl.KdlInflater;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ServiceLoader;
import io.github.digitalsmile.goldberry.bind.ActionRegistry;
import io.github.digitalsmile.goldberry.bind.BindingRegistry;

/// Turns markup into widgets, with every widget module on the path already in it.
///
/// ```java
/// var inflater = Widgets.inflater(icons, model);
/// ```
///
/// That is the whole wiring. `model` is a
/// [io.github.digitalsmile.goldberry.bind.Model] and its `@Bind` paths and
/// `@Action` names are read off it; the node names come from every
/// [WidgetCatalog] the build generated, in this module and in any other
/// ([ADR-0131](../../../../../../book/src/adr/0131-a-widget-package-announces-itself.md)).
///
/// ## Why this and not `Controls.inflater`
///
/// `Controls.inflater(actions, icons, bindings)` asked the caller for three
/// registries it could work out for itself, and for a catalog naming exactly the
/// widgets `:widgets` happens to ship. Neither survives contact with a second
/// widget module: the application would have to merge two catalogs by hand and
/// keep the merge in step with both.
public final class Widgets {

    private Widgets() {
    }

    /// An inflater for `models`, with icons.
    ///
    /// @param icons  what an `icon="plus"` attribute resolves against — the one
    ///               registry that cannot be derived, because an `Icon` owns
    ///               native memory and the application decides what it opens
    /// @param models the `@Model` objects whose paths and actions this document
    ///               may name. More than one because a window's own actions —
    ///               "open the menu", "toggle the HUD" — belong to the window and
    ///               not to the view model
    public static KdlInflater<Widget> inflater(Icons icons, Object... models) {
        return inflater(Wiring.of(icons, models));
    }

    /// The same, with the objects a document may name — a `FormController`, a
    /// `Validator`.
    ///
    /// A fourth registry rather than a fourth kind of annotation, for the reason
    /// [Named] gives: a controller is not a method, not a resource, and not a
    /// value that changes, and the registry that *told* it was not was the one
    /// already written.
    public static KdlInflater<Widget> inflater(Named named, Icons icons, Object... models) {
        return inflater(Wiring.of(icons, models).with(named));
    }

    /// An inflater for `models`, with no icons.
    public static KdlInflater<Widget> inflater(Object... models) {
        return inflater(Icons.none(), models);
    }

    /// An inflater with nothing bound: every `press=`, `bind=` and `icon=`
    /// resolves to nothing.
    ///
    /// What a golden image or a layout preview wants — the markup is about what
    /// the window looks like, and a preview that had to supply a handler for
    /// every button in it would be unusable.
    public static KdlInflater<Widget> inflater() {
        return inflater(Wiring.none());
    }

    /// An inflater with all three registries supplied by hand.
    ///
    /// Present as an exact overload rather than left to `inflater(Object...)`,
    /// because without it a call passing three registries binds to the varargs
    /// form, is read as three *models*, and fails at run time instead of at the
    /// call. An overload that compiles and means something else is worse than no
    /// overload.
    public static KdlInflater<Widget> inflater(
            io.github.digitalsmile.goldberry.bind.ActionRegistry actions, Icons icons,
            io.github.digitalsmile.goldberry.bind.BindingRegistry bindings) {
        return inflater(new Wiring(actions, icons, bindings));
    }

    /// An inflater with actions and icons bound, and no bindings.
    ///
    /// The explicit door a test uses: registries assembled by hand rather than
    /// read off a model.
    public static KdlInflater<Widget> inflater(
            io.github.digitalsmile.goldberry.bind.ActionRegistry actions, Icons icons) {
        return inflater(new Wiring(actions, icons,
                io.github.digitalsmile.goldberry.bind.BindingRegistry.none()));
    }

    /// An inflater with actions bound and no icons.
    public static KdlInflater<Widget> inflater(
            io.github.digitalsmile.goldberry.bind.ActionRegistry actions) {
        return inflater(actions, Icons.none());
    }

    /// An inflater against a [Wiring] the caller assembled.
    ///
    /// The explicit door, for a test that wants a lenient registry or an
    /// application that builds its registries some other way.
    public static KdlInflater<Widget> inflater(Wiring wiring) {
        var inflater = new KdlInflater<Widget>();
        var catalog = new Inflatable.Catalog(inflater, wiring);
        for (var module : catalogs()) {
            module.register(catalog);
        }
        return inflater;
    }

    /// Every catalog on the module path, in the order the service loader found
    /// them.
    ///
    /// `ServiceLoader`, which GraalVM resolves when it **builds the image** — so
    /// this is discovery with no runtime scan, and the closed world already knows
    /// every provider (ADR-0127). On the class path the same lookup reads the
    /// `META-INF/services` entry the build wrote; on the module path it reads the
    /// `provides` the build patched into `module-info`.
    ///
    /// Loaded through this class's own loader rather than the context class
    /// loader: a toolkit that read the thread's loader would find a different set
    /// of widgets depending on which thread inflated the document.
    public static Iterable<WidgetCatalog> catalogs() {
        return ServiceLoader.load(WidgetCatalog.class, Widgets.class.getClassLoader());
    }
}
