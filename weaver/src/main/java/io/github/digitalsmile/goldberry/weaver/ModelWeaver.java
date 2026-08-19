package io.github.digitalsmile.goldberry.weaver;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Interfaces;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// Rewires a `@Model`'s raw fields into bindings, in its own bytecode.
///
/// The whole of ADR-0125, and it is smaller than it sounds. For a model like
///
/// ```java
/// @Model final class Settings {
///     @Bind("app.gain") int gain = 40;
///     @Action("app.louder") void louder() { gain++; }
/// }
/// ```
///
/// this does four things to `Settings.class`:
///
/// 1. adds `implements BoundModel`, and a lazily created [FieldListeners] field;
/// 2. synthesises `goldberry$set$gain(int)` — store, compare, notify;
/// 3. rewrites every `putfield gain` **in this class** into a call to it, which
///    is a one-for-one instruction swap because `putfield` and an instance call
///    take their operands in the same order;
/// 4. writes `bindings()` and `actions()`, the second as `invokedynamic` call
///    sites bootstrapped by `LambdaMetafactory` — the same call site `javac`
///    emits for `settings::louder`.
///
/// Reads are left alone. `getfield` is already the fastest thing that could
/// happen and there is nothing to observe about it, so a model pays for a binding
/// only where it writes.
///
/// ## Why a build step
///
/// Because a field write cannot be intercepted any other way. A subclass cannot
/// do it — `putfield` is not virtual — so the class that declares the field is
/// the only place the rewrite can happen, and doing it to the compiled class in
/// the build is the one option that needs no agent, no `opens`, and nothing
/// generated at run time. That last part is what lets the result go into a
/// GraalVM image at all (ADR-0127).
public final class ModelWeaver {

    private static final String BIND_PACKAGE = "io.github.digitalsmile.goldberry.bind.";

    private static final ClassDesc CD_MODEL = ClassDesc.of(BIND_PACKAGE + "Model");
    private static final ClassDesc CD_ACTIONS_MARKER = ClassDesc.of(BIND_PACKAGE + "Actions");
    private static final ClassDesc CD_BIND = ClassDesc.of(BIND_PACKAGE + "Bind");
    private static final ClassDesc CD_ACTION = ClassDesc.of(BIND_PACKAGE + "Action");
    private static final ClassDesc CD_BOUND_MODEL = ClassDesc.of(BIND_PACKAGE + "BoundModel");
    private static final ClassDesc CD_FIELD_LISTENERS = ClassDesc.of(BIND_PACKAGE + "FieldListeners");
    private static final ClassDesc CD_BINDINGS = ClassDesc.of(BIND_PACKAGE + "BindingRegistry");
    private static final ClassDesc CD_ACTIONS = ClassDesc.of(BIND_PACKAGE + "ActionRegistry");
    private static final ClassDesc CD_OBSERVABLE = ClassDesc.of(BIND_PACKAGE + "Observable");
    private static final ClassDesc CD_PROPERTY = ClassDesc.of(BIND_PACKAGE + "Property");

    private static final ClassDesc CD_OBJECTS = ClassDesc.of("java.util.Objects");
    private static final ClassDesc CD_RUNNABLE = ClassDesc.of("java.lang.Runnable");
    private static final ClassDesc CD_CONSUMER = ClassDesc.of("java.util.function.Consumer");
    private static final ClassDesc CD_LMF = ClassDesc.of("java.lang.invoke.LambdaMetafactory");
    private static final ClassDesc CD_IAE = ClassDesc.of("java.lang.IllegalArgumentException");

    /// The name a synthesised setter gets, prefixed so it cannot collide with
    /// anything an author would write and is obvious in a stack trace.
    private static final String SETTER_PREFIX = "goldberry$set$";
    private static final String BRIDGE_PREFIX = "goldberry$action$";
    private static final String LISTENERS_FIELD = "goldberry$listeners";
    private static final String BINDINGS_FIELD = "goldberry$bindings";

    /// `identifier(.identifier)*` — the same grammar `BindingRegistry` enforces at run
    /// time, checked here first so a typo is a build failure (ADR-0062).
    private static final Pattern PATH = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*(\\.[A-Za-z_][A-Za-z0-9_-]*)*");

    private ModelWeaver() {
    }

    /// A woven model, as a *different* class needs to know it.
    ///
    /// What pass one of [WeaverMain] collects, so pass two can rewrite a write to
    /// `model.gain` wherever it appears — not only inside the model. Which is
    /// what lets an application keep its values in one class and the methods that
    /// change them in another
    /// ([ADR-0134](../../../../book/src/adr/0134-a-write-is-rewritten-wherever-it-is.md)).
    ///
    /// @param owner  the model's own type
    /// @param fields every rewired `@Bind` field, by name, to its declared type
    public record Rewired(ClassDesc owner, Map<String, ClassDesc> fields) {
    }

    /// One `@Bind` field.
    ///
    /// @param path     what markup names it
    /// @param field    the field's own name
    /// @param type     its declared type
    /// @param slot     its index in [FieldListeners], or -1 for a `Property`
    /// @param property whether it is already observable and needs no rewiring
    private record Bound(String path, String field, ClassDesc type, int slot, boolean property,
            boolean restyle, boolean repaint) {
    }

