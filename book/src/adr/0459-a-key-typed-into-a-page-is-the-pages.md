# 459. A key typed into a page is the page's

Date: 2026-09-23

## Status

Accepted. Amends [ADR-0458](0458-a-page-on-macos-is-a-view-not-a-window.md),
whose consequences recorded that on macOS a keystroke typed into a page reached
the application as well, and left it unsolved. Writes the Windows row of
[ADR-0442](0442-a-page-is-a-child-window-where-the-window-system-allows-one.md),
which ADR-0458 left as the only platform that said "not implemented".

The Windows half is **written and unverified**. It was written on macOS and has
not been compiled or run. Everything it calls is documented Win32 or WebView2,
and webview.h's own Win32 backend is the model.

## Context

### Keystrokes on macOS

SDL3's `NSApplication` subclass overrides `sendEvent:`. It hands every key event
to `Cocoa_HandleKeyEvent` first, and then to `[super sendEvent:]`, which is what
delivers the event to the window's first responder. So with the page's
`WKWebView` focused, one keystroke:

- types into the page, and
- arrives in SDL's queue as `KEY_DOWN`/`KEY_UP`, and as `TEXT_INPUT` when text
  input is on. `Cocoa_HandleKeyEvent` calls `interpretKeyEvents:` on SDL's field
  editor itself, whether or not that editor is the first responder.

Typing in a page's search box would therefore also fire the application's
shortcuts, move its focus with Tab, and type into whichever Goldberry field
last had the focus.

The opposite direction was broken too. SDL's content view does not accept first
responder, so clicking back onto the Goldberry frame left the page as first
responder. The keys kept going to the page.

X11 and Windows have neither problem. On X11 the focused page is its own X
window, and the server sends its keys there and nowhere else. On Windows the
focused window is WebView2's, owned by the browser process, and its keys never
enter SDL's thread queue.

### Windows

webview.h's Win32 backend, given a parent `HWND`, does exactly what embedding
needs. It creates a `WS_CHILD` window, `webview_widget`, inside the parent,
hosts the WebView2 controller in it, and never touches the parent's window
procedure or content. Unlike Cocoa, nothing is replaced. What it leaves to an
owner is COM, clipping, placement and load state.

## Decision

### The backend drops what was typed into a page

Two exports, `goldberry_webview_has_focus` and `goldberry_webview_blur`, bumping
the shim's ABI from 6 to 7. `BackendWebView` gains `hasKeyboardFocus()` and
`blur()`, both defaulting to "cannot say / nothing to do".

- **Dropping.** `Sdl3Backend` drops `KEY_DOWN`, `KEY_UP`, `TEXT_INPUT` and
  `TEXT_EDITING` for a window while one of its embedded pages holds the
  keyboard. All four, not only presses: a release without its press is a stuck
  key, and text is the same keystrokes a second time. A window with no page
  costs one map lookup per key.
- **Giving the keyboard back.** Every `MOUSE_BUTTON_DOWN` that SDL reports
  blurs that window's pages. A press on a page belongs to the page and never
  reaches SDL — the `WKWebView` is the hit-tested view, and SDL hears buttons
  only through the responder chain — so every press SDL sees landed outside the
  page. No hit-testing is needed to know the user clicked back into the
  application.
- **What "blur" means on macOS.** Focus goes back to SDL's text-input field
  editor, an `SDL3TranslatorResponder` subview of the content view, if there is
  one; otherwise to the window. SDL makes that editor first responder only when
  it *adds* it, and does not add it again while text input is already on. So a
  Goldberry field that was focused before the page was clicked would otherwise
  lose IME composition until it was unfocused and refocused.

`has_focus` asks whether the key window's first responder is the `WKWebView` or
a descendant of it. WebKit keeps a private content view that is the real
responder. On Windows it asks whether the foreground thread's focus `HWND` is
the child or inside it, using `GetGUIThreadInfo` rather than `GetFocus`, because
the focused window belongs to the browser process. X11 answers no, which is
true there.

### Windows embeds by giving the engine the parent

`webview_create(debug, sdlHwnd)`, and then:

| Left to the owner | Done here |
|---|---|
| COM | `CoInitializeEx(STA)`. SDL's `WIN_VideoInit` already did it; one more reference is taken so a page does not depend on that, and it is released on destroy. `RPC_E_CHANGED_MODE` — a multi-threaded apartment — refuses the page, because WebView2 would. |
| Clipping | `WS_CLIPCHILDREN` on SDL's window. SDL presents by `BitBlt` into the window's DC, which without it paints over the child. `WS_CLIPSIBLINGS` on the child. |
| Placement | `SetWindowPos(HWND_TOP, …, SWP_SHOWWINDOW)` in client pixels, which is what Java already sends. The widget's own `WM_SIZE` passes the new size to the controller. `NotifyParentWindowPositionChanged`, because WebView2 caches screen positions for its popups. |
| Load state | A COM object implementing both `NavigationStarting` and `NavigationCompleted` handlers, unregistered before the engine is destroyed. WebView2 has nothing synchronous to ask. |

A parked page is moved off the parent's top-left corner, where the parent clips
it, exactly as on X11. The IIDs come from `__uuidof` against WebView2.h's own
declarations rather than GUIDs typed into this file: the Windows build is MSVC
under both generators ([ADR-0454](0454-a-symbol-list-belongs-in-a-file-on-every-platform.md)),
and a typo in a copied GUID fails silently.

The widget's Windows notice is now the engine one — the log says why — instead
of "not implemented".

## Alternatives considered

- **Filter in the launcher or the focus manager, not the backend.** The backend
  is the layer that knows which window a page is in and holds the list, and
  what is being corrected is a platform quirk in how SDL receives events. The
  layers above never have to know it happened.
- **Hit-test presses against the page's rectangle to decide on blur.** Not
  needed: SDL never receives a press on the page, so "SDL saw it" already means
  "outside".
- **Make SDL's content view first responder on blur.** It does not accept first
  responder, so the call would be refused and the page would keep the keyboard.
- **`SetParent` a toplevel webview window on Windows, as X11 reparents.** It
  would show and then move a top-level window, with the flash and taskbar
  ghost ADR-0446 fought on X11. webview.h's own child window has neither.

## Consequences

- **A key typed into a page on macOS is the page's alone**, and clicking the
  application takes the keyboard back. Covered by unit tests of the backend's
  decision, not by typing: this environment could not send keystrokes to the
  showcase. The showcase was run with the ABI-7 library, and the page opened,
  loaded and was destroyed as before.
- **The application sees nothing of what is typed into a page, including
  shortcuts.** A Cmd-W typed while the page has the keyboard goes to WebKit,
  which ignores it, not to the application. That is what a focused browser
  control does elsewhere. An application that wants a global shortcut to work
  over a page needs a menu item, because AppKit routes key equivalents through
  the menu bar before any view.
- **The macOS blur knows the name of one of SDL's private classes.** If SDL
  renames `SDL3TranslatorResponder`, the lookup finds nothing, focus goes to the
  window, and the only loss is IME composition in a field that was already
  focused before the page was clicked.
- **Windows is unverified until a Windows build runs it.** The likeliest
  surprises are
  - whether SDL's `BitBlt` honours `WS_CLIPCHILDREN` through its cached DC, and
  - whether a WebView2 controller created inside a message loop that SDL pumps
    raises its navigation events without `webview_run`.

  A page that shows white and never takes down its spinner is the second one.
- **A stale ABI-6 library is refused by name.** Unlike ADR-0458, this change is
  one the ABI check catches.
