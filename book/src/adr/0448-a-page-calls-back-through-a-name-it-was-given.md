# 448. A page calls back through a name it was given

Date: 2026-09-20

## Status

Accepted.

## Context

Everything between Goldberry and an embedded page has run one way. The
application can navigate it, size it, park it and evaluate script in it; the
page can do nothing but be looked at. That makes `web-view` a viewer, and the
thing applications actually embed a page **for** — a form, a chart, a document
editor that has to hand its result back — was not expressible.

`webview/webview` has the mechanism and nothing was bound to it. `webview_bind`
makes a name a global JavaScript function, injecting the glue at document start;
calling it in the page returns a **promise**, and the handler is given a request
id, the arguments as a JSON array, and a `void *` it was bound with.
`webview_return` resolves or rejects that promise.

## Decision

**A callback is declared on the page value**, beside where it says what to load:

```java
WebPage.of(url).on("save", arguments -> { store(arguments); return "true"; })
```
```js
const ok = await window.save({title: "note"});
```

On the **value** rather than on the widget, because a page value is the whole
description of a page and the standalone window form (`WebViews.open`) is
described by the same value — so it gets callbacks for nothing instead of
needing a second mechanism. Bindings travel `WebPage` → `WebViewSpec` →
`WebViewEngine`, which applies them **before** the content, since the glue runs
at document start.

### What crosses is text, and it is not parsed

`window.save({title: "note"})` arrives as the string `[{"title":"note"}]` — the
arguments as a JSON array, exactly as the engine hands them over. The toolkit
does not parse it. Goldberry ships no JSON reader and binding one for the sake
of a callback would put a dependency in `:core` that every application pays for
and few would use; a page that wants to send one value sends one value, and a
page that wants structure brings a reader. The showcase's demo takes the quotes
off a one-string array and says in as many words that it is not a parser.

### Throwing rejects the promise

The page is `await`ing. A handler that cannot answer has still told the page
something, and the exception's message is what its `catch` receives — whereas a
handler that swallowed its failure would leave a promise pending for ever, which
is a hang with no error anywhere.

### One upcall stub for every binding of every page

An upcall stub is executable memory in a global arena, so one per bound name
would be one that is never freed per name. There is a single stub and a registry
keyed by the number handed over as the callback's `void *arg` — `SdlFileDialogs`'
idiom, a counter and a map and `MemorySegment.ofAddress`. A page drops its own
entries when it closes.

### On the UI thread

The engine's loop is drained by `Webview.pump()`, which the frame loop calls, so
a handler runs on the thread that paints. It may read and write state and call
`setState`, like every other widget callback. It must not block: the page's
promise and the next frame are both waiting on it.

## Alternatives considered

**On the `WebView` widget** — `new WebView(page).on(...)`. Keeps `WebPage` a
value with no behaviour in it, which is a real argument, and leaves the window
form unable to have callbacks at all without a second mechanism for the same
thing.

**A handle handed back after opening**, which the application calls `bind()` on.
The familiar shape, and it fights the rule that a widget is a value rebuilt every
frame: there would be a moment before the handle exists, an order to get right,
and a registration to unwind.

**`webview_init` as well**, injecting script at document start. Useful and not
needed by anything: a page that wants a helper defines one, and the export list's
rule is that a symbol nothing binds is dead weight.

**Parsing the JSON.** See above — a reader is a dependency, and the one shape
this crossing has is "a string the page chose".

## Consequences

**`web-view` is a two-way widget.** An embedded page can hand results back, and
the showcase demonstrates both directions: a resolved promise and a rejected one,
from a document that greets Java on load so the mechanism shows itself before
anything is pressed.

**`WebPage` and `WebViewSpec` gain a component**, with the seven-argument
constructor kept so nothing that existed had to change.

**Two pages are now rarely `equals`.** A handler is a lambda and lambdas have no
value equality, so two rebuilds of the same `on(...)` expression produce unequal
pages. That is why [ADR-0449]'s `showsSameAs` exists: a widget navigating on
`equals` would reload the page on every frame.

**Bindings are read when the page opens.** A handler added to a page that is
already open does not take effect, because the glue has already been injected.
The javadoc says so; the alternative is rebinding on every rebuild, which is a
platform call per frame for a case nobody has.

**Webview ABI 6**, two exports. It is also the first ABI bump caught by its own
guard: the shim was raised to 6 and the Java constant was not, and the page
simply refused to open with a message naming both numbers — which is what that
check is for.

**A page can now run application code.** The binding is the application's own
and is reached only by the document it loaded, but an application that binds a
handler and then navigates to a page it does not control has handed that page a
call into itself. Worth saying once, here.

[ADR-0449]: 0449-a-page-follows-the-value-that-describes-it.md
