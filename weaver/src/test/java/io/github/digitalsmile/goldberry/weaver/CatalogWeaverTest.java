package io.github.digitalsmile.goldberry.weaver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The half of ADR-0131 that `:widgets` cannot test.
///
/// Its test source set runs on the **class path**, so the `META-INF/services`
/// file is what the service loader reads there and the `provides` patched into
/// `module-info.class` is never exercised. That patch is what makes the scheme
/// work on the module path — where the module system ignores `META-INF/services`
/// entirely — so it is checked here, structurally, against a descriptor built for
/// the purpose.
@DisplayName("the catalog weaver")
class CatalogWeaverTest {

    private static final ClassDesc CD_CATALOG =
            ClassDesc.of("io.github.digitalsmile.goldberry.widgets.markup.WidgetCatalog");

    /// A module descriptor with one `requires` and one `exports`, so the patch
    /// has something to preserve.
    private static byte[] moduleInfo() {
        return ClassFile.of().buildModule(ModuleAttribute.of(
                ModuleDesc.of("com.example.widgets"), builder -> builder
                        .requires(ModuleDesc.of("java.base"), 0, null)
                        .exports(PackageDesc.of("com.example.widgets"), 0)));
    }

    private static ModuleAttribute moduleOf(byte[] bytes) {
        return ClassFile.of().parse(bytes).findAttribute(Attributes.module()).orElseThrow();
    }

    @Nested
    @DisplayName("patching a module descriptor")
    class Patching {

        @Test
        @DisplayName("adds the provides a named module needs")
        void addsProvides() {
            var catalog = ClassDesc.of("com.example.widgets.GoldberryCatalog");

            var patched = CatalogWeaver.provideCatalog(moduleInfo(), catalog);

            assertNotNull(patched);
            var module = moduleOf(patched);
            assertEquals(1, module.provides().size());
            assertEquals(CD_CATALOG, module.provides().getFirst().provides().asSymbol());
            assertEquals(List.of(catalog), module.provides().getFirst().providesWith()
                    .stream().map(entry -> entry.asSymbol()).toList());
        }

        @Test
        @DisplayName("and keeps everything else the module said")
        void keepsTheRest() {
            // A patch that dropped `requires java.base` would produce a module
            // that does not resolve, and the failure would be at launch rather
            // than here.
            var patched = CatalogWeaver.provideCatalog(
                    moduleInfo(), ClassDesc.of("com.example.widgets.GoldberryCatalog"));
            var module = moduleOf(patched);

            assertEquals("com.example.widgets", module.moduleName().name().stringValue());
            assertTrue(module.requires().stream().anyMatch(
                    r -> r.requires().name().stringValue().equals("java.base")));
            assertEquals(1, module.exports().size());
        }

        @Test
        @DisplayName("twice is a no-op, because the build rewrites in place")
        void idempotent() {
            var catalog = ClassDesc.of("com.example.widgets.GoldberryCatalog");
            var once = CatalogWeaver.provideCatalog(moduleInfo(), catalog);

            assertNull(CatalogWeaver.provideCatalog(once, catalog));
        }

        @Test
        @DisplayName("an ordinary class is not a module descriptor")
        void notAModule() {
            assertNull(CatalogWeaver.provideCatalog(
                    Woven.bytesOf(CatalogWeaverTest.class),
                    ClassDesc.of("com.example.Catalog")));
        }
    }

    @Nested
    @DisplayName("where the catalog goes")
    class Placement {

        @Test
        @DisplayName("the package every widget shares")
        void rootPackage() {
            assertEquals("com.example.widgets", CatalogWeaver.rootPackage(List.of(
                    ClassDesc.of("com.example.widgets.controls.button.Button"),
                    ClassDesc.of("com.example.widgets.menu.Menu"),
                    ClassDesc.of("com.example.widgets.text.Text"))));
        }