    /// One `@Action` method.
    ///
    /// @param name    what markup names it
    /// @param method  the method's own name
    /// @param owner   its descriptor as declared
    /// @param hidden  whether it is private, which decides the call opcode
    /// @param param   its single parameter type, or null when it takes none
    private record Act(String name, String method, MethodTypeDesc owner, boolean hidden, ClassDesc param) {
    }

    /// Weaves one class.
    ///
    /// @param bytes a compiled class file
    /// @return the woven class, or **null** if it is not a `@Model` and should be
    ///         left exactly as it was — the common case, since this runs over
    ///         every class in a module
    /// @throws WeaveException if it is a `@Model` the toolkit refuses
    public static byte[] weave(byte[] bytes) {
        return weave(bytes, Map.of());
    }

    /// Weaves one class against every model in the same compilation.
    ///
    /// Two things happen here, and only the first needs the class to be a model:
    ///
    /// - **every** class has its writes to a woven `@Bind` field rewritten, so an
    ///   application may keep its values in one class and the methods that change
    ///   them in another (ADR-0134);
    /// - a `@Model` also gets the interface, the listener store, the setters and
    ///   the two registries.
    ///
    /// @param bytes  a compiled class file
    /// @param models every `@Model` in this compilation, by internal name — what
    ///               pass one of [WeaverMain] collected
    /// @return the woven class, or **null** when nothing about it changed
    /// @throws WeaveException if it is a `@Model` the toolkit refuses, or writes
    ///         to one it may not reach
    public static byte[] weave(byte[] bytes, Map<String, Rewired> models) {
        return weave(bytes, models, java.util.Set.of());
    }

    /// The same, told which models are written to from outside their own nest.
    ///
    /// Those, and only those, get package-private setters. Everything else keeps
    /// `private` ones, so a model whose actions are a nested class is exactly as
    /// encapsulated as one that has no actions at all (ADR-0137).
    ///
    /// @param reachedFromOutsideTheNest internal names of the models that need a
    ///                                  setter a sibling class can call
    public static byte[] weave(byte[] bytes, Map<String, Rewired> models,
            java.util.Set<String> reachedFromOutsideTheNest) {
        var classFile = ClassFile.of();
        var model = classFile.parse(bytes);
        var owner = model.thisClass().asSymbol();
        var mine = isModel(model);

        if (mine && alreadyWoven(model)) {
            // The build task rewrites classes in place, so it hands this the same
            // file again on the next build that did not recompile -- and weaving
            // twice would add every synthesised member a second time, which is a
            // class file the verifier rejects.
            return null;
        }
        if (!mine) {
            // Not a model, but it may still assign to one. Rewritten only if it
            // actually does, so the common class is not even re-serialised.
            if (!writesToAModel(model, models)) {
                return null;
            }
            return classFile.transformClass(model, rewriteWrites(owner, models));
        }

        var name = owner.displayName();
        var values = marked(model, CD_MODEL);
        var onlyActions = marked(model, CD_ACTIONS_MARKER);
        if (values && onlyActions) {
            throw new WeaveException(name + " is annotated both @Model and @Actions."
                    + " A class holds values or it does not: @Model for one that has @Bind"
                    + " fields (and may have @Action methods too), @Actions for one that has"
                    + " only methods.");
        }
        if (model.flags().has(AccessFlag.INTERFACE) || model.flags().has(AccessFlag.ABSTRACT)) {
            throw new WeaveException("@Model " + name + " is abstract; a model is instantiated"
                    + " and its fields are the state, so there is nothing to weave into");
        }
        var parent = model.superclass().map(c -> c.asInternalName()).orElse("java/lang/Object");
        if (models.containsKey(parent)) {
            throw new WeaveException("@Model " + name + " extends " + parent.replace('/', '.')
                    + ", which is a @Model too. Each would get its own listener store and the"
                    + " subclass's would shadow the superclass's, so the inherited @Bind fields"
                    + " would notify nobody. Put the state in one class, or hold the other as a"
                    + " field.");
        }

        var claimed = new LinkedHashMap<String, String>();
        var bounds = collectBinds(model, name, claimed);
        var actions = collectActions(model, name, claimed);
        if (onlyActions && !bounds.isEmpty()) {
            throw new WeaveException("@Actions " + name + " has a @Bind field ("
                    + bounds.getFirst().field() + "); a class that holds values is a @Model."
                    + " Annotate it @Model, or move the field to the values it acts on.");
        }
        if (onlyActions && actions.isEmpty()) {
            throw new WeaveException("@Actions " + name + " has no @Action method;"
                    + " it publishes nothing and the annotation does nothing");
        }
        if (values && bounds.isEmpty() && actions.isEmpty()) {
            throw new WeaveException("@Model " + name + " has no @Bind or @Action member;"
                    + " it publishes nothing and the annotation does nothing");
        }

        var woven = bounds.stream().filter(b -> !b.property()).toList();
        // This model's own fields have to be in the map even when the caller did
        // not supply them -- the single-class `weave(bytes)` door is what the
        // tests use.
        var all = new LinkedHashMap<>(models);
        var mineFields = new LinkedHashMap<String, ClassDesc>();
        for (var bound : woven) {
            mineFields.put(bound.field(), bound.type());
        }
        all.put(model.thisClass().asInternalName(), new Rewired(owner, mineFields));

        var open = reachedFromOutsideTheNest.contains(model.thisClass().asInternalName());
        return classFile.transformClass(model, rewriteWrites(owner, all)
                .andThen(addMembers(model, owner, bounds, woven, actions, open)));
    }

