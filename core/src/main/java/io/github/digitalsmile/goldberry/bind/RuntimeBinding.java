package io.github.digitalsmile.goldberry.bind;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/// A [Model] bound by reflection, for a build that did not run the weaver.
///
/// The second of the two implementations of [BoundModel], and the one an ordinary
/// jar uses. The weaver rewrites the compiled class so that `gain++` notifies;
/// this reads the same annotations at run time and notices afterwards
/// ([ADR-0155](../../../../../../book/src/adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md)).
///
/// ## What it can do, and the one thing it cannot
///
/// Reading is exact. A [BoundField] over a [VarHandle] sees the field itself, so
/// `Models.observable(model, "app.gain").get()` is the current value at the
/// instant it is asked, exactly as the woven form is.
///
/// **A field write cannot be intercepted.** `putfield` is not virtual, which is
/// the whole reason the weaver exists (ADR-0125) — no proxy, no subclass and no
/// handle can see one. So the notification is not raised *by* the write; it is
/// raised by a **sweep** that compares each field against what it last held and
/// fires the listeners of the ones that moved.
///
/// The sweep is what everything below is shaped around, and it is why the fields
/// are specialised one class per primitive kind rather than read as `Object`:
/// it runs per press and per frame over every bound field of every bound model,
/// so a box allocated there and thrown away unread is the difference between 9 ns
/// a field and 31 (ADR-0155).
///
/// The sweep runs where a change is expected to have happened:
///
/// - after every `@Action` this registry hands out, which is the path every
///   button, slider and select in a document takes;
/// - at the top of each frame, for the models an
///   [io.github.digitalsmile.goldberry.Application] named — so a change made from
///   a timer or a finished background job reaches the screen with the next frame;
/// - wherever an application asks, with [Models#refresh].
///
/// A change made from none of those and followed by nothing is the one case the
/// two forms differ on: woven, it asks for a frame; here, it waits for a sweep.
/// [Models#refresh] is the answer, and a native image — where the weaver *has*
/// run — does not have the question.
///
/// ## What it needs of the application
///
/// That the model's package is open to this module. A model in an ordinary
/// classpath application already is; one in a named module says so:
///
/// ```java
/// opens com.example.app to io.github.digitalsmile.goldberry.core;
/// ```
///
/// The failure names the package and that line, because "cannot access a member
/// of class Settings" does not.
final class RuntimeBinding implements BoundModel {

    /// The reflection done once per class rather than once per instance:
    /// resolving handles and validating the annotations is the expensive half,
    /// and it is the half that does not depend on which model it is.
    ///
    /// A [ClassValue] rather than a map, because that is the cache the JVM keeps
    /// beside the class itself — it goes away when the class's loader does, which
    /// a static map keyed by `Class` famously does not.
    private static final ClassValue<Plan> PLANS = new ClassValue<>() {

        @Override
        protected Plan computeValue(Class<?> type) {
            return Plan.of(type);
        }
    };

    /// One binding per model **instance**, found by identity.
    ///
    /// The woven form keeps its listener store in a field of the model. This has
    /// nowhere to put one, so it keeps it here — and by identity rather than by
    /// `equals`, because an `@Actions` class is routinely a record (ADR-0138) and
    /// two records holding equal values are two models with two different sets of
    /// listeners.
    private static final Map<Identity, RuntimeBinding> ATTACHED = new HashMap<>();

    /// Where a key whose model has been collected turns up, so the map does not
    /// keep an entry per model a test ever made.
    private static final ReferenceQueue<Object> COLLECTED = new ReferenceQueue<>();

    /// [#ATTACHED]'s values, as an array that is replaced rather than mutated —
    /// see [#attached].
    private static volatile RuntimeBinding[] snapshot = new RuntimeBinding[0];

    /// Weakly, so that this cache is not what keeps an application's model alive.
    /// Every reader of a binding — a widget's [Observable], a [Subscription] —
    /// reaches the model through here, and each of those is itself held by
    /// something the application owns.
    private final WeakReference<Object> model;

    private final Plan plan;
    private final FieldListeners listeners;

    /// What each woven **reference** field held at the end of the last sweep.
    ///
    /// The sweep's whole state, and the reason "fired once per change, not once
    /// per write" survives a mechanism that cannot see the write.
    private final Object[] seen;

