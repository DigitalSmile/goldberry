# Goldberry on the web — a plan

Written 2026-11-21, before any code. This is W3.11 of
[`gaps/goldberry-gaps-plan.md`](gaps/goldberry-gaps-plan.md), Tessera's T-31:
Goldberry in a browser. The owner decided on 2026-11-08 (D15 there) that this
gets a plan of its own, weighing TeaVM against a GWT-like compiler of the
toolkit's own. It follows `gpu-gaming-plan.md`'s shape: what was found, the
options, the decisions with a status, phases with exit criteria, risks, and a
log.

**This is a plan only. No code is written until the owner has read it and
answered §5.** Each decision becomes an ADR when it is taken, parked in
`docs/snapshot/adr/`.

## 1. Where things stand

Read from the tree on 2026-11-21 (master at `5998561b`). Facts about other
projects were checked on the web the same day; where a source was unclear the
row says so.

### The toolkit

| Area | State |
|------|-------|
| Language | Java 25 throughout: records, sealed interfaces, pattern `switch` (compiled to `SwitchBootstraps` `invokedynamic`), records' `toString`/`equals` (`ObjectMethods` `invokedynamic`), lambdas, string concatenation by `invokedynamic`. A browser compiler has to handle each of these bootstraps |
| Threads | One UI thread. `EventLoop` has a virtual-thread executor for background work; `SingleInstance` starts a virtual thread; `ReentrantLock` is used in the GPU and media paths; Blend2D paints with worker threads when `threadCount` is above 1 |
| The event loop | `EventLoop.run` **blocks**: `while (running) { drain; backend.pumpEvents(sink, timeout); timers; drain }`. A browser's main thread cannot block; it returns to the browser between callbacks |
| Reflection | `bind.runtime.RuntimeBinding` binds `@Bind` fields and `@Action` methods reflectively. `:weaver` already writes the same bindings as direct code for GraalVM native image, and collects `@Markup` widgets into a catalogue at build time. Closed-world compilers need exactly that |
| Services | `ServiceLoader` in 8 files of `:core` (the emoji face, the compositor, and others) |
| Backends | `Backend`/`BackendWindow` (`core` `render`) is an SPI. `Sdl3Backend` and `HeadlessBackend` implement it; `HeadlessBackend` proves a backend without SDL can run the whole toolkit. A backend with no popup windows is allowed: `HostedWindow` falls back to the overlay layer when `createPopup` answers empty |
| Coupling to `:natives` | `:core` imports `dev.goldberry.natives.*` in 36 files: `blend2d` 35 imports, `sdl` 32, `desktop` 11, `yoga` 9, `harfbuzz` 5, `webview` 3, `webp` 2, `platform` 2, `glib` 1. `:widgets` imports none. `:html` 5 (md4c). `:gpu` 56, and `:media` binds its own library in 103 files |
| Native libraries | `libgoldberry` (13 MB on Linux x64): SDL 3.4.18, Blend2D with AsmJit, HarfBuzz, Yoga, libwebp, md4c and the shim, all reached by FFM (`java.lang.foreign`). `libgoldberry-webview` (webview/webview). `libgoldberry-media` (dav1d, libvpx, Opus, Vorbis, FLAC, minimp3, libyuv) |
| Sizes | `goldberry-core` jar 3.4 MB, 2.4 MB of it the five bundled font files. `goldberry-widgets` 1.5 MB, `goldberry-emoji` 2.9 MB, `goldberry-html` 0.26 MB |

**Java has no FFM in a browser** under any of the compilers below. So every
`dev.goldberry.natives` class that `:core` touches needs a second
implementation, and the question is what it calls.

### The C libraries in WebAssembly

| Library | Wasm today |
|---------|------------|
| Blend2D | `BLEND2D_NO_JIT` exists (`CMakeLists.txt`): the reference pipelines, no AsmJit. Its internal headers mention Emscripten and wasm. Whether the no-JIT pipelines give **the same pixels** as the JIT ones is not known (§6) |
| HarfBuzz | Builds with Emscripten; `harfbuzzjs` is the upstream example |
| Yoga | Ships a WebAssembly build (`yoga-src/javascript`, the `yoga-layout` npm package) |
| libwebp, md4c | Plain C99; both build with Emscripten (not tried here) |
| SDL 3 | Has an Emscripten port, but the toolkit would use none of it: the browser backend is the toolkit's own (§3.3) |

### The compilers