    /// Whether `model` assigns to any woven `@Bind` field.
    private static boolean writesToAModel(ClassModel model, Map<String, Rewired> models) {
        return !modelsWrittenBy(model, models).isEmpty();
    }

    /// Every model `bytes` assigns a `@Bind` field of, by internal name.
    ///
    /// What decides a setter's visibility: a model written to only from inside
    /// its own nest keeps private ones (ADR-0137).
    public static java.util.Set<String> modelsWrittenBy(byte[] bytes, Map<String, Rewired> models) {
        return modelsWrittenBy(ClassFile.of().parse(bytes), models);
    }

    private static java.util.Set<String> modelsWrittenBy(ClassModel model, Map<String, Rewired> models) {
        if (models.isEmpty()) {
            return java.util.Set.of();
        }
        var written = new java.util.LinkedHashSet<String>();
        for (var method : model.methods()) {
            var code = method.code().orElse(null);
            if (code == null) {
                continue;
            }
            for (var element : code) {
                if (element instanceof FieldInstruction instruction
                        && instruction.opcode() == Opcode.PUTFIELD) {
                    var owner = instruction.owner().asInternalName();
                    var target = models.get(owner);
                    if (target != null && target.fields().containsKey(instruction.name().stringValue())) {
                        written.add(owner);
                    }
                }
            }
        }
        return written;
    }

    // --- reading what the author declared ------------------------------------

    /// The internal name of `bytes` if it is a `@Model`, and null otherwise.
    ///
    /// The cheap first pass [WeaverMain] does over a whole tree, so that the
    /// second one can refuse a model extending a model.
    public static String modelName(byte[] bytes) {
        var model = ClassFile.of().parse(bytes);
        return isModel(model) ? model.thisClass().asInternalName() : null;
    }

    /// What `bytes` contributes to the second pass, or null if it is not a model.
    ///
    /// The rewired fields only: a `Property` field is already observable and no
    /// write to it is rewritten, here or anywhere.
    public static Rewired rewired(byte[] bytes) {
        var model = ClassFile.of().parse(bytes);
        if (!isModel(model) || alreadyWoven(model)) {
            return null;
        }
        var owner = model.thisClass().asSymbol();
        var fields = new LinkedHashMap<String, ClassDesc>();
        for (var bound : collectBinds(model, owner.displayName(), new LinkedHashMap<>())) {
            if (!bound.property()) {
                fields.put(bound.field(), bound.type());
            }
        }
        return new Rewired(owner, fields);
    }

    /// The nest a class belongs to — itself, unless it is nested in something.
    ///
    /// What decides whether a model's synthesised setters can stay `private`: a
    /// nestmate may call one, and anything else needs the package
    /// ([ADR-0137](../../../../book/src/adr/0137-a-model-keeps-its-fields.md)).
    public static String nestHost(byte[] bytes) {
        var model = ClassFile.of().parse(bytes);
        return model.findAttribute(java.lang.classfile.Attributes.nestHost())
                .map(attribute -> attribute.nestHost().asInternalName())
                .orElseGet(() -> model.thisClass().asInternalName());
    }

    private static boolean alreadyWoven(ClassModel model) {
        return model.interfaces().stream().anyMatch(i -> i.asSymbol().equals(CD_BOUND_MODEL));
    }

    /// Whether the class carries `marker`.
    private static boolean marked(ClassModel model, ClassDesc marker) {
        return model.findAttribute(Attributes0.VISIBLE)
                .map(RuntimeVisibleAnnotationsAttribute::annotations)
                .orElse(List.of())
                .stream()
                .anyMatch(a -> a.classSymbol().equals(marker));
    }

    /// Whether the class is one the weaver has anything to do to.
    ///
    /// Either marker: `@Model` for a class with values, `@Actions` for one with
    /// only methods. Both get the same treatment — the interface, the listener
    /// store, and the two registries — because a class with no `@Bind` field
    /// simply has an empty half (ADR-0139).
    private static boolean isModel(ClassModel model) {
        return marked(model, CD_MODEL) || marked(model, CD_ACTIONS_MARKER);
    }

    /// The `value()` of `wanted` on `member`, or null when it is not annotated.
    static String annotationValue(
            java.util.Optional<RuntimeInvisibleAnnotationsAttribute> attribute, ClassDesc wanted) {

        for (var annotation : attribute.map(RuntimeInvisibleAnnotationsAttribute::annotations).orElse(List.of())) {
            if (!annotation.classSymbol().equals(wanted)) {
                continue;
            }
            for (var element : annotation.elements()) {
                if (element.name().equalsString("value")
                        && element.value() instanceof java.lang.classfile.AnnotationValue.OfString s) {
                    return s.stringValue();
                }
            }
        }
        return null;
    }

    /// A boolean member of an annotation, or `whenAbsent` if it is not written
    /// down.
    ///
    /// The default matters: a member left at its declared default does not appear
    /// in the class file at all, so "absent" and "explicitly the default" are the
    /// same thing here and the caller has to say which way that falls.
    private static boolean annotationFlag(
            java.util.Optional<RuntimeInvisibleAnnotationsAttribute> attribute,
            ClassDesc wanted, String member, boolean whenAbsent) {

        for (var annotation : attribute.map(RuntimeInvisibleAnnotationsAttribute::annotations).orElse(List.of())) {
            if (!annotation.classSymbol().equals(wanted)) {
                continue;
            }
            for (var element : annotation.elements()) {
                if (element.name().equalsString(member)
                        && element.value() instanceof java.lang.classfile.AnnotationValue.OfBoolean flag) {
                    return flag.booleanValue();
                }
            }
        }
        return whenAbsent;
    }