    /// The same, for the primitive fields, as bits.
    ///
    /// Not an `Object[]`, and this is the difference between 15.7 ns per field
    /// and 5.2 ns: a boxed comparison allocates an `Integer` on **every** sweep
    /// of every `int` field only to throw it away, where a `long` compare
    /// allocates nothing and is one instruction. The bits are the field's value
    /// widened, except for `float` and `double`, which go through
    /// `floatToIntBits`/`doubleToLongBits` — canonical NaN and a distinct -0.0,
    /// which is exactly the answer `Objects.equals` gave on the boxes and
    /// `Double.compare` gives in the woven setter (ADR-0155).
    private final long[] bits;

    /// Guards the sweep against itself: a listener may run an action, and an
    /// action ends in a sweep.
    private boolean sweeping;

    /// Built once and kept, like the woven `bindings()` — a document reload
    /// resolves its paths against the same registry, so the values survive it
    /// (ADR-0051).
    private BindingRegistry bindings;

    private RuntimeBinding(Object model, Plan plan) {
        this.model = new WeakReference<>(model);
        this.plan = plan;
        var woven = plan.woven();
        this.listeners = new FieldListeners(woven.length);
        this.seen = new Object[woven.length];
        this.bits = new long[woven.length];
        for (var bound : woven) {
            // Seeded rather than left at zero, so the first sweep reports what
            // has moved since the model was bound and not what it was born with.
            bound.moved(model, seen, bits);
        }
    }

    /// The binding for `model`, created on first use and kept for as long as the
    /// model is.
    ///
    /// @throws IllegalStateException if the class is annotated neither [Model]
    ///         nor [Actions], or is annotated and cannot be bound
    static synchronized RuntimeBinding of(Object model) {
        Objects.requireNonNull(model, "model");
        if (purge()) {
            resnapshot();
        }
        var existing = ATTACHED.get(new Identity(model, null));
        if (existing != null) {
            return existing;
        }
        var created = new RuntimeBinding(model, PLANS.get(model.getClass()));
        ATTACHED.put(new Identity(model, COLLECTED), created);
        resnapshot();
        return created;
    }

    /// Drops the entries whose models have been collected.
    ///
    /// @return whether anything went
    private static boolean purge() {
        var dropped = false;
        for (Reference<?> stale; (stale = COLLECTED.poll()) != null; ) {
            ATTACHED.remove(stale);
            dropped = true;
        }
        return dropped;
    }

    /// Rebuilds [#snapshot] from the map. Called under this class's monitor,
    /// which is the only place the map changes.
    private static void resnapshot() {
        snapshot = ATTACHED.values().toArray(new RuntimeBinding[0]);
    }

    @Override
    public Object boundValue(int slot) {
        var woven = plan.woven();
        if (slot < 0 || slot >= woven.length) {
            throw new IllegalArgumentException(
                    plan.type().getName() + " has no bound field in slot " + slot);
        }
        return woven[slot].read(alive());
    }

    @Override
    public FieldListeners boundListeners() {
        return listeners;
    }

    @Override
    public BindingRegistry bindings() {
        if (bindings != null) {
            return bindings;
        }
        var registry = BindingRegistry.strict();
        var owner = alive();
        for (var bound : plan.bounds()) {
            registry.bind(bound.path, bound.property()
                    ? (Observable<?>) bound.read(owner)
                    : listeners.view(this, bound.slot));
        }
        bindings = registry;
        return registry;
    }

    @Override
    public ActionRegistry actions() {
        // Fresh on every call, exactly as the woven `actions()` is: an
        // application routinely adds the window's own actions to what a model
        // published, and a shared registry would refuse the second caller.
        var registry = ActionRegistry.strict();
        for (var action : plan.actions()) {
            if (action.valued()) {
                registry.bind(action.name(), (Consumer<String>) value -> {
                    action.invoke(alive(), value);
                    sweptAfterAnAction();
                });
            } else {
                registry.bind(action.name(), (Runnable) () -> {
                    action.invoke(alive(), null);
                    sweptAfterAnAction();
                });
            }
        }
        return registry;
    }

