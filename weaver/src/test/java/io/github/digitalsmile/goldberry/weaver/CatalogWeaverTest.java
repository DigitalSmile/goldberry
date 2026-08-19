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
            ClassDesc.of("io.github.digitalsmile.goldberry.widgets.WidgetCatalog");

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