    private static List<Bound> collectBinds(ClassModel model, String owner, Map<String, String> claimed) {
        var bounds = new ArrayList<Bound>();
        var slot = 0;
        for (FieldModel field : model.fields()) {
            var path = annotationValue(field.findAttribute(Attributes0.INVISIBLE), CD_BIND);
            if (path == null) {
                continue;
            }
            var fieldName = field.fieldName().stringValue();
            var type = field.fieldTypeSymbol();
            var where = "@Bind field " + owner + "." + fieldName;

            if (!PATH.matcher(path).matches()) {
                throw new WeaveException(where + " claims \"" + path + "\", which is not a dotted"
                        + " path. A path is a name, or names joined by dots — `gain`,"
                        + " `app.gain` (ADR-0062).");
            }
            claim(claimed, path, where);
            if (field.flags().has(AccessFlag.STATIC)) {
                throw new WeaveException(where + " is static; a binding belongs to a model"
                        + " instance, and a static one would be shared by every window in the"
                        + " process");
            }
            var property = type.equals(CD_PROPERTY);
            if (!property && field.flags().has(AccessFlag.FINAL)) {
                throw new WeaveException(where + " is final; a value that cannot change is not"
                        + " something to subscribe to, and binding one shows up as a control"
                        + " that never moves. Drop `final`, or hold a Property if the cell"
                        + " itself is shared.");
            }
            if (type.isArray()) {
                throw new WeaveException(where + " is an array; only the assignment is observed,"
                        + " so `values[0] = x` would notify nobody. Hold a List and assign a new"
                        + " one — a value that is edited in place is not a value.");
            }
            var restyle = annotationFlag(
                    field.findAttribute(Attributes0.INVISIBLE), CD_BIND, "restyle", false);
            var repaint = annotationFlag(
                    field.findAttribute(Attributes0.INVISIBLE), CD_BIND, "repaint", true);
            if (restyle && property) {
                throw new WeaveException(where + " is a Property and asks for a restyle;"
                        + " the weaver rewires no writes to it, so it has nowhere to put the call."
                        + " Hold the value as a plain field, or call Host.restyle() yourself.");
            }
            bounds.add(new Bound(path, fieldName, type, property ? -1 : slot, property,
                    restyle, repaint));
            if (!property) {
                slot++;
            }
        }
        return bounds;
    }

    private static List<Act> collectActions(ClassModel model, String owner, Map<String, String> claimed) {
        var actions = new ArrayList<Act>();
        for (MethodModel method : model.methods()) {
            var name = annotationValue(method.findAttribute(Attributes0.INVISIBLE), CD_ACTION);
            if (name == null) {
                continue;
            }
            var methodName = method.methodName().stringValue();
            var type = method.methodTypeSymbol();
            var where = "@Action method " + owner + "." + methodName;

            claim(claimed, name, where);
            if (method.flags().has(AccessFlag.STATIC)) {
                throw new WeaveException(where + " is static; an action changes a model, and a"
                        + " static one has no model to change");
            }
            if (type.parameterCount() > 1) {
                throw new WeaveException(where + " takes " + type.parameterCount() + " arguments;"
                        + " a control reports either *that* something happened or *what* it"
                        + " should become, never both");
            }
            var param = type.parameterCount() == 1 ? type.parameterType(0) : null;
            if (param != null && !PARSERS.containsKey(param)) {
                throw new WeaveException(where + " takes a " + param.displayName() + "; a valued"
                        + " action crosses as a String, so the parameter must be one of String,"
                        + " double, int, boolean (or their boxes)");
            }
            actions.add(new Act(name, methodName, type, method.flags().has(AccessFlag.PRIVATE), param));
        }
        return actions;
    }

    private static void claim(Map<String, String> claimed, String path, String where) {
        var previous = claimed.putIfAbsent(path, where);
        if (previous != null) {
            throw new WeaveException("\"" + path + "\" is claimed by both " + previous + " and "
                    + where + "; two features quietly sharing one name is a bug that presents as"
                    + " a value changing by itself");
        }
    }

    // --- rewriting the writes ------------------------------------------------

    /// Turns every `putfield` on a woven field into a call to its setter.
    ///
    /// A one-for-one swap: `putfield` pops *objectref, value* and so does an
    /// instance call taking one argument, so the stack shape either side is
    /// identical and nothing around it has to move.
    ///
    /// Constructors are left alone. A field written before anything could have
    /// subscribed has nobody to notify, and the listener store does not exist
    /// yet — so skipping them is both correct and one less thing to order.
    private static ClassTransform rewriteWrites(ClassDesc self, Map<String, Rewired> models) {
        // A write to *any* woven model's field, wherever it appears. Rewriting
        // only the declaring class was the original rule and it left a silent
        // gap: a nested class assigning to its outer's `@Bind` field compiles to
        // a `putfield` in a different class, which nothing saw (ADR-0134).
        CodeTransform rewrite = rewriter(self, models, false);
        // Inside a constructor the class's *own* fields are left alone -- nothing
        // can have subscribed yet and the listener store does not exist -- but a
        // write to somebody else's model is an ordinary write to an object that
        // was constructed long ago.
        CodeTransform inConstructor = rewriter(self, models, true);

        // One transform that picks per method, rather than two composed with
        // complementary predicates: chaining `transformingMethodBodies` twice
        // rebuilds each method once and the second pass no longer sees the code
        // elements the first handed on, so every rewrite is quietly lost. This
        // was found by every notification test failing at once.
        return (builder, element) -> {
            if (element instanceof MethodModel method && method.code().isPresent()) {
                builder.withMethod(method.methodName(), method.methodType(),
                        method.flags().flagsMask(), out -> out.transformCode(
                                method.code().orElseThrow(),
                                isInitialiser(method) ? inConstructor : rewrite));
            } else {
                builder.with(element);
            }
        };
    }