    /// The sweep an action ends in: this model first, then every other one bound
    /// at run time.
    ///
    /// **Every other one**, because an action routinely does not write to the
    /// model that published it. That is the shape the toolkit asks applications
    /// for — values in one class, the methods that change them in another beside
    /// it ([ADR-0134], [ADR-0136]) — and an `@Actions` record holds no fields at
    /// all, so sweeping only itself would sweep nothing whatsoever.
    ///
    /// Measured at **14 ns per attached model and 9 ns per bound field**, per
    /// press (`BindingSchemeBenchmark`, ADR-0155) — so ten models cost a button
    /// roughly 140 ns against a 16 ms frame. The woven form pays none of it,
    /// because its setter already knows which field moved.
    ///
    /// It is not a cost worth an index of who writes to whom, and an index is not
    /// something reflection could build: finding out which models a method
    /// assigns to means reading its bytecode, which is the weaver's job and the
    /// thing this exists to avoid needing.
    private void sweptAfterAnAction() {
        refresh();
        for (var other : attached()) {
            if (other != this) {
                other.refresh();
            }
        }
    }

    /// Every binding attached right now.
    ///
    /// A snapshot rebuilt when the map changes rather than copied when it is
    /// read: this is on the path of **every action a document dispatches**, and a
    /// `List.copyOf` there was an allocation and a lock per button press. The
    /// array is replaced, never mutated, so a reader either sees the old one or
    /// the new one and a listener that binds a fresh model mid-sweep disturbs
    /// nothing.
    ///
    /// A binding whose model has since been collected may still be in here; its
    /// [#refresh] sees a null owner and returns, and the entry goes at the next
    /// [#purge].
    private static RuntimeBinding[] attached() {
        return snapshot;
    }

    /// Compares every woven field against what it last held and notifies what
    /// moved.
    ///
    /// The order within one field is the woven setter's, and deliberately: the
    /// restyle first — so a window has dropped its resolved styles before
    /// anything rebuilds against them — then the field's own listeners, then the
    /// frame request. A sweep that moves three fields asks for three frames, the
    /// same three a woven model asks for and which the frame scheduler coalesces
    /// (ADR-0122).
    ///
    /// @return whether anything had changed
    boolean refresh() {
        if (sweeping) {
            // A listener that writes to the model it is being notified about
            // would otherwise re-enter here and notify itself. The write is not
            // lost: either the sweep in progress has not reached that field yet,
            // or the next one will.
            return false;
        }
        var owner = model.get();
        if (owner == null) {
            return false;
        }
        var changed = false;
        sweeping = true;
        try {
            // An indexed walk over an array, not an enhanced for over a List: this
            // runs per press and per frame, and the iterator was an allocation
            // per sweep for a loop that usually finds nothing.
            var woven = plan.woven();
            for (var i = 0; i < woven.length; i++) {
                var bound = woven[i];
                if (!bound.moved(owner, seen, bits)) {
                    continue;
                }
                changed = true;
                if (bound.restyle) {
                    listeners.restyled();
                }
                // Boxed here and only here -- once the field is known to have
                // moved, which is what the woven setter does too.
                listeners.fire(i, bound.read(owner));
                if (bound.repaint) {
                    listeners.repainted();
                }
            }
        } finally {
            sweeping = false;
        }
        return changed;
    }

    /// The model, or a failure that says it has been collected.
    ///
    /// Reachable in every ordinary case: whoever is asking got here through an
    /// [Observable] or an action bound to this instance, and the application is
    /// holding the model those came from.
    private Object alive() {
        var owner = model.get();
        if (owner == null) {
            throw new IllegalStateException("the " + plan.type().getName()
                    + " this binding belongs to has been collected; a binding is a window onto a"
                    + " model and outlives nothing");
        }
        return owner;
    }

    @Override
    public String toString() {
        return "RuntimeBinding[" + plan.type().getName() + ", " + plan.bounds().length
                + " binding(s), " + plan.actions().length + " action(s)]";
    }

    // --- the per-class plan --------------------------------------------------

    /// Everything about one model class that does not depend on the instance.
    ///
    /// @param type the class as the author wrote it
    /// @param bounds its `@Bind` fields in member-name order, which is the order
    ///        the registry is built in
    /// @param woven the same fields minus the `Property` ones, indexed by the
    ///        slot the weaver would have given them
    /// @param actions its `@Action` methods
    /// Arrays and not lists, and never compared: this is walked on every sweep,
    /// and an enhanced `for` over a `List` allocates an iterator each time.
    private record Plan(Class<?> type, Bound[] bounds, Bound[] woven, Act[] actions) {

