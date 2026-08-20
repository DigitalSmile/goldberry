package io.github.digitalsmile.goldberry.weaver;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.ModuleProvideInfo;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Collects a module's `@Markup` widgets into one generated [WidgetCatalog].
///
/// The registration half of ADR-0131. Where [ModelWeaver] *transforms* a class
/// the author wrote, this **writes a new one** — there is nothing to transform,
/// because the thing being produced is a list and a list has no natural home in
/// somebody's source file.
///
/// What it emits, for a module whose widgets are annotated `@Markup("button")`
/// and so on:
///
/// ```java
/// public final class GoldberryCatalog implements WidgetCatalog {
///     public void register(Inflatable.Catalog into) {
///         into.add("button", Button::inflate)
///             .add("checkbox", Checkbox::inflate)
///             …;
///     }
/// }
/// ```
///
/// plus a `provides io.…widgets.WidgetCatalog with …GoldberryCatalog` patched
/// into the module's own `module-info.class`, and a `META-INF/services` entry for
/// when the same jar is used on the class path. So a module that ships widgets is
/// found by an application that never names it, and neither the widget author nor
/// the module author writes a line of registration.
///
/// Each `Button::inflate` is an `invokedynamic` bootstrapped by
/// `LambdaMetafactory`, exactly like an `@Action`'s call site — one shape, one
/// mechanism, and one that a closed world resolves when it builds the image
/// (ADR-0127).
public final class CatalogWeaver {

    private static final String WIDGETS = "io.github.digitalsmile.goldberry.widgets.";

    private static final ClassDesc CD_MARKUP = ClassDesc.of(WIDGETS + "Markup");
    private static final ClassDesc CD_CATALOG = ClassDesc.of(WIDGETS + "WidgetCatalog");
    private static final ClassDesc CD_REGISTRAR = ClassDesc.of(WIDGETS + "Inflatable$Catalog");
    private static final ClassDesc CD_INFLATABLE = ClassDesc.of(WIDGETS + "Inflatable");
    private static final ClassDesc CD_KDL_NODE = ClassDesc.of("io.github.digitalsmile.goldberry.kdl.KdlNode");
    private static final ClassDesc CD_WIDGET = ClassDesc.of("io.github.digitalsmile.goldberry.widget.Widget");
    private static final ClassDesc CD_WIRING = ClassDesc.of(WIDGETS + "Wiring");
    private static final ClassDesc CD_LMF = ClassDesc.of("java.lang.invoke.LambdaMetafactory");
    private static final ClassDesc CD_LIST = ClassDesc.of("java.util.List");

    /// The name the generated class always gets, inside the module's own root
    /// package.
    ///
    /// Fixed rather than derived, so the `provides` clause and the services file
    /// can be written without either of them having to agree with a naming rule
    /// somebody could change.
    public static final String CATALOG_CLASS = "GoldberryCatalog";

    /// The descriptor of the factory method every `@Markup` class must have.
    static final MethodTypeDesc INFLATE =
            MethodTypeDesc.of(CD_WIDGET, CD_KDL_NODE, CD_LIST, CD_WIRING);

    private CatalogWeaver() {
    }

    /// The node name `bytes` claims, or null if it is not a `@Markup` widget.
    ///
    /// @throws WeaveException if it is one and has no usable `inflate`
    public static String markupName(byte[] bytes) {
        var model = ClassFile.of().parse(bytes);
        var name = ModelWeaver.annotationValue(model, CD_MARKUP);
        if (name == null) {
            return null;
        }
        var owner = model.thisClass().asSymbol().displayName();
        if (name.isBlank()) {
            throw new WeaveException("@Markup on " + owner + " has an empty name;"
                    + " a document has to be able to write it");
        }
        // Java cannot say "and it must have this static method" in an annotation,
        // so the check lands here rather than at the first document that uses the
        // node.
        var found = model.methods().stream()
                .anyMatch(m -> m.methodName().equalsString("inflate")
                        && m.methodTypeSymbol().equals(INFLATE)
                        && m.flags().has(java.lang.reflect.AccessFlag.STATIC)
                        && m.flags().has(java.lang.reflect.AccessFlag.PUBLIC));
        if (!found) {
            throw new WeaveException("@Markup(\"" + name + "\") on " + owner + " needs a"
                    + " `public static Widget inflate(KdlNode, List<Widget>, Wiring)`;"
                    + " without one there is nothing for the node name to build");
        }
        return name;
    }