    private static boolean isInitialiser(MethodModel method) {
        return method.methodName().equalsString(ConstantDescs.INIT_NAME)
                || method.methodName().equalsString(ConstantDescs.CLASS_INIT_NAME);
    }

    /// The instruction swap, for one class.
    ///
    /// @param self          the class being woven, so its own fields can be told
    ///                      from another model's
    /// @param models        every model in this compilation, by internal name
    /// @param sparingOwnFields whether to leave `self`'s own fields alone
    private static CodeTransform rewriter(ClassDesc self, Map<String, Rewired> models,
            boolean sparingOwnFields) {

        return (builder, element) -> {
            if (element instanceof FieldInstruction instruction
                    && instruction.opcode() == Opcode.PUTFIELD) {
                var owner = instruction.owner().asSymbol();
                var model = models.get(instruction.owner().asInternalName());
                var type = model == null ? null : model.fields().get(instruction.name().stringValue());
                var own = owner.equals(self);
                if (type != null && !(own && sparingOwnFields)) {
                    if (!own && !owner.packageName().equals(self.packageName())) {
                        // The synthesised setter is package-private, so a call
                        // from another package would not verify. Refused here
                        // rather than left to be an IllegalAccessError at the
                        // first click.
                        throw new WeaveException(self.displayName() + " assigns to "
                                + owner.displayName() + "." + instruction.name().stringValue()
                                + ", which is a @Bind field of a model in another package."
                                + " A class that changes a model's values has to sit beside it,"
                                + " or ask the model to change them.");
                    }
                    builder.invokevirtual(owner, SETTER_PREFIX + instruction.name().stringValue(),
                            MethodTypeDesc.of(ConstantDescs.CD_void, type));
                    return;
                }
            }
            builder.with(element);
        };
    }

    // --- adding what the interface needs -------------------------------------

    private static ClassTransform addMembers(ClassModel model, ClassDesc owner,
            List<Bound> bounds, List<Bound> woven, List<Act> actions, boolean openSetters) {

        var interfaces = new ArrayList<ClassDesc>();
        for (var existing : model.interfaces()) {
            interfaces.add(existing.asSymbol());
        }
        if (!interfaces.contains(CD_BOUND_MODEL)) {
            interfaces.add(CD_BOUND_MODEL);
        }

        return new ClassTransform() {

            @Override
            public void accept(ClassBuilder builder, ClassElement element) {
                // Replaced wholesale in atEnd -- a transform cannot edit the
                // interface list in place, and dropping it here is how the
                // rebuilt one becomes the only one.
                if (!(element instanceof Interfaces)) {
                    builder.with(element);
                }
            }

            @Override
            public void atEnd(ClassBuilder builder) {
                builder.withInterfaceSymbols(interfaces);
                emitListenersField(builder);
                emitBoundListeners(builder, owner, woven.size());
                emitBoundValue(builder, owner, woven);
                for (var bound : woven) {
                    emitSetter(builder, owner, bound, openSetters);
                }
                for (var action : actions) {
                    emitBridge(builder, owner, action);
                }
                emitBindings(builder, owner, bounds);
                emitActions(builder, owner, actions);
            }
        };
    }

    private static void emitListenersField(ClassBuilder builder) {
        // Transient: a model that serialises has no business carrying the
        // widgets that were watching it.
        builder.withField(LISTENERS_FIELD, CD_FIELD_LISTENERS,
                ClassFile.ACC_PRIVATE | ClassFile.ACC_TRANSIENT | ClassFile.ACC_SYNTHETIC);
        builder.withField(BINDINGS_FIELD, CD_BINDINGS,
                ClassFile.ACC_PRIVATE | ClassFile.ACC_TRANSIENT | ClassFile.ACC_SYNTHETIC);
    }