        /// Reads `type`'s annotations and resolves a handle per member.
        ///
        /// The rules are the weaver's, checked here for the reason it checks
        /// them: a `@Bind` on a `static` field, or a second field claiming a
        /// taken path, is a mistake whose only symptom is a control that never
        /// moves.
        static Plan of(Class<?> type) {
            if (!type.isAnnotationPresent(Model.class) && !type.isAnnotationPresent(Actions.class)) {
                throw new IllegalStateException(type.getName()
                        + " is annotated neither @Model nor @Actions, so it publishes no bindings"
                        + " or actions. Annotate the class — @Model if it holds values, @Actions"
                        + " if it only has methods — or build the registries by hand with"
                        + " BindingRegistry.strict() and ActionRegistry.strict().");
            }
            if (type.isAnnotationPresent(Model.class) && type.isAnnotationPresent(Actions.class)) {
                throw new IllegalStateException(type.getName() + " is annotated both @Model and"
                        + " @Actions. A class with values is a model; @Actions is for one that"
                        + " acts on somebody else's (ADR-0139).");
            }
            for (var parent = type.getSuperclass(); parent != null && parent != Object.class;
                    parent = parent.getSuperclass()) {
                if (parent.isAnnotationPresent(Model.class)
                        || parent.isAnnotationPresent(Actions.class)) {
                    throw new IllegalStateException(type.getName() + " extends " + parent.getName()
                            + ", which is also a model. A subclass would publish its parent's paths"
                            + " against its own listeners and notify neither reliably; keep the"
                            + " values in one class, or hold the other as a field.");
                }
            }
            var lookup = lookupIn(type);
            var claimed = new LinkedHashMap<String, String>();
            var bounds = collectBinds(type, lookup, claimed);
            var actions = collectActions(type, lookup, claimed);
            var woven = bounds.stream().filter(bound -> !bound.property()).toArray(Bound[]::new);
            return new Plan(type, bounds.toArray(new Bound[0]), woven, actions.toArray(new Act[0]));
        }

        /// A lookup with private access to `type`, or the failure that says which
        /// line the application is missing.
        ///
        /// `privateLookupIn` asks for two things and only one of them is the
        /// application's to give. The **open** is: a model in a named module has
        /// to open its package to this one, and nothing here can do that for it.
        /// The **read edge** is not: an application `requires` the toolkit and the
        /// toolkit has no business requiring the application back, so this adds
        /// the edge itself — the one call a module may make about its own reads,
        /// and the reason it is legal is that this code *is* that module.
        private static MethodHandles.Lookup lookupIn(Class<?> type) {
            var toolkit = RuntimeBinding.class.getModule();
            var application = type.getModule();
            if (toolkit != application && !toolkit.canRead(application)) {
                toolkit.addReads(application);
            }
            try {
                return MethodHandles.privateLookupIn(type, MethodHandles.lookup());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(type.getName() + " cannot be bound at run time:"
                        + " its package is not open to this module. Add\n\n    opens "
                        + type.getPackageName() + " to io.github.digitalsmile.goldberry.core;\n\n"
                        + "to module " + application.getName() + ", or weave this module -- the"
                        + " woven form reflects on nothing and needs no `opens` at all"
                        + " (ADR-0155).", e);
            }
        }

        private static List<Bound> collectBinds(Class<?> type, MethodHandles.Lookup lookup,
                Map<String, String> claimed) {

            var bounds = new ArrayList<Bound>();
            var slot = 0;
            for (Field field : declared(type.getDeclaredFields(), Field::getName)) {
                var bind = field.getAnnotation(Bind.class);
                if (bind == null) {
                    continue;
                }
                var where = "@Bind field " + type.getName() + "." + field.getName();
                claim(claimed, bind.value(), where);
                if (Modifier.isStatic(field.getModifiers())) {
                    throw new IllegalStateException(where + " is static; a binding belongs to a"
                            + " model instance, and a static one would be shared by every window"
                            + " in the process");
                }
                var property = field.getType() == Property.class;
                if (!property && Modifier.isFinal(field.getModifiers())) {
                    throw new IllegalStateException(where + " is final; a value that cannot change"
                            + " is not something to subscribe to, and binding one shows up as a"
                            + " control that never moves. Drop `final`, or hold a Property if the"
                            + " cell itself is shared.");
                }
                if (field.getType().isArray()) {
                    throw new IllegalStateException(where + " is an array; only the assignment is"
                            + " observed, so `values[0] = x` would notify nobody. Hold a List and"
                            + " assign a new one — a value that is edited in place is not a"
                            + " value.");
                }
                if (bind.restyle() && property) {
                    throw new IllegalStateException(where + " is a Property and asks for a"
                            + " restyle; nothing observes the writes to it, so there is nowhere to"
                            + " put the call. Hold the value as a plain field, or call"
                            + " Host.restyle() yourself.");
                }
                VarHandle handle;
                try {
                    handle = lookup.unreflectVarHandle(field);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(where + " cannot be read reflectively", e);
                }
                bounds.add(boundFor(field.getType(), bind.value(), handle, property ? -1 : slot,
                        bind.restyle(), bind.repaint()));
                if (!property) {
                    slot++;
                }
            }
            return bounds;
        }

