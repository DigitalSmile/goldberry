package io.github.digitalsmile.goldberry.bind;

/// What the weaver makes a [Model] class implement.
///
/// Four methods, none of which appear in the author's source: the model is
/// written as plain Java with plain fields, and this interface is added to its
/// bytecode by the build step that rewires those fields
/// ([ADR-0125](../../../../../../book/src/adr/0125-a-raw-field-is-woven-into-a-binding.md)).
///
/// ## It is not what an application calls
///
/// Because the interface arrives *after* javac, the author's own code cannot see
/// it — `model.bindings()` does not compile against a class whose source says
/// nothing of the sort. [Models] is the front door, and it exists precisely to
/// turn that into a cast that either works or explains itself.
///
/// An application therefore writes `Models.bindings(model)` and never names this
/// type. It is public because woven bytecode in another module has to implement
/// it, not because it is an API.
public interface BoundModel {

    /// The current value of the woven field in `slot`, boxed.
    ///
    /// The weaver writes a `switch` over the slots it assigned, so this is a
    /// tableswitch and a `getfield` — the boxing is the only cost, and it is paid
    /// only when something actually reads through the binding rather than on
    /// every write.
    Object boundValue(int slot);

    /// This model's listener store, one slot per woven field.
    FieldListeners boundListeners();

    /// Every `@Bind` path on this model, strict.
    ///
    /// Built fresh on each call, which is what makes a document reloadable: the
    /// new tree resolves its paths against a registry pointing at the same
    /// fields, so the values survive the reload (ADR-0051).
    BindingRegistry bindings();

    /// Every `@Action` name on this model, strict.
    ActionRegistry actions();
}