    /// `boundListeners()`, creating the store on first use.
    ///
    /// Lazily rather than in the constructor, which is the difference between
    /// this weaver and one that has to find the `super()` call in every
    /// constructor and inject after it — including the delegating ones, in the
    /// right order. A null check on a field that is null exactly once is not a
    /// cost worth that.
    private static void emitBoundListeners(ClassBuilder builder, ClassDesc owner, int slots) {
        builder.withMethodBody("boundListeners", MethodTypeDesc.of(CD_FIELD_LISTENERS),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_SYNTHETIC, code -> {
                    var ready = code.newLabel();
                    code.aload(0).getfield(owner, LISTENERS_FIELD, CD_FIELD_LISTENERS).astore(1)
                            .aload(1).ifnonnull(ready)
                            .new_(CD_FIELD_LISTENERS).dup().loadConstant(slots)
                            .invokespecial(CD_FIELD_LISTENERS, ConstantDescs.INIT_NAME,
                                    MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int))
                            .astore(1)
                            .aload(0).aload(1).putfield(owner, LISTENERS_FIELD, CD_FIELD_LISTENERS)
                            .labelBinding(ready)
                            .aload(1).areturn();
                });
    }

    /// `boundValue(int)` — the read half, a switch over the slots.
    private static void emitBoundValue(ClassBuilder builder, ClassDesc owner, List<Bound> woven) {
        builder.withMethodBody("boundValue",
                MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_int),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_SYNTHETIC, code -> {
                    if (woven.isEmpty()) {
                        emitUnknownSlot(code, owner);
                        return;
                    }
                    var cases = new ArrayList<SwitchCase>(woven.size());
                    var targets = new ArrayList<java.lang.classfile.Label>(woven.size());
                    for (var bound : woven) {
                        var label = code.newLabel();
                        targets.add(label);
                        cases.add(SwitchCase.of(bound.slot(), label));
                    }
                    var unknown = code.newLabel();
                    code.iload(1).lookupswitch(unknown, cases);
                    for (var i = 0; i < woven.size(); i++) {
                        var bound = woven.get(i);
                        code.labelBinding(targets.get(i))
                                .aload(0).getfield(owner, bound.field(), bound.type());
                        box(code, bound.type());
                        code.areturn();
                    }
                    code.labelBinding(unknown);
                    emitUnknownSlot(code, owner);
                });
    }

    private static void emitUnknownSlot(CodeBuilder code, ClassDesc owner) {
        code.new_(CD_IAE).dup()
                .loadConstant("no woven @Bind field with that slot on " + owner.displayName())
                .invokespecial(CD_IAE, ConstantDescs.INIT_NAME,
                        MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String))
                .athrow();
    }

    /// `goldberry$set$<field>` — store, compare, notify.
    ///
    /// The comparison is [java.util.Objects#equals]'s answer, reached the cheap
    /// way for each type: an `int` is compared as an `int`, and `Float.compare`
    /// and `Double.compare` are used rather than `==` so that `NaN` and `-0.0`
    /// behave the way a boxed comparison would. Nothing notifies on a write that
    /// changed nothing — the rule that makes two mirrored values terminate
    /// instead of recursing, and the same one `Property.set` follows.
    private static void emitSetter(ClassBuilder builder, ClassDesc owner, Bound bound,
            boolean open) {
        var type = bound.type();
        var kind = TypeKind.from(type);
        // `private` unless something outside this model's nest writes to it, in
        // which case the package is the smallest visibility that lets the call
        // verify. A model whose actions are a nested class therefore keeps every
        // field *and* every setter private -- exactly as encapsulated as a model
        // with no actions at all (ADR-0137). Synthetic either way, so it is not
        // something an IDE offers or a reader trips over.
        builder.withMethodBody(SETTER_PREFIX + bound.field(),
                MethodTypeDesc.of(ConstantDescs.CD_void, type),
                (open ? 0 : ClassFile.ACC_PRIVATE) | ClassFile.ACC_SYNTHETIC, code -> {
                    var changed = code.newLabel();
                    code.aload(0).getfield(owner, bound.field(), type).loadLocal(kind, 1);
                    switch (kind) {
                        case BOOLEAN, BYTE, CHAR, SHORT, INT -> code.if_icmpne(changed);
                        case LONG -> code.lcmp().ifne(changed);
                        case FLOAT -> code.invokestatic(ConstantDescs.CD_Float, "compare",
                                MethodTypeDesc.of(ConstantDescs.CD_int,
                                        ConstantDescs.CD_float, ConstantDescs.CD_float))
                                .ifne(changed);
                        case DOUBLE -> code.invokestatic(ConstantDescs.CD_Double, "compare",
                                MethodTypeDesc.of(ConstantDescs.CD_int,
                                        ConstantDescs.CD_double, ConstantDescs.CD_double))
                                .ifne(changed);
                        case REFERENCE -> code.invokestatic(CD_OBJECTS, "equals",
                                MethodTypeDesc.of(ConstantDescs.CD_boolean,
                                        ConstantDescs.CD_Object, ConstantDescs.CD_Object))
                                .ifeq(changed);
                        default -> throw new WeaveException(
                                "@Bind field " + owner.displayName() + "." + bound.field()
                                        + " is a " + type.displayName() + ", which has no value to hold");
                    }
                    code.return_();

                    code.labelBinding(changed)
                            .aload(0).loadLocal(kind, 1).putfield(owner, bound.field(), type);
                    if (bound.restyle()) {
                        // Before `fire`, so a window has dropped its resolved
                        // styles by the time it is asked for the frame that will
                        // use them (ADR-0133).
                        code.aload(0).invokevirtual(owner, "boundListeners",
                                        MethodTypeDesc.of(CD_FIELD_LISTENERS))
                                .invokevirtual(CD_FIELD_LISTENERS, "restyled",
                                        MethodTypeDesc.of(ConstantDescs.CD_void));
                    }
                    code.aload(0).invokevirtual(owner, "boundListeners",
                                    MethodTypeDesc.of(CD_FIELD_LISTENERS))
                            .loadConstant(bound.slot())
                            .loadLocal(kind, 1);
                    box(code, type);
                    code.invokevirtual(CD_FIELD_LISTENERS, "fire",
                            MethodTypeDesc.of(ConstantDescs.CD_void,
                                    ConstantDescs.CD_int, ConstantDescs.CD_Object));
                    if (bound.repaint()) {
                        // Per field, and emitted rather than decided at run time:
                        // a value declared `repaint = false` costs not a branch
                        // but an instruction that is not there (ADR-0135).
                        code.aload(0).invokevirtual(owner, "boundListeners",
                                        MethodTypeDesc.of(CD_FIELD_LISTENERS))
                                .invokevirtual(CD_FIELD_LISTENERS, "repainted",
                                        MethodTypeDesc.of(ConstantDescs.CD_void));
                    }
                    code.return_();
                });
    }

    /// `bindings()` — `BindingRegistry.strict()` with one `bind` per path, built once.
    ///
    /// Kept, unlike [#emitActions], because this registry is the model's values
    /// and nothing adds to it: a reload re-resolves its paths against the same
    /// object, which is both what reload wants and what makes
    /// `Models.observable(model, path)` a map lookup rather than a rebuild. An
    /// application that resolves a path while building a widget does that once
    /// per frame per widget, and a fresh registry each time would be the kind of
    /// cost nobody goes looking for.
    private static void emitBindings(ClassBuilder builder, ClassDesc owner, List<Bound> bounds) {
        builder.withMethodBody("bindings", MethodTypeDesc.of(CD_BINDINGS),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_SYNTHETIC, code -> {
                    var ready = code.newLabel();
                    code.aload(0).getfield(owner, BINDINGS_FIELD, CD_BINDINGS).astore(1)
                            .aload(1).ifnonnull(ready);

                    code.invokestatic(CD_BINDINGS, "strict", MethodTypeDesc.of(CD_BINDINGS));
                    for (var bound : bounds) {
                        code.loadConstant(bound.path());
                        if (bound.property()) {
                            // Already observable: hand over the property itself,
                            // so a value somebody else owns is published without
                            // being copied.
                            code.aload(0).getfield(owner, bound.field(), CD_PROPERTY);
                        } else {
                            // Through the store, which keeps one window per
                            // field: two windows onto one field that were not the
                            // same object would make a widget's `binding()`
                            // comparison false for a value that has not moved.
                            code.aload(0).invokevirtual(owner, "boundListeners",
                                            MethodTypeDesc.of(CD_FIELD_LISTENERS))
                                    .aload(0).loadConstant(bound.slot())
                                    .invokevirtual(CD_FIELD_LISTENERS, "view",
                                            MethodTypeDesc.of(CD_OBSERVABLE,
                                                    CD_BOUND_MODEL, ConstantDescs.CD_int));
                        }
                        code.invokevirtual(CD_BINDINGS, "bind",
                                MethodTypeDesc.of(CD_BINDINGS, ConstantDescs.CD_String, CD_OBSERVABLE));
                    }
                    code.astore(1)
                            .aload(0).aload(1).putfield(owner, BINDINGS_FIELD, CD_BINDINGS)
                            .labelBinding(ready)
                            .aload(1).areturn();
                });
    }

    /// `actions()` — `ActionRegistry.strict()` with one `invokedynamic` per name.
    ///
    /// Built fresh on every call, unlike [#emitBindings]. An application
    /// routinely *extends* this registry — the showcase adds `app.open-menu` and
    /// `app.toggle-hud`, which are the window's actions and not the model's — and
    /// handing out a shared one would make the second caller fail with "already
    /// bound" for doing exactly what the first did.
    private static void emitActions(ClassBuilder builder, ClassDesc owner, List<Act> actions) {
        builder.withMethodBody("actions", MethodTypeDesc.of(CD_ACTIONS),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_SYNTHETIC, code -> {
                    code.invokestatic(CD_ACTIONS, "strict", MethodTypeDesc.of(CD_ACTIONS));
                    for (var action : actions) {
                        var valued = action.param() != null;
                        code.loadConstant(action.name())
                                .aload(0)
                                .invokedynamic(callSite(owner, action, valued))
                                .invokevirtual(CD_ACTIONS, "bind", MethodTypeDesc.of(
                                        CD_ACTIONS, ConstantDescs.CD_String,
                                        valued ? CD_CONSUMER : CD_RUNNABLE));
                    }
                    code.areturn();
                });
    }

    /// The `LambdaMetafactory` call site that turns one bridge into a `Runnable`
    /// or a `Consumer<String>`.
    ///
    /// Identical in shape to what `javac` writes for `model::louder`: the same
    /// bootstrap, the same three static arguments, the model captured as the
    /// single dynamic argument. Which is the point — this is not a new mechanism,
    /// it is the mechanism a method reference already uses, written by something
    /// other than javac (ADR-0126).
    private static DynamicCallSiteDesc callSite(ClassDesc owner, Act action, boolean valued) {
        var bootstrap = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, CD_LMF,
                "metafactory", MethodTypeDesc.of(ConstantDescs.CD_CallSite,
                        ConstantDescs.CD_MethodHandles_Lookup, ConstantDescs.CD_String,
                        ConstantDescs.CD_MethodType, ConstantDescs.CD_MethodType,
                        ConstantDescs.CD_MethodHandle, ConstantDescs.CD_MethodType));

        var bridge = MethodTypeDesc.of(ConstantDescs.CD_void,
                valued ? new ClassDesc[] {ConstantDescs.CD_String} : new ClassDesc[0]);
        var implementation = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.SPECIAL,
                owner, BRIDGE_PREFIX + action.method(), bridge);

        // The erased shape the interface method has, which is what the call site
        // is checked against; the instantiated one below is the shape it really
        // has once the String is known not to be an Object.
        var erased = MethodTypeDesc.of(ConstantDescs.CD_void,
                valued ? new ClassDesc[] {ConstantDescs.CD_Object} : new ClassDesc[0]);

        return DynamicCallSiteDesc.of(bootstrap, valued ? "accept" : "run",
                MethodTypeDesc.of(valued ? CD_CONSUMER : CD_RUNNABLE, owner),
                erased, implementation, bridge);
    }

    /// The private method the call site actually points at.
    ///
    /// Every action gets one, even the no-argument ones that could have been
    /// referenced directly. It is what makes the parse from `String` somewhere a
    /// person can see rather than something the registry does on the way past,
    /// and it means every call site in the class has the same shape — one bridge,
    /// `private`, returning void, reached by `invokespecial`.
    private static void emitBridge(ClassBuilder builder, ClassDesc owner, Act action) {
        var valued = action.param() != null;
        builder.withMethodBody(BRIDGE_PREFIX + action.method(),
                MethodTypeDesc.of(ConstantDescs.CD_void,
                        valued ? new ClassDesc[] {ConstantDescs.CD_String} : new ClassDesc[0]),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_SYNTHETIC, code -> {
                    code.aload(0);
                    if (valued) {
                        code.aload(1);
                        PARSERS.get(action.param()).accept(code);
                    }
                    if (action.hidden()) {
                        code.invokespecial(owner, action.method(), action.owner());
                    } else {
                        code.invokevirtual(owner, action.method(), action.owner());
                    }
                    // An action's return value is not something markup can
                    // receive, so a method that has one is called for its effect
                    // and the value is dropped -- exactly what a method reference
                    // to it in a Runnable would do.
                    var returned = TypeKind.from(action.owner().returnType());
                    if (returned.slotSize() == 2) {
                        code.pop2();
                    } else if (returned != TypeKind.VOID) {
                        code.pop();
                    }
                    code.return_();
                });
    }

    // --- small emitters ------------------------------------------------------

    /// Boxes whatever is on the stack, so it can cross as the `Object` a
    /// listener is handed.
    private static void box(CodeBuilder code, ClassDesc type) {
        var kind = TypeKind.from(type);
        if (kind == TypeKind.REFERENCE) {
            return;
        }
        var boxed = switch (kind) {
            case BOOLEAN -> ConstantDescs.CD_Boolean;
            case BYTE -> ConstantDescs.CD_Byte;
            case CHAR -> ConstantDescs.CD_Character;
            case SHORT -> ConstantDescs.CD_Short;
            case INT -> ConstantDescs.CD_Integer;
            case LONG -> ConstantDescs.CD_Long;
            case FLOAT -> ConstantDescs.CD_Float;
            case DOUBLE -> ConstantDescs.CD_Double;
            default -> throw new WeaveException("cannot box a " + type.displayName());
        };
        code.invokestatic(boxed, "valueOf", MethodTypeDesc.of(boxed, type));
    }

    /// How a valued action's `String` becomes its parameter.
    ///
    /// The one piece of boilerplate every application was writing by hand. A type
    /// that is not here is a build failure naming the method, rather than a
    /// `ClassCastException` three frames into a click.
    private static final Map<ClassDesc, java.util.function.Consumer<CodeBuilder>> PARSERS = Map.of(
            ConstantDescs.CD_String, code -> { },
            ConstantDescs.CD_double, code -> code.invokestatic(ConstantDescs.CD_Double,
                    "parseDouble", MethodTypeDesc.of(ConstantDescs.CD_double, ConstantDescs.CD_String)),
            ConstantDescs.CD_Double, code -> code.invokestatic(ConstantDescs.CD_Double,
                    "valueOf", MethodTypeDesc.of(ConstantDescs.CD_Double, ConstantDescs.CD_String)),
            ConstantDescs.CD_int, code -> code.invokestatic(ConstantDescs.CD_Integer,
                    "parseInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_String)),
            ConstantDescs.CD_Integer, code -> code.invokestatic(ConstantDescs.CD_Integer,
                    "valueOf", MethodTypeDesc.of(ConstantDescs.CD_Integer, ConstantDescs.CD_String)),
            ConstantDescs.CD_boolean, code -> code.invokestatic(ConstantDescs.CD_Boolean,
                    "parseBoolean", MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_String)),
            ConstantDescs.CD_Boolean, code -> code.invokestatic(ConstantDescs.CD_Boolean,
                    "valueOf", MethodTypeDesc.of(ConstantDescs.CD_Boolean, ConstantDescs.CD_String)));

    /// The two attribute mappers this needs, named once.
    ///
    /// `@Model` is `RUNTIME`-retained so [io.github.digitalsmile.goldberry.bind.Models]
    /// can say "annotated but not woven"; `@Bind` and `@Action` are `CLASS`-retained
    /// because only this reads them, and a toolkit that leaves them in the image
    /// for nobody is a toolkit that made the image bigger for nothing.
    private static final class Attributes0 {

        private static final java.lang.classfile.AttributeMapper<RuntimeVisibleAnnotationsAttribute> VISIBLE =
                java.lang.classfile.Attributes.runtimeVisibleAnnotations();

        private static final java.lang.classfile.AttributeMapper<RuntimeInvisibleAnnotationsAttribute> INVISIBLE =
                java.lang.classfile.Attributes.runtimeInvisibleAnnotations();

        private Attributes0() {
        }
    }
}