| Option | What it is | Status (2026-11) | Fit |
|--------|------------|------------------|-----|
| **TeaVM** | Bytecode to JavaScript or WebAssembly GC, ahead of time, with its own class library | Open source (Apache 2.0). 0.16.0 released 2 October 2026. Java 25 class files since 0.13.0; WebAssembly GC backend since 0.11.0, the old non-GC and WASI backends removed in 0.14.0. Threads are coroutines (`Thread.start`, `sleep`, simple synchronisation), in Wasm GC since 0.13.0. Imported memory and `SharedArrayBuffer` since 0.13.0. **Not confirmed**: `SwitchBootstraps` and `ObjectMethods`, and how much of `java.util.concurrent` and `java.time` its class library covers | Good: reads the bytecode we already make, and its coroutines could even keep `EventLoop.run`'s shape. The class library is the risk |
| **GraalVM Web Image** | Native Image's closed-world compiler with a WebAssembly back end (`--tool:svm-wasm`), over the real JDK library | Experimental. Oracle's docs: "available in Oracle GraalVM 25.1 or later"; whether Community Edition ships it is reported both ways. **Not in the CE 25.2.4, 25.3.4 and 25.4.4.1 installs on this machine** (no `lib/svm/tools`). Needs Binaryen 119+, Wasm GC and exception handling. Oracle's 2025 slides: no threads, networking or graphics yet | Good on the language (it is the JDK) and on reflection (the weaver's metadata already serves Native Image). No threads, and a licence question if it stays Oracle-only |
| J2CL / J2Wasm | Google's Java *source* to Closure JavaScript or Wasm compiler | Used in Google's own products; Bazel-first; its JRE emulation is GWT-like and partial. Java language level not checked | A GWT-like compiler that already exists. Source-based, so every library must come as source |
| CheerpJ | A JVM in the browser, running unmodified jars | 5.0, 2 November 2026: Java 8, 11, 17, 21; Java 25 called both "supported" and "preview" by its own pages. Commercial licence for organisations (not checked in detail). Whether FFM or JNI reach Wasm modules: not known | Fastest to a demo, heaviest to ship (a whole JVM), and least control |
| A compiler of our own | GWT-like: Java source to JS/Wasm | Does not exist | Years of work that the options above have already done. **Not recommended** |
| Remote rendering | No compiler: the application runs on a JVM server with a headless-style backend, frames stream to a thin page, input streams back | The SPI already allows it (`HeadlessBackend`) | A different product: latency, a server per user. Listed so it is weighed, not as the plan's goal |

## 2. What this plan would deliver

1. **A choice of compiler**, made by a spike, not on paper (D1).
2. **The native layer in WebAssembly**: Blend2D, HarfBuzz, Yoga, libwebp and
   md4c built with Emscripten into one module, so a widget paints the same
   pixels in a browser as on the desktop (D2).
3. **A second implementation of the `:natives` types `:core` uses**, over
   that module, with the same Java API, so `:core` and `:widgets` compile
   unchanged (D3).
4. **A browser backend**: one `<canvas>` per window, DOM events in, the frame
   out, popups as overlays, and an event loop the browser drives (D4).
5. **Packaging**: a Gradle task that writes a static site an application can
   host (D7).
6. **A CI lane** that runs a showcase subset in a headless browser against
   the desktop goldens (D8).

**Not in this plan**, so it is not rediscovered:

- `:gpu` (SDL_GPU), `:media` (its codecs), the web view, the tray, the
  keychain, global shortcuts, drag-out, desktop notifications, single-instance
  locking, file-system paths. A browser has its own versions of some of these
  (WebGPU, WebCodecs, the Notification API, the File System Access API); each
  is a plan of its own after this one.
- Accessibility through a mirrored DOM tree. The semantics tree would allow it,
  but the screen-reader bridge stays on hold (ADR-0440).
- Server-side rendering of pages for search engines.

## 3. The pieces in detail

### 3.1 How compiled Java calls the C libraries

FFM's `MethodHandle` downcalls become imports from the Emscripten module:
TeaVM's JSO (`@JSBody`, imports in Wasm GC) or Web Image's JS interop. The
binding classes (`*Calls` holders in `dev.goldberry.natives.*.calls`) are
generated today; a second generator writes their web twins from the same
symbol list (`natives/src/main/cmake/exports/goldberry.symbols`).

Memory is the hard part. Wasm GC objects (the Java heap) and the C libraries'
linear memory are different memories. Whatever crosses has to live in linear
memory: a frame's pixels, glyph buffers, path data. So `ByteBuffer`s and
`MemorySegment`s that `:core` hands to Blend2D must be views of the Emscripten
heap. TeaVM's imported memory (0.13.0) suggests a way; the spike checks it
(§4, phase 0).

### 3.2 Drawing

