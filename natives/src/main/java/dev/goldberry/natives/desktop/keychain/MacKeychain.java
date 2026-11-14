package dev.goldberry.natives.desktop.keychain;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import dev.goldberry.natives.Downcalls;
import dev.goldberry.natives.desktop.keychain.SecretStoreException.Reason;

/// The macOS Keychain, through `Security.framework`'s `SecItem` calls: a
/// generic password per service and account, in the user's login keychain.
///
/// UNVERIFIED: written against Apple's documentation and not yet run, because
/// no lane drives a macOS keychain. The same holds for the access prompt macOS
/// shows when an application other than the one that stored an item reads it.
///
/// What is stored is a `kSecClassGenericPassword` with `kSecAttrService` and
/// `kSecAttrAccount`, which is what Keychain Access shows and what
/// `security find-generic-password -s service -a account` finds.
public final class MacKeychain implements SecretStore {

    // OSStatus values from SecBase.h.
    static final int SUCCESS = 0;
    static final int ITEM_NOT_FOUND = -25300;
    static final int DUPLICATE_ITEM = -25299;
    static final int USER_CANCELED = -128;
    static final int AUTH_FAILED = -25293;
    static final int INTERACTION_NOT_ALLOWED = -25308;
    static final int NOT_AVAILABLE = -25291;

    /// `kCFStringEncodingUTF8`.
    private static final int UTF8 = 0x08000100;