    /// The catalog class for a module, or null when the module has no widgets.
    ///
    /// @param inPackage where to put it — the module's root package
    /// @param widgets   node name to the class that builds it, in the order they
    ///                  should be registered
    public static byte[] catalog(String inPackage, Map<String, ClassDesc> widgets) {
        if (widgets.isEmpty()) {
            return null;
        }
        var self = ClassDesc.of(inPackage.isEmpty() ? CATALOG_CLASS : inPackage + "." + CATALOG_CLASS);
        return ClassFile.of().build(self, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SYNTHETIC);
            builder.withInterfaceSymbols(CD_CATALOG);
            builder.withSuperclass(ConstantDescs.CD_Object);

            // ServiceLoader needs a public no-argument constructor and calls it
            // by name, which is the one place this whole scheme touches
            // reflection -- and it is the place GraalVM's own ServiceLoader
            // support already resolves at image build time.
            builder.withMethodBody(ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void),
                    ClassFile.ACC_PUBLIC, code -> code
                            .aload(0)
                            .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME,
                                    MethodTypeDesc.of(ConstantDescs.CD_void))
                            .return_());

            builder.withMethodBody("register", MethodTypeDesc.of(ConstantDescs.CD_void, CD_REGISTRAR),
                    ClassFile.ACC_PUBLIC, code -> {
                        code.aload(1);
                        for (var entry : widgets.entrySet()) {
                            code.loadConstant(entry.getKey())
                                    .invokedynamic(factory(entry.getValue()))
                                    .invokevirtual(CD_REGISTRAR, "add",
                                            MethodTypeDesc.of(CD_REGISTRAR,
                                                    ConstantDescs.CD_String, CD_INFLATABLE));
                        }
                        // `add` chains, so the last one leaves a Catalog behind.
                        code.pop().return_();
                    });
        });
    }

    /// `Button::inflate`, as the call site javac would have written for it.
    private static DynamicCallSiteDesc factory(ClassDesc widget) {
        var bootstrap = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, CD_LMF,
                "metafactory", MethodTypeDesc.of(ConstantDescs.CD_CallSite,
                        ConstantDescs.CD_MethodHandles_Lookup, ConstantDescs.CD_String,
                        ConstantDescs.CD_MethodType, ConstantDescs.CD_MethodType,
                        ConstantDescs.CD_MethodHandle, ConstantDescs.CD_MethodType));
        var implementation = MethodHandleDesc.ofMethod(
                DirectMethodHandleDesc.Kind.STATIC, widget, "inflate", INFLATE);
        return DynamicCallSiteDesc.of(bootstrap, "inflate",
                MethodTypeDesc.of(CD_INFLATABLE), INFLATE, implementation, INFLATE);
    }

    /// Adds `provides WidgetCatalog with <catalog>` to a compiled `module-info`.
    ///
    /// Needed because a **named** module publishes services through its
    /// descriptor and not through `META-INF/services`, which the module system
    /// ignores for one. The alternative was asking every widget module to declare
    /// a `provides` naming a class that does not exist until after javac has run,
    /// which does not compile.
    ///
    /// @return the patched descriptor, or null when it already provides one
    public static byte[] provideCatalog(byte[] moduleInfo, ClassDesc catalog) {
        var classFile = ClassFile.of();
        var model = classFile.parse(moduleInfo);
        var module = model.findAttribute(java.lang.classfile.Attributes.module()).orElse(null);
        if (module == null) {
            return null;
        }
        for (var existing : module.provides()) {
            if (existing.provides().asSymbol().equals(CD_CATALOG)) {
                return null;
            }
        }
        var provides = new ArrayList<>(module.provides());
        provides.add(ModuleProvideInfo.of(CD_CATALOG, List.of(catalog)));

        var patched = ModuleAttribute.of(module.moduleName(), module.moduleFlagsMask(),
                module.moduleVersion().orElse(null), module.requires(), module.exports(),
                module.opens(), module.uses(), provides);

        return classFile.transformClass(model, ClassTransform.dropping(
                        element -> element instanceof ModuleAttribute)
                .andThen(ClassTransform.endHandler(builder -> builder.with(patched))));
    }

    /// The module's root package — the longest prefix every widget shares.
    ///
    /// Which is where the catalog goes, so a module that keeps its widgets in
    /// `…widgets.controls.button` and `…widgets.menu` gets one catalog in
    /// `…widgets` rather than a choice nobody made.
    public static String rootPackage(List<ClassDesc> widgets) {
        if (widgets.isEmpty()) {
            return "";
        }
        var prefix = widgets.getFirst().packageName().split("\\.");
        var length = prefix.length;
        for (var widget : widgets) {
            var parts = widget.packageName().split("\\.");
            var shared = 0;
            while (shared < length && shared < parts.length && prefix[shared].equals(parts[shared])) {
                shared++;
            }
            length = shared;
        }
        return String.join(".", List.of(prefix).subList(0, length));
    }

    /// The names a module contributes, in a stable order.
    ///
    /// Sorted by node name rather than left in file-system order, because the
    /// list is what an unknown-node error prints and because a build that
    /// produced a different class file on a different machine would break every
    /// reproducibility claim the repository makes.
    public static Map<String, ClassDesc> sorted(Map<String, ClassDesc> widgets) {
        var ordered = new ArrayList<>(widgets.keySet());
        ordered.sort(null);
        var result = new LinkedHashMap<String, ClassDesc>();
        for (var name : ordered) {
            result.put(name, widgets.get(name));
        }
        return result;
    }

    /// The annotation reader [ModelWeaver] already has, reused so both halves
    /// read one the same way -- and out of the same two attributes, whichever the
    /// retention put it in.
    static String annotationValue(java.lang.classfile.AttributedElement member, ClassDesc wanted) {
        return ModelWeaver.annotationValue(member, wanted);
    }
}