        private static List<Act> collectActions(Class<?> type, MethodHandles.Lookup lookup,
                Map<String, String> claimed) {

            var actions = new ArrayList<Act>();
            for (Method method : declared(type.getDeclaredMethods(), Method::getName)) {
                var action = method.getAnnotation(Action.class);
                if (action == null) {
                    continue;
                }
                var where = "@Action method " + type.getName() + "." + method.getName();
                claim(claimed, action.value(), where);
                if (Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalStateException(where + " is static; an action changes a"
                            + " model, and a static one has no model to change");
                }
                if (method.getParameterCount() > 1) {
                    throw new IllegalStateException(where + " takes " + method.getParameterCount()
                            + " arguments; a control reports either *that* something happened or"
                            + " *what* it should become, never both");
                }
                var param = method.getParameterCount() == 1 ? method.getParameterTypes()[0] : null;
                var parser = param == null ? null : Act.parser(param);
                if (param != null && parser == null) {
                    throw new IllegalStateException(where + " takes a " + param.getSimpleName()
                            + "; a valued action crosses as a String, so the parameter must be one"
                            + " of String, double, int, boolean (or their boxes)");
                }
                MethodHandle handle;
                try {
                    // Erased to `(Object[, Object])void`, so the call below is one
                    // `invokeExact` and the cast, the unboxing and the dropped
                    // return value are all the handle's business.
                    handle = lookup.unreflect(method).asType(param == null
                            ? MethodType.methodType(void.class, Object.class)
                            : MethodType.methodType(void.class, Object.class, Object.class));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(where + " cannot be called reflectively", e);
                }
                actions.add(new Act(action.value(), handle, parser));
            }
            return List.copyOf(actions);
        }

        /// `type`'s members in a **stable** order.
        ///
        /// `getDeclaredFields` and `getDeclaredMethods` promise no order at all,
        /// and both are free to return a different one on a different JVM or a
        /// different run. Two things here depend on the order, so neither may be
        /// left to that: the slot each field is given, and the order a strict
        /// registry prints its "Bound: ..." list in — a failure message that
        /// reshuffles between runs is one nobody can diff.
        ///
        /// By member name rather than by the path claimed, because the name is
        /// what the author reads in the stack trace beside it.
        ///
        /// It is **not** the woven order, which is the class file's: the two forms
        /// of a binding publish the same names and make no promise about the
        /// sequence, and there is no way to recover declaration order reflectively
        /// to promise one.
        private static <M> List<M> declared(M[] members, Function<M, String> name) {
            var ordered = new ArrayList<>(List.of(members));
            ordered.sort(java.util.Comparator.comparing(name));
            return ordered;
        }

        private static void claim(Map<String, String> claimed, String path, String where) {
            var previous = claimed.putIfAbsent(path, where);
            if (previous != null) {
                throw new IllegalStateException("\"" + path + "\" is claimed by both " + previous
                        + " and " + where + "; two features quietly sharing one name is a bug that"
                        + " presents as a value changing by itself");
            }
        }
    }

    /// One `@Bind` field, resolved, and specialised to the field's own type.
    ///
    /// One subclass per kind rather than one class reading `Object`, for the
    /// reason the sweep exists at all: `handle.get(model)` typed as `Object`
    /// **boxes on every read**, and a sweep reads every field of every model on
    /// every press. Asking the same `VarHandle` for an `int` and comparing two
    /// `long`s is 3× quicker and allocates nothing (ADR-0155).
    ///
    /// The comparison each subclass makes is the one `Objects.equals` made on the
    /// boxes, which is in turn the one the woven setter makes on the raw values —
    /// including `Float`/`Double`, where the bits are the *canonical* ones so that
    /// `NaN` equals `NaN` and `-0.0` does not equal `0.0`.
    private abstract static sealed class Bound {