    // The shapes, as constants: recorded for a native image wherever it is
    // built. See Downcalls.describe.
    private static final FunctionDescriptor CREATE_STRING =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT));
    private static final FunctionDescriptor CREATE_DATA =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));
    private static final FunctionDescriptor CREATE_DICTIONARY =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS));
    private static final FunctionDescriptor DATA_LENGTH = Downcalls.describe(FunctionDescriptor.of(JAVA_LONG, ADDRESS));
    private static final FunctionDescriptor P_P = Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final FunctionDescriptor V_P = Downcalls.describe(FunctionDescriptor.ofVoid(ADDRESS));
    private static final FunctionDescriptor I_P = Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final FunctionDescriptor I_PP =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    private final Frameworks lib;

    private MacKeychain(Frameworks lib) {
        this.lib = lib;
    }

    /// The login keychain, or empty off macOS.
    public static Optional<MacKeychain> open() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            return Optional.empty();
        }
        return Frameworks.find().map(MacKeychain::new);
    }

    @Override
    public String name() {
        return "macOS Keychain";
    }

    @Override
    public synchronized void store(String service, String account, String label, byte[] secret, String contentType) {
        try (var cf = new Owned(lib)) {
            var data = cf.data(secret);
            var text = cf.string(label);
            var add = cf.dictionary(
                    query(cf, service, account).plus(List.of(lib.attrLabel, lib.valueData), List.of(text, data)));
            var status = (int) lib.call(lib.itemAdd, add, MemorySegment.NULL);
            if (status == DUPLICATE_ITEM) {
                var change = cf.dictionary(new Pairs(List.of(lib.valueData, lib.attrLabel), List.of(data, text)));
                status = (int) lib.call(lib.itemUpdate, cf.dictionary(query(cf, service, account)), change);
            }
            check("SecItemAdd", status);
        }
    }

    @Override
    @SuppressWarnings("restricted")
    public synchronized Optional<byte[]> load(String service, String account) {
        try (var cf = new Owned(lib);
                var arena = Arena.ofConfined()) {
            var query = cf.dictionary(query(cf, service, account)
                    .plus(List.of(lib.returnData, lib.matchLimit), List.of(lib.booleanTrue, lib.matchLimitOne)));
            var result = arena.allocate(ADDRESS);
            var status = (int) lib.call(lib.itemCopyMatching, query, result);
            if (status == ITEM_NOT_FOUND) {
                return Optional.empty();
            }
            check("SecItemCopyMatching", status);
            var data = cf.own(result.get(ADDRESS, 0));
            var length = (long) lib.call(lib.dataLength, data);
            var bytes = ((MemorySegment) lib.call(lib.dataBytes, data)).reinterpret(length);
            return Optional.of(bytes.toArray(JAVA_BYTE));
        }
    }

    @Override
    public synchronized boolean delete(String service, String account) {
        try (var cf = new Owned(lib)) {
            var status = (int) lib.call(lib.itemDelete, cf.dictionary(query(cf, service, account)));
            if (status == ITEM_NOT_FOUND) {
                return false;
            }
            check("SecItemDelete", status);
            return true;
        }
    }

    @Override
    public void close() {
        // Nothing is held between calls.
    }

    /// The class, service and account every call names.
    private Pairs query(Owned cf, String service, String account) {
        return new Pairs(
                List.of(lib.secClass, lib.attrService, lib.attrAccount),
                List.of(lib.classGenericPassword, cf.string(service), cf.string(account)));
    }

    /// Keys and their values, in step.
    private record Pairs(List<MemorySegment> keys, List<MemorySegment> values) {

        Pairs plus(List<MemorySegment> moreKeys, List<MemorySegment> moreValues) {
            var allKeys = new ArrayList<>(keys);
            allKeys.addAll(moreKeys);
            var allValues = new ArrayList<>(values);
            allValues.addAll(moreValues);
            return new Pairs(allKeys, allValues);
        }
    }

    /// What an `OSStatus` means to an application.
    static void check(String call, int status) {
        if (status == SUCCESS) {
            return;
        }
        var reason = switch (status) {
            case USER_CANCELED, AUTH_FAILED, INTERACTION_NOT_ALLOWED -> Reason.DENIED;
            case NOT_AVAILABLE -> Reason.UNAVAILABLE;
            default -> Reason.FAILED;
        };
        throw new SecretStoreException(reason, call + " answered OSStatus " + status);
    }

    /// Core Foundation objects made for one call, released together.
    private static final class Owned implements AutoCloseable {

        private final Frameworks lib;
        private final List<MemorySegment> owned = new ArrayList<>();

        Owned(Frameworks lib) {
            this.lib = lib;
        }

        MemorySegment own(MemorySegment object) {
            if (object.address() == 0) {
                throw new SecretStoreException(Reason.FAILED, "Core Foundation is out of memory");
            }
            owned.add(object);
            return object;
        }

        MemorySegment string(String text) {
            try (var arena = Arena.ofConfined()) {
                return own(
                        (MemorySegment) lib.call(lib.createString, MemorySegment.NULL, arena.allocateFrom(text), UTF8));
            }
        }

        MemorySegment data(byte[] bytes) {
            try (var arena = Arena.ofConfined()) {
                var copy = arena.allocate(Math.max(1, bytes.length));
                MemorySegment.copy(bytes, 0, copy, JAVA_BYTE, 0, bytes.length);
                var data = own((MemorySegment) lib.call(lib.createData, MemorySegment.NULL, copy, (long) bytes.length));
                copy.fill((byte) 0);
                return data;
            }
        }

        MemorySegment dictionary(Pairs pairs) {
            var keys = pairs.keys();
            var values = pairs.values();
            try (var arena = Arena.ofConfined()) {
                var keyArray = arena.allocate(ADDRESS, Math.max(1, keys.size()));
                var valueArray = arena.allocate(ADDRESS, Math.max(1, values.size()));
                for (var i = 0; i < keys.size(); i++) {
                    keyArray.setAtIndex(ADDRESS, i, keys.get(i));
                    valueArray.setAtIndex(ADDRESS, i, values.get(i));
                }
                return own((MemorySegment) lib.call(
                        lib.createDictionary,
                        MemorySegment.NULL,
                        keyArray,
                        valueArray,
                        (long) keys.size(),
                        lib.keyCallbacks,
                        lib.valueCallbacks));
            }
        }

        @Override
        public void close() {
            for (var object : owned.reversed()) {
                lib.call(lib.release, object);
            }
        }
    }

    /// Core Foundation and Security, bound once.
    private static final class Frameworks {

        final MethodHandle createString;
        final MethodHandle createData;
        final MethodHandle createDictionary;
        final MethodHandle dataLength;
        final MethodHandle dataBytes;
        final MethodHandle release;
        final MethodHandle itemAdd;
        final MethodHandle itemUpdate;
        final MethodHandle itemCopyMatching;
        final MethodHandle itemDelete;

        final MemorySegment keyCallbacks;
        final MemorySegment valueCallbacks;
        final MemorySegment booleanTrue;
        final MemorySegment secClass;
        final MemorySegment classGenericPassword;
        final MemorySegment attrService;
        final MemorySegment attrAccount;
        final MemorySegment attrLabel;
        final MemorySegment valueData;
        final MemorySegment returnData;
        final MemorySegment matchLimit;
        final MemorySegment matchLimitOne;

        private Frameworks(SymbolLookup core, SymbolLookup security) {
            createString = bind(core, "CFStringCreateWithCString", CREATE_STRING);
            createData = bind(core, "CFDataCreate", CREATE_DATA);
            createDictionary = bind(core, "CFDictionaryCreate", CREATE_DICTIONARY);
            dataLength = bind(core, "CFDataGetLength", DATA_LENGTH);
            dataBytes = bind(core, "CFDataGetBytePtr", P_P);
            release = bind(core, "CFRelease", V_P);
            itemAdd = bind(security, "SecItemAdd", I_PP);
            itemUpdate = bind(security, "SecItemUpdate", I_PP);
            itemCopyMatching = bind(security, "SecItemCopyMatching", I_PP);
            itemDelete = bind(security, "SecItemDelete", I_P);
            // The callbacks are structs whose address is passed; the rest are
            // `const CFStringRef` (or `CFBooleanRef`) globals, read once.
            keyCallbacks = symbol(core, "kCFTypeDictionaryKeyCallBacks");
            valueCallbacks = symbol(core, "kCFTypeDictionaryValueCallBacks");
            booleanTrue = constant(core, "kCFBooleanTrue");
            secClass = constant(security, "kSecClass");
            classGenericPassword = constant(security, "kSecClassGenericPassword");
            attrService = constant(security, "kSecAttrService");
            attrAccount = constant(security, "kSecAttrAccount");
            attrLabel = constant(security, "kSecAttrLabel");
            valueData = constant(security, "kSecValueData");
            returnData = constant(security, "kSecReturnData");
            matchLimit = constant(security, "kSecMatchLimit");
            matchLimitOne = constant(security, "kSecMatchLimitOne");
        }

        @SuppressWarnings("restricted")
        static Optional<Frameworks> find() {
            try {
                var core = SymbolLookup.libraryLookup(
                        "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());
                var security = SymbolLookup.libraryLookup(
                        "/System/Library/Frameworks/Security.framework/Security", Arena.global());
                return Optional.of(new Frameworks(core, security));
            } catch (IllegalArgumentException | UnsatisfiedLinkError e) {
                return Optional.empty();
            }
        }

        private static MemorySegment symbol(SymbolLookup lookup, String name) {
            return lookup.find(name).orElseThrow(() -> new UnsatisfiedLinkError("no " + name));
        }

        @SuppressWarnings("restricted")
        private static MemorySegment constant(SymbolLookup lookup, String name) {
            return symbol(lookup, name).reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0);
        }

        private static MethodHandle bind(SymbolLookup lookup, String name, FunctionDescriptor shape) {
            return Downcalls.link(shape).bindTo(symbol(lookup, name));
        }

        /// Calls a bound function; a `void` one answers [Boolean#TRUE].
        Object call(MethodHandle handle, Object... arguments) {
            try {
                var result = handle.invokeWithArguments(arguments);
                return result == null ? Boolean.TRUE : result;
            } catch (Throwable e) {
                throw new SecretStoreException(Reason.FAILED, "a Keychain call failed: " + e);
            }
        }
    }
}