        @Test
        @DisplayName("which is the package itself when they all sit in one")
        void onePackage() {
            assertEquals("com.example", CatalogWeaver.rootPackage(List.of(
                    ClassDesc.of("com.example.A"), ClassDesc.of("com.example.B"))));
        }

        @Test
        @DisplayName("and the default package when they share nothing")
        void nothingShared() {
            assertEquals("", CatalogWeaver.rootPackage(List.of(
                    ClassDesc.of("com.example.A"), ClassDesc.of("org.other.B"))));
        }

        @Test
        @DisplayName("never a package the module has no class in, because two modules"
                + " sharing one package is a LayerInstantiationException")
        void notAPackageSomebodyElseOwns() {
            // `:html`'s own shape: two widget trees whose only shared prefix is the
            // package `:core` keeps `Goldberry` and `Host` in. Writing the catalog
            // there would have been a module path that does not start at all, and no
            // class-path test could have seen it (ADR-0298).
            var widgets = List.of(
                    ClassDesc.of("io.github.digitalsmile.goldberry.html.view.HtmlView"),
                    ClassDesc.of("io.github.digitalsmile.goldberry.markdown.view.MarkdownView"));
            var owned = Set.of(
                    "io.github.digitalsmile.goldberry.html",
                    "io.github.digitalsmile.goldberry.html.view",
                    "io.github.digitalsmile.goldberry.markdown",
                    "io.github.digitalsmile.goldberry.markdown.view");

            assertEquals("io.github.digitalsmile.goldberry.html", CatalogWeaver.rootPackage(widgets, owned));
        }

        @Test
        @DisplayName("the shared package itself when the module does own it")
        void theSharedOneWhenItIsOwned() {
            var widgets = List.of(
                    ClassDesc.of("com.example.widgets.controls.button.Button"),
                    ClassDesc.of("com.example.widgets.menu.Menu"));

            assertEquals("com.example.widgets",
                    CatalogWeaver.rootPackage(widgets, Set.of("com.example.widgets", "com.example.widgets.menu")));
        }

        @Test
        @DisplayName("and the first widget's own package when nothing between is owned")
        void asFarDownAsItHasTo() {
            var widgets = List.of(ClassDesc.of("com.example.deep.down.A"), ClassDesc.of("com.other.B"));

            assertEquals("com.example.deep.down",
                    CatalogWeaver.rootPackage(widgets, Set.of("com.example.deep.down", "com.other")));
        }

        @Test
        @DisplayName("and the shared package when the module's own packages are unknown")
        void nothingKnownIsTheOldAnswer() {
            // The one-argument form is still what a caller with no inventory gets, so
            // that this change cannot alter a build that has not been told about it.
            var widgets = List.of(ClassDesc.of("com.example.a.A"), ClassDesc.of("com.example.b.B"));

            assertEquals("com.example", CatalogWeaver.rootPackage(widgets, Set.of()));
        }
    }

    @Nested
    @DisplayName("the order names are registered in")
    class Order {

        @Test
        @DisplayName("is sorted, so the class file does not depend on the file system")
        void sorted() {
            // A build that produced a different class file on a different machine
            // would break every reproducibility claim the repository makes -- and
            // the list is what an unknown-node error prints, so hash order would
            // be a worse thing to read than alphabetical.
            var widgets = new java.util.LinkedHashMap<String, ClassDesc>();
            widgets.put("toggle", ClassDesc.of("a.Toggle"));
            widgets.put("button", ClassDesc.of("a.Button"));
            widgets.put("radio-group", ClassDesc.of("a.RadioGroup"));

            assertEquals(List.of("button", "radio-group", "toggle"),
                    List.copyOf(CatalogWeaver.sorted(widgets).keySet()));
        }

        @Test
        @DisplayName("and a module with no widgets gets no catalog at all")
        void noWidgets() {
            assertNull(CatalogWeaver.catalog("com.example", Map.of()));
        }
    }
}