        final String path;
        final VarHandle handle;

        /// Its index in [FieldListeners], or -1 for a `Property` field — which is
        /// already observable and takes no slot, here as in the weaver.
        final int slot;

        final boolean restyle;
        final boolean repaint;

        Bound(String path, VarHandle handle, int slot, boolean restyle, boolean repaint) {
            this.path = path;
            this.handle = handle;
            this.slot = slot;
            this.restyle = restyle;
            this.repaint = repaint;
        }

        boolean property() {
            return slot < 0;
        }

        /// The current value, boxed. What [#boundValue] promises and what a
        /// notification carries — so it is called on a change, not on a sweep.
        abstract Object read(Object model);

        /// Reads the field, compares it with what `seen`/`bits` holds for this
        /// slot, and updates that record if it has moved.
        ///
        /// Two arrays and not one because there are two kinds of answer: a
        /// reference is remembered as itself, and a primitive as bits. Each
        /// subclass touches exactly one of them.
        ///
        /// @return whether it had moved
        abstract boolean moved(Object model, Object[] seen, long[] bits);
    }

    /// The general case, and the only one that still compares two objects.
    /// Also what a `Property` field is, which is never swept.
    private static final class Ref extends Bound {

        Ref(String path, VarHandle handle, int slot, boolean restyle, boolean repaint) {
            super(path, handle, slot, restyle, repaint);
        }

        @Override
        Object read(Object model) {
            return handle.get(model);
        }

        @Override
        boolean moved(Object model, Object[] seen, long[] bits) {
            var now = (Object) handle.get(model);
            if (Objects.equals(now, seen[slot])) {
                return false;
            }
            seen[slot] = now;
            return true;
        }
    }

    /// The integral kinds, which differ only in the cast that keeps the read
    /// unboxed and in how the value is boxed once it has moved.
    private abstract static sealed class Integral extends Bound {

        Integral(String path, VarHandle handle, int slot, boolean restyle, boolean repaint) {
            super(path, handle, slot, restyle, repaint);
        }

        /// The field, widened. The cast at each override's call site is what
        /// stops `VarHandle` boxing on the way out.
        abstract long value(Object model);

        @Override
        final boolean moved(Object model, Object[] seen, long[] bits) {
            var now = value(model);
            if (now == bits[slot]) {
                return false;
            }
            bits[slot] = now;
            return true;
        }
    }

    private static final class Bool extends Integral {

        Bool(String path, VarHandle handle, int slot, boolean restyle, boolean repaint) {
            super(path, handle, slot, restyle, repaint);
        }

        @Override
        long value(Object model) {
            return (boolean) handle.get(model) ? 1 : 0;
        }

        @Override
        Object read(Object model) {
            return (boolean) handle.get(model);
        }
    }

    private static final class Int8 extends Integral {

        Int8(String path, VarHandle handle, int slot, boolean restyle, boolean repaint) {
            super(path, handle, slot, restyle, repaint);
        }

        @Override
        long value(Object model) {
            return (byte) handle.get(model);
        }

        @Override
        Object read(Object model) {
            return (byte) handle.get(model);
        }
    }

    private static final class Char16 extends Integral {

        Char16(String path, VarHandle handle, int slot, boolean restyle, boolean repaint) {
            super(path, handle, slot, restyle, repaint);
        }

        @Override
        long value(Object model) {
            return (char) handle.get(model);
        }

        @Override
        Object read(Object model) {
            return (char) handle.get(model);
        }
    }

    private static final class Int16 extends Integral {

        Int16(String path, VarHandle handle, int slot, boolean restyle, boolean repaint) {
            super(path, handle, slot, restyle, repaint);
        }

        @Override
        long value(Object model) {
            return (short) handle.get(model);
        }

        @Override
        Object read(Object model) {
            return (short) handle.get(model);
        }
    }

    private static final class Int32 extends Integral {

        Int32(String path, VarHandle handle, int slot, boolean restyle, boolean repaint) {
            super(path, handle, slot, restyle, repaint);
        }

        @Override
        long value(Object model) {
            return (int) handle.get(model);
        }

