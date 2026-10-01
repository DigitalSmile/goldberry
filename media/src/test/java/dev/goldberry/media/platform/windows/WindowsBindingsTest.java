package dev.goldberry.media.platform.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The bindings' foreign-call surface, which the native-image metadata is made
/// from. None of it needs Windows: linking a descriptor opens no library.
@DisplayName("WindowsBindings")
class WindowsBindingsTest {

    @Test
    @DisplayName("records a descriptor for every binding, each shape once")
    void everyBinding() {
        var downcalls = WindowsBindings.downcalls();
        assertEquals(downcalls.size(), new HashSet<>(downcalls).size());
        // Spot checks of shapes only one binding has.
        assertTrue(downcalls.contains(FunctionDescriptor.of(
                JAVA_INT, Guid.LAYOUT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS))); // MFTEnumEx
        assertTrue(downcalls.contains(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS))); // IMFTransform::ProcessOutput
        assertTrue(downcalls.contains(
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG))); // IMFTransform::ProcessMessage
        assertTrue(downcalls.contains(
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS))); // GetBlob
        assertTrue(downcalls.contains(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT))); // CoInitializeEx
        assertTrue(downcalls.contains(FunctionDescriptor.ofVoid(ADDRESS))); // CoTaskMemFree
        assertTrue(WindowsBindings.UPCALLS.isEmpty());
    }

    @Test
    @DisplayName("lists every class in the package that links a downcall")
    void bindingsAreComplete() throws IOException, URISyntaxException {
        var linking = packageClasses()
                .filter(type ->
                        Arrays.stream(type.getDeclaredFields()).anyMatch(WindowsBindingsTest::isDowncallConstant))
                .toList();
        assertEquals(new HashSet<>(WindowsBindings.BINDINGS), new HashSet<>(linking));
    }

    @Test
    @DisplayName("declares no callback descriptors: the MFTs are synchronous")
    void noUpcalls() throws IOException, URISyntaxException {
        var declared = packageClasses()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == FunctionDescriptor.class)
                .toList();
        assertEquals(WindowsBindings.UPCALLS.size(), declared.size());
    }

    @Test
    @DisplayName("every unbound handle is a private static final constant")
    void constants() throws IOException, URISyntaxException {
        var handles = packageClasses()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(field -> field.getType() == MethodHandle.class)
                .toList();
        assertTrue(handles.size() > 20, handles.size() + " handles");
        for (var field : handles) {
            var modifiers = field.getModifiers();
            assertTrue(
                    Modifier.isPrivate(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers),
                    field.toString());
            assertTrue(field.getName().startsWith("FD_"), field.toString());
        }
    }

    @Test
    @DisplayName("the vtable slots are the SDK headers' order")
    void slots() {
        assertEquals(0, Com.IUnknown_QueryInterface);
        assertEquals(1, Com.IUnknown_AddRef);
        assertEquals(2, Com.IUnknown_Release);
        assertEquals(7, MfAttributes.IMFAttributes_GetUINT32);
        assertEquals(8, MfAttributes.IMFAttributes_GetUINT64);
        assertEquals(10, MfAttributes.IMFAttributes_GetGUID);
        assertEquals(14, MfAttributes.IMFAttributes_GetBlobSize);
        assertEquals(15, MfAttributes.IMFAttributes_GetBlob);
        assertEquals(21, MfAttributes.IMFAttributes_SetUINT32);
        assertEquals(22, MfAttributes.IMFAttributes_SetUINT64);
        assertEquals(24, MfAttributes.IMFAttributes_SetGUID);
        assertEquals(26, MfAttributes.IMFAttributes_SetBlob);
        assertEquals(33, MfTransform.IMFActivate_ActivateObject);
        assertEquals(35, MfSample.IMFSample_GetSampleTime);
        assertEquals(36, MfSample.IMFSample_SetSampleTime);
        assertEquals(41, MfSample.IMFSample_ConvertToContiguousBuffer);
        assertEquals(42, MfSample.IMFSample_AddBuffer);
        assertEquals(3, MfBuffer.IMFMediaBuffer_Lock);
        assertEquals(4, MfBuffer.IMFMediaBuffer_Unlock);
        assertEquals(5, MfBuffer.IMFMediaBuffer_GetCurrentLength);
        assertEquals(6, MfBuffer.IMFMediaBuffer_SetCurrentLength);
        assertEquals(3, MfBuffer.IMF2DBuffer_Lock2D);
        assertEquals(4, MfBuffer.IMF2DBuffer_Unlock2D);
        assertEquals(7, MfTransform.IMFTransform_GetOutputStreamInfo);
        assertEquals(14, MfTransform.IMFTransform_GetOutputAvailableType);
        assertEquals(15, MfTransform.IMFTransform_SetInputType);
        assertEquals(16, MfTransform.IMFTransform_SetOutputType);
        assertEquals(18, MfTransform.IMFTransform_GetOutputCurrentType);
        assertEquals(23, MfTransform.IMFTransform_ProcessMessage);
        assertEquals(24, MfTransform.IMFTransform_ProcessInput);
        assertEquals(25, MfTransform.IMFTransform_ProcessOutput);
    }

    /// Whether `field` is a binding's unbound handle, `FD_…`.
    private static boolean isDowncallConstant(Field field) {
        return Modifier.isStatic(field.getModifiers())
                && field.getType() == MethodHandle.class
                && field.getName().startsWith("FD_");
    }

    /// Every top-level and nested class compiled into this package's main output,
    /// loaded but not initialised. The main class directory is found through a
    /// main class's own code source, so the test classes of the same package are
    /// not in it.
    private static Stream<Class<?>> packageClasses() throws IOException, URISyntaxException {
        var packageName = WindowsBindings.class.getPackageName();
        var root = Path.of(WindowsBindings.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        var directory = root.resolve(packageName.replace('.', '/'));
        try (var files = Files.list(directory)) {
            return files
                    .map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".class"))
                    .map(name -> packageName + "." + name.substring(0, name.length() - ".class".length()))
                    .map(WindowsBindingsTest::load)
                    .toList()
                    .stream();
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, WindowsBindingsTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }
}
