package io.github.digitalsmile.goldberry.natives.webview.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// The eighteen functions `libgoldberry-webview` exports.
///
/// None of them is `webview/webview`'s own, for the reason md4c's are not: the
/// upstream C API returns a `webview_error_t` in an out-parameter shape this
/// project would have to model, and two of the calls here have no upstream at all
/// — [Pump], which is a GLib iteration on Linux and nothing anywhere else, and
/// [Abi], which is how a stale library is caught before it is trusted
/// ([ADR-0441]).
///
/// See [Downcalls] for why each handle is a `static final` constant and why these
/// live in a package of their own.
public record WebviewCalls(
        Create create,
        Destroy destroy,
        Navigate navigate,
        SetHtml setHtml,
        SetTitle setTitle,
        SetSize setSize,
        Eval eval,
        Pump pump,
        Abi abi,
        GtkConflict gtkConflict,
        CreateEmbedded createEmbedded,
        SetBounds setBounds,
        LoadState loadState,
        Bind bind,
        Return answer,
        CanEmbed canEmbed,
        HasFocus hasFocus,
        Blur blur) {

    /// Binds **only** the ABI probe.
    ///
    /// Separate from [#bind] because the order matters and was wrong first: a
    /// library that disagrees about the ABI is by definition one whose other
    /// symbols may be absent, so binding them all and *then* asking the version
    /// makes the check unreachable — `Downcalls.symbol` throws an
    /// `UnsatisfiedLinkError` on the first missing name and the version is never
    /// read. That is exactly what a stale `libgoldberry-webview.so` left in an
    /// install directory did: it failed with "does not export
    /// goldberry_webview_gtk_conflict" instead of "this library is ABI 1 and
    /// this build binds ABI 2".
    ///
    /// @param lookup the loaded `libgoldberry-webview`
    public static Abi bindAbi(SymbolLookup lookup) {
        return new Abi(lookup);
    }

    /// Binds every function above.
    ///
    /// Call [#bindAbi] first and check it. This throws if any symbol is missing,
    /// which after an agreed ABI means a broken library rather than an old one.
    ///
    /// @param lookup the loaded `libgoldberry-webview`
    public static WebviewCalls bind(SymbolLookup lookup) {
        return new WebviewCalls(
                new Create(lookup),
                new Destroy(lookup),
                new Navigate(lookup),
                new SetHtml(lookup),
                new SetTitle(lookup),
                new SetSize(lookup),
                new Eval(lookup),
                new Pump(lookup),
                new Abi(lookup),
                new GtkConflict(lookup),
                new CreateEmbedded(lookup),
                new SetBounds(lookup),
                new LoadState(lookup),
                new Bind(lookup),
                new Return(lookup),
                new CanEmbed(lookup),
                new HasFocus(lookup),
                new Blur(lookup));
    }

    /// Creates a page and its window.
    ///
    /// `void* goldberry_webview_create(int debug)`
    public static final class Create {

        private static final MethodHandle FD_goldberry_webview_create =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        Create(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_create");
        }

        /// Calls `goldberry_webview_create`.
        ///
        /// @param debug whether to enable the engine's own inspector
        /// @return the page handle, or [MemorySegment#NULL] when the engine would
        ///         not start — no display, or a WebKit too old
        public MemorySegment call(int debug) {
            try {
                return (MemorySegment) FD_goldberry_webview_create.invokeExact(address, debug);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_create", t);
            }
        }
    }

    /// Closes the window and frees the page.
    ///
    /// `void goldberry_webview_destroy(void* w)`
    public static final class Destroy {

        private static final MethodHandle FD_goldberry_webview_destroy =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        Destroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_destroy");
        }

        public void call(MemorySegment webview) {
            try {
                FD_goldberry_webview_destroy.invokeExact(address, webview);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_destroy", t);
            }
        }
    }

    /// Points the page at a URL.
    ///
    /// `int goldberry_webview_navigate(void* w, const char* url)`
    public static final class Navigate {

        private static final MethodHandle FD_goldberry_webview_navigate =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        Navigate(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_navigate");
        }

        /// @return 0 on success, non-zero on failure
        public int call(MemorySegment webview, MemorySegment url) {
            try {
                return (int) FD_goldberry_webview_navigate.invokeExact(address, webview, url);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_navigate", t);
            }
        }
    }

    /// Replaces the page's contents with a document.
    ///
    /// `int goldberry_webview_set_html(void* w, const char* html)`
    public static final class SetHtml {

        private static final MethodHandle FD_goldberry_webview_set_html =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetHtml(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_set_html");
        }

        public int call(MemorySegment webview, MemorySegment html) {
            try {
                return (int) FD_goldberry_webview_set_html.invokeExact(address, webview, html);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_set_html", t);
            }
        }
    }

    /// Sets the window's title.
    ///
    /// `int goldberry_webview_set_title(void* w, const char* title)`
    public static final class SetTitle {

        private static final MethodHandle FD_goldberry_webview_set_title =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetTitle(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_set_title");
        }

        public int call(MemorySegment webview, MemorySegment title) {
            try {
                return (int) FD_goldberry_webview_set_title.invokeExact(address, webview, title);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_set_title", t);
            }
        }
    }

    /// Sizes the window.
    ///
    /// `int goldberry_webview_set_size(void* w, int width, int height, int hint)`
    public static final class SetSize {

        private static final MethodHandle FD_goldberry_webview_set_size =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        SetSize(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_set_size");
        }

        public int call(MemorySegment webview, int width, int height, int hint) {
            try {
                return (int) FD_goldberry_webview_set_size.invokeExact(address, webview, width, height, hint);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_set_size", t);
            }
        }
    }

    /// Runs script in the page.
    ///
    /// `int goldberry_webview_eval(void* w, const char* js)`
    public static final class Eval {

        private static final MethodHandle FD_goldberry_webview_eval =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        Eval(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_eval");
        }

        public int call(MemorySegment webview, MemorySegment script) {
            try {
                return (int) FD_goldberry_webview_eval.invokeExact(address, webview, script);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_eval", t);
            }
        }
    }

    /// Gives the engine's own event loop a turn.
    ///
    /// `void goldberry_webview_pump(void)`
    ///
    /// Process-wide rather than per page, because what it drives is: GLib has one
    /// main context and draining it services every page at once.
    public static final class Pump {

        private static final MethodHandle FD_goldberry_webview_pump = Downcalls.link(FunctionDescriptor.ofVoid());

        private final MemorySegment address;

        Pump(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_pump");
        }

        public void call() {
            try {
                FD_goldberry_webview_pump.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_pump", t);
            }
        }
    }

    /// The shim's ABI number.
    ///
    /// `int goldberry_webview_abi(void)`
    ///
    /// This library is the one that may be loaded from somewhere the rest of the
    /// build did not put it — a distribution package, a `-D` override — so it is
    /// the one that has to say which contract it implements.
    public static final class Abi {

        private static final MethodHandle FD_goldberry_webview_abi = Downcalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        Abi(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_abi");
        }

        public int call() {
            try {
                return (int) FD_goldberry_webview_abi.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_abi", t);
            }
        }
    }

    /// Whether a GTK of the other major is already in this process.
    ///
    /// `int goldberry_webview_gtk_conflict(void)`
    ///
    /// Exists so a failed [Create] can say *which* failure it was. Two GTK majors
    /// in one process is a segfault in `gtk_init_check`, not an error — and a
    /// Goldberry process is already a GTK 3 one whenever it has shown a tray
    /// icon, because SDL's Linux tray is libayatana-appindicator ([ADR-0441]).
    /// Always 0 on macOS and Windows.
    public static final class GtkConflict {

        private static final MethodHandle FD_goldberry_webview_gtk_conflict =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        GtkConflict(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_gtk_conflict");
        }

        public int call() {
            try {
                return (int) FD_goldberry_webview_gtk_conflict.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_gtk_conflict", t);
            }
        }
    }

    /// Opens a page already inside another window.
    ///
    /// `void* goldberry_webview_create_embedded(int debug, long long parent, int kind, int x, int y, int w, int h)`
    ///
    /// Create-and-embed in one call rather than two, because GDK picks its
    /// backend during `gtk_init` and prefers Wayland where both are available —
    /// so a page created first gets a `wl_surface` that cannot be reparented,
    /// even when SDL is on X11 ([ADR-0442]).
    public static final class CreateEmbedded {

        private static final MethodHandle FD_goldberry_webview_create_embedded = Downcalls.link(
                FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        CreateEmbedded(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_create_embedded");
        }

        /// @return the page, or [MemorySegment#NULL] when it could not be opened
        ///         embedded — a Wayland session, a GTK already up on the wrong
        ///         backend, or an engine that would not start
        public MemorySegment call(int debug, long parent, int kind, int x, int y, int width, int height) {
            try {
                return (MemorySegment) FD_goldberry_webview_create_embedded.invokeExact(
                        address, debug, parent, kind, x, y, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_create_embedded", t);
            }
        }
    }

    /// Moves and resizes an embedded page within its parent.
    ///
    /// `int goldberry_webview_set_bounds(void* w, int x, int y, int width, int height)`
    public static final class SetBounds {

        private static final MethodHandle FD_goldberry_webview_set_bounds =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        SetBounds(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_set_bounds");
        }

        public int call(MemorySegment webview, int x, int y, int width, int height) {
            try {
                return (int) FD_goldberry_webview_set_bounds.invokeExact(address, webview, x, y, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_set_bounds", t);
            }
        }
    }

    /// How far through loading a page is.
    ///
    /// `int goldberry_webview_load_state(void* w)`
    ///
    /// -1 unknown, 0 not started, 1 loading, 2 finished —
    /// [io.github.digitalsmile.goldberry.natives.webview.LoadState] names them.
    ///
    /// The one call in this record a *widget* makes every frame, and it is a
    /// poll for that reason: the alternative is WebKit's `load-changed` signal,
    /// which would need an upcall stub whose lifetime outlives the Java object
    /// that owns it and a crossing from GLib's thread. The widget is already
    /// calling [SetBounds] once a frame from its painter, so this rides along.
    public static final class LoadState {

        private static final MethodHandle FD_goldberry_webview_load_state =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        LoadState(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_load_state");
        }

        public int call(MemorySegment webview) {
            try {
                return (int) FD_goldberry_webview_load_state.invokeExact(address, webview);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_load_state", t);
            }
        }
    }

    /// Makes a name a global JavaScript function the page can call.
    ///
    /// ```c
    /// int goldberry_webview_bind(void *w, const char *name,
    ///                            void (*fn)(const char *id, const char *req, void *arg), void *arg);
    /// ```
    ///
    /// The **third** upcall family in this module after Yoga's measure and SDL's
    /// tray, and the first whose callback comes from a page's own script. The
    /// glue is injected by the engine: `window.<name>(...)` in the page returns
    /// a promise, and the handler is given a request id, the arguments as a JSON
    /// array, and the `arg` the binding was made with.
    ///
    /// Bind **before** navigating — the glue runs at document start.
    public static final class Bind {

        private static final MethodHandle FD_goldberry_webview_bind =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        Bind(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_bind");
        }

        /// @param name     the JS function's name, a C string
        /// @param callback an upcall stub of the handler's shape
        /// @param userData handed back to the handler untouched — how one stub
        ///        serves every binding in the process
        /// @return 0 on success; non-zero for a duplicate name
        public int call(MemorySegment webview, MemorySegment name, MemorySegment callback, MemorySegment userData) {
            try {
                return (int) FD_goldberry_webview_bind.invokeExact(address, webview, name, callback, userData);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_bind", t);
            }
        }
    }

    /// Answers one call to a bound function.
    ///
    /// `int goldberry_webview_return(void* w, const char* id, int status, const char* result)`
    ///
    /// Zero resolves the page's promise with `result`, which must be a valid
    /// JSON value or an empty string for `undefined`; anything else rejects with
    /// it. **Something must answer**, or the promise is pending for ever.
    ///
    /// Named `Return` for the C function and reached as `answer()`, because
    /// `return` is not a name a Java method can have.
    public static final class Return {

        private static final MethodHandle FD_goldberry_webview_return =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        Return(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_return");
        }

        public int call(MemorySegment webview, MemorySegment id, int status, MemorySegment result) {
            try {
                return (int) FD_goldberry_webview_return.invokeExact(address, webview, id, status, result);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_return", t);
            }
        }
    }

    /// Whether this process can embed a page, asked **after** the engine is up.
    ///
    /// `int goldberry_webview_can_embed(void)`
    public static final class CanEmbed {

        private static final MethodHandle FD_goldberry_webview_can_embed =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        CanEmbed(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_can_embed");
        }

        public int call() {
            try {
                return (int) FD_goldberry_webview_can_embed.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_can_embed", t);
            }
        }
    }

    /// Whether the keyboard focus is inside a page.
    ///
    /// `int goldberry_webview_has_focus(void* w)`
    ///
    /// 1 or 0. Only macOS can answer 1 in a way that matters: it is the one
    /// platform where SDL also receives the keys a focused page is typed
    /// ([ADR-0459]).
    public static final class HasFocus {

        private static final MethodHandle FD_goldberry_webview_has_focus =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        HasFocus(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_has_focus");
        }

        public int call(MemorySegment webview) {
            try {
                return (int) FD_goldberry_webview_has_focus.invokeExact(address, webview);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_has_focus", t);
            }
        }
    }

    /// Gives the keyboard back from a page to the window it is embedded in.
    ///
    /// `void goldberry_webview_blur(void* w)`
    public static final class Blur {

        private static final MethodHandle FD_goldberry_webview_blur =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        Blur(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_webview_blur");
        }

        public void call(MemorySegment webview) {
            try {
                FD_goldberry_webview_blur.invokeExact(address, webview);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_webview_blur", t);
            }
        }
    }
}