| Option | Pixels | Work |
|--------|--------|------|
| **(a) Blend2D, HarfBuzz and Yoga in Wasm** | The desktop's, if the no-JIT pipelines match the JIT ones (to be measured) | One Emscripten build; the frame shown with `putImageData` or a WebGL texture upload of the damaged regions |
| (b) Canvas 2D | The browser's: different antialiasing, gradients and text. Every golden differs | Rewrite `Frame`'s painting over `CanvasRenderingContext2D`; text through HarfBuzz outlines or `fillText` (which loses the shaping the editor's caret relies on) |
| (c) WebGPU | Ours, eventually | A new GPU renderer for vector UI: a project the size of the toolkit |

### 3.3 The platform

| Need | Desktop | Browser |
|------|---------|---------|
| Window | SDL window | A `<canvas>` sized in device pixels; `devicePixelRatio` is the `DisplayScale`; a `ResizeObserver` for size changes |
| Present | Window surface or composited | `putImageData` of the frame in linear memory (a copy), or `texSubImage2D` of the damage into a WebGL texture |
| Event loop | `EventLoop.run` blocks in `pumpEvents` | `requestAnimationFrame` for frames, `setTimeout` for timers, DOM callbacks for input (D4) |
| Pointer, wheel, keys | SDL events | Pointer Events, `wheel`, `KeyboardEvent.code` mapped to the toolkit's keys |
| Text input and IME | SDL text input, preedit, `textInputArea` | A hidden `<textarea>` kept under the caret; `compositionstart`/`update`/`end` become `PreeditEvent`s and the commit a `TextEvent` |
| Clipboard | Synchronous read and write by MIME type | Asynchronous and permission-gated. Paste arrives synchronously only inside a `paste` event, so `Ctrl+V` has to come through that event, not the key |
| Fonts | Bundled faces from the jar | The same bytes, fetched as resources (2.4 MB) |
| File dialogs | SDL's | `<input type=file>`, or the File System Access API where there is one |
| Popups | Windows of their own | None: the overlay fallback that already exists |
| Cursors | SDL cursors | CSS `cursor` on the canvas |
| Opening a URL | The desktop's handler | `window.open` |
| Threads | Virtual threads, Blend2D workers | One thread at first (D5) |
| Preferences, theme | Desktop settings | `prefers-color-scheme`, `prefers-reduced-motion`, `localStorage` |

## 4. Phases

### Phase 0 — the spike (proposed)

Three questions, each answered with a written result, before anything else:

1. **What compiles.** `:common` and `:core`'s pure-Java packages (CSS, KDL,
   the widget and element trees, layout's model) compiled with TeaVM 0.16 to
   Wasm GC, and with Web Image if an Oracle GraalVM 25.1+ build can be had.
   Every refusal listed: missing classes, `invokedynamic` bootstraps,
   reflection, `ServiceLoader`.
2. **What it paints.** Blend2D (`BLEND2D_NO_JIT`), HarfBuzz and Yoga built
   with Emscripten; a C test paints one golden scene (a button with text and a
   shadow) and is compared with the desktop golden under the RASTER tolerance.
3. **How memory crosses.** A compiled Java method calls into the module,
   Blend2D paints into a buffer the Java side sees as a `ByteBuffer`, and the
   page shows it.

Exit: §1's unknowns answered, D1 and D2 decided on evidence. Nothing from the
spike ships.

### Phase 1 — `:core` compiles

The refusals from phase 0 fixed in the toolkit where the fix is neutral (a
`switch` a compiler cannot take written another way only if the desktop loses
nothing), and in the compiler's configuration otherwise. The weaver's binding
and catalogue output used instead of `RuntimeBinding`. Exit: `:core` and
`:widgets` compile for the browser target in CI.

### Phase 2 — the native layer

The Emscripten build as a superbuild target of its own (`natives/src/main/wasm`),
the generator for the web twins of the `*Calls` classes, and a `natives-web`
artifact. Exit: `:core`'s text and paint tests that need no window pass in a
headless browser.

### Phase 3 — the browser backend and the event loop

`BrowserBackend` and `BrowserWindow` (§3.3), and D4's event loop. Exit: a
counter application runs in Chromium and Firefox, with text input and IME
(tested with a composition event script), resize and HiDPI.

### Phase 4 — the showcase subset

The showcase's screens that use no module from §2's "not" list, built for the
web. Exit: their goldens taken in a headless browser match the desktop's under
the RASTER tolerance.

### Phase 5 — packaging and the lane

The Gradle plugin's web task (D7) and a CI lane (D8). Exit: the lane runs on
every push; the guide's section is written and parked.

## 5. Decisions for the owner

### D1. The compiler (proposed: decide after the spike)

(a) TeaVM, (b) GraalVM Web Image, (c) J2CL, (d) CheerpJ, (e) a compiler of the
toolkit's own, (f) no compiler: remote rendering instead.

**Recommendation:** spike (a) and (b) on the same slice; choose by what
compiles and runs. TeaVM is the favourite going in: open source, released
regularly, Java 25 class files, Wasm GC and threads as coroutines. Web Image
would win on library coverage if it ships in Community Edition. Decline (e).
Keep (f) as a separate idea, not this plan.

### D2. Drawing (proposed: (a))

(a) Blend2D, HarfBuzz and Yoga in Wasm; (b) Canvas 2D; (c) WebGPU (§3.2).
**Recommendation: (a)**, so a browser paints what the desktop paints and the
goldens serve both. (b) only if the spike's pixels are far off.

### D3. Where the second implementation lives (proposed: (a))

(a) A `natives-web` module with the same public `dev.goldberry.natives.*`
types, implemented over the Emscripten module; `:core` unchanged. (b) An
interface layer inside `:core` over the native types, implemented twice.
**Recommendation: (a).** `:core`'s 36 importing files stay as they are, and the
FFM boundary rule (FFM only in `:natives`) keeps its meaning.

### D4. The event loop (proposed: (a))

(a) `EventLoop` gains a step the backend calls from `requestAnimationFrame` and
DOM callbacks; `run` stays the desktop's loop over the same step. (b) Keep the
blocking `run` and let TeaVM's coroutines suspend it in `pumpEvents`.
**Recommendation: (a).** It works with any compiler; (b) ties the toolkit to
TeaVM.

### D5. Threads (proposed: one thread)

The first version runs on the page's main thread: background work on the same
thread, Blend2D single-threaded. Wasm threads need `SharedArrayBuffer`, which
needs the page served cross-origin isolated (COOP and COEP headers), so they
are an option for later, not a requirement.

### D6. Which modules (proposed)

In: `:common`, `:core`, `:widgets`, `:html` (md4c in the same Wasm module),
`:emoji`. Out: everything in §2's "not" list. A widget that needs an out module
answers as it does on a desktop without the feature: refused with a message.

### D7. Packaging (proposed)

A task in the Gradle plugin that writes a static directory (`index.html`, the
compiled module, the Emscripten module, the fonts) any web server can host.
No server of the toolkit's own.

### D8. Testing (proposed)

The phase 4 goldens compared with the desktop's under the RASTER tolerance, in
a headless Chromium driven by Playwright on CI. Firefox by hand at first:
headless Firefox here is the snap, which has refused beside an open session.

## 6. Risks

- **Pixels.** Blend2D's reference pipelines may not match the JIT ones bit for
  bit. If they differ beyond the tolerance, the web gets goldens of its own.
- **The class library** (TeaVM) or **the missing threads** (Web Image) may
  stop phase 1 at something the spike's slice did not reach.
- **Size.** The bundled fonts alone are 2.4 MB; the compiled toolkit and the
  Wasm C libraries come on top. Not estimated yet; phase 0 measures it.
- **Memory crossing** (§3.1). If buffers cannot be views of linear memory,
  every frame and every shaped line is copied across.
- **Clipboard and IME** differ in kind from the desktop's: asynchronous,
  permission-gated, and driven by a DOM element.
- **The licence of the compiler** (CheerpJ's, and Web Image's while it is
  Oracle-only).
- **Upkeep.** A second binding layer and a second backend kept in step with
  every native change. The generator (§3.1) is what keeps that honest.

## 7. Documents kept in step

- `gaps/goldberry-gaps-plan.md` §8 and §10, and `gaps/answers-tessera.md`
  G-31.
- `docs/snapshot/adr/` for D1–D8 as they are taken.
- `ARCHITECTURE.md`'s module map, if `natives-web` is made.

## 8. Status and log

| Phase | Status |
|-------|--------|
| 0 — the spike | not started; waits for the owner |
| 1 — `:core` compiles | not started |
| 2 — the native layer | not started |
| 3 — the browser backend | not started |
| 4 — the showcase subset | not started |
| 5 — packaging and the lane | not started |

- **2026-11-21.** Plan written from the tree at `5998561b`, with the
  compilers' status checked on the web. What shapes it: FFM is absent in every
  browser compiler, so `:natives` needs a second implementation over a Wasm
  build of the same C libraries; `EventLoop.run` blocks, which a page cannot;
  and the weaver already produces the closed-world bindings that TeaVM and Web
  Image both need. No code until the owner has read this.