        @Override
        Object read(Object model) {
            return (int) handle.get(model);
        }
    }

    private static final class Int64 extends Integral {

        Int64(String path, VarHandle handle, int slot, boolean restyle, boolean repaint) {
            super(path, handle, slot, restyle, repaint);
        }

        @Override
        long value(Object model) {
            return (long) handle.get(model);
        }

        @Override
        Object read(Object model) {
            return (long) handle.get(model);
        }
    }

    /// `floatToIntBits` and not `==`: it canonicalises `NaN` so that a second
    /// write of `NaN` is silent, and keeps `-0.0` distinct from `0.0`. Which is
    /// `Objects.equals` on two `Float`s, and `Float.compare` in the woven setter.
    private static final class Float32 extends Integral {

        Float32(String path, VarHandle handle, int slot, boolean restyle, boolean repaint) {
            super(path, handle, slot, restyle, repaint);
        }

        @Override
        long value(Object model) {
            return Float.floatToIntBits((float) handle.get(model));
        }

        @Override
        Object read(Object model) {
            return (float) handle.get(model);
        }
    }

    private static final class Float64 extends Integral {

        Float64(String path, VarHandle handle, int slot, boolean restyle, boolean repaint) {
            super(path, handle, slot, restyle, repaint);
        }

        @Override
        long value(Object model) {
            return Double.doubleToLongBits((double) handle.get(model));
        }

        @Override
        Object read(Object model) {
            return (double) handle.get(model);
        }
    }

    /// The subclass for a field of `type`.
    private static Bound boundFor(Class<?> type, String path, VarHandle handle, int slot,
            boolean restyle, boolean repaint) {

        return switch (type.getName()) {
            case "boolean" -> new Bool(path, handle, slot, restyle, repaint);
            case "byte" -> new Int8(path, handle, slot, restyle, repaint);
            case "char" -> new Char16(path, handle, slot, restyle, repaint);
            case "short" -> new Int16(path, handle, slot, restyle, repaint);
            case "int" -> new Int32(path, handle, slot, restyle, repaint);
            case "long" -> new Int64(path, handle, slot, restyle, repaint);
            case "float" -> new Float32(path, handle, slot, restyle, repaint);
            case "double" -> new Float64(path, handle, slot, restyle, repaint);
            default -> new Ref(path, handle, slot, restyle, repaint);
        };
    }

    /// One `@Action` method, resolved to a handle and the parse its parameter
    /// needs.
    ///
    /// @param parser turns the `String` a document carries into the argument, or
    ///        null for an action that takes none
    private record Act(String name, MethodHandle handle, Function<String, Object> parser) {

        boolean valued() {
            return parser != null;
        }

        void invoke(Object model, String value) {
            try {
                if (parser == null) {
                    handle.invokeExact(model);
                } else {
                    handle.invokeExact(model, parser.apply(value));
                }
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                // A checked exception out of a model's own method. There is
                // nothing to declare it as here — what a document resolves to is
                // a Runnable — so it is wrapped with the name of the action that
                // threw it.
                throw new IllegalStateException("the action \"" + name + "\" threw", e);
            }
        }

        /// The `String` → parameter conversion, or null for a type no action may
        /// take. The same four the weaver emits a `parseXxx` for.
        static Function<String, Object> parser(Class<?> param) {
            return switch (param.getName()) {
                case "java.lang.String" -> value -> value;
                case "double", "java.lang.Double" -> Double::valueOf;
                case "int", "java.lang.Integer" -> Integer::valueOf;
                case "boolean", "java.lang.Boolean" -> Boolean::valueOf;
                default -> null;
            };
        }
    }

    /// A weak key that is the model's **identity**.
    ///
    /// `WeakHashMap` would be the obvious store and is the wrong one twice over:
    /// it compares with `equals`, so a record model would collide with an equal
    /// one; and its value would hold the model, so the key it is filed under
    /// would never be cleared.
    private static final class Identity extends WeakReference<Object> {

        private final int hash;

        Identity(Object model, ReferenceQueue<Object> queue) {
            super(model, queue);
            this.hash = System.identityHashCode(model);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            // A cleared key equals only itself, which is what makes a stale entry
            // removable by the reference the queue handed back and reachable by
            // no lookup.
            var model = get();
            return other instanceof Identity key && key.hash == hash && model != null
                    && model == key.get();
        }
    }
}
