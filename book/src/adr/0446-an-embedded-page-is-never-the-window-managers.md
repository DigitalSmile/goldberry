# 446. An embedded page is never the window manager's

Date: 2026-09-20

## Status

Accepted. Fixes a defect in
[ADR-0442](0442-a-page-is-a-child-window-where-the-window-system-allows-one.md)'s
embedding path.

## Context

Open a page in the showcase and the desktop grows an extra entry — in the dock,
in the taskbar, in the alt-tab list — that cannot be clicked, cannot be raised
and cannot be closed. One per page opened, for the life of the session.

It is the page's own window, and the sequence that produces it is
`goldberry_webview_embed_into`:

```c
gtk_window_set_decorated(GTK_WINDOW(window), FALSE);
gtk_window_resize(GTK_WINDOW(window), width, height);
gtk_widget_show_all(window);        // <- mapped as a TOPLEVEL, here
gtk_widget_realize(window);
...
XReparentWindow(display, child, parent, x, y);
```

`webview_create` makes an ordinary `GtkWindow`, which is a child of the root
window. `gtk_widget_show_all` maps it, and for that instant the window manager
adopts it: it is a managed client, so it goes in the window list, the pager and
the switcher. `XReparentWindow` then takes it off the root — and what the shell
is left holding is an entry for a window that has become somebody else's child.
Every EWMH operation on it is now meaningless, which is exactly what the user
sees.

The show **cannot simply be moved after the reparent**. The comment above it was
earned:

> `gtk_widget_realize` alone creates the shell's window without mapping the
> WebKit widget inside it, and the result is a correctly positioned rectangle
> with nothing in it — which is exactly what the first attempt produced.

So the window must be mapped while it is still a toplevel. The question is
whether the window manager has to notice.

## Decision

**It does not, and it is told so twice.**

```c
gtk_window_set_skip_taskbar_hint(GTK_WINDOW(window), TRUE);
gtk_window_set_skip_pager_hint(GTK_WINDOW(window), TRUE);
gtk_widget_realize(window);                     // create, without mapping
gdk_window_set_override_redirect(gdk, TRUE);    // and do not manage it
gtk_widget_show_all(window);                    // now map it
```

The hints are the EWMH way to say it; `override_redirect` is the X way, and it
is the stronger of the two — an override-redirect window is one the window
manager is told not to manage **at all**, which is the literal truth here: this
window is about to stop being a toplevel.

Both are set because they fail differently. A window manager that ignores
`_NET_WM_STATE_SKIP_TASKBAR` still honours `override_redirect`, because
honouring it is not optional; and the hints remain correct and readable for
anything that inspects the window between the map and the reparent.

**One reordering**, and it is required rather than tidy: `override_redirect` is
an attribute of the X window, so the X window must exist before it can be set,
and it must be set before the map or the window manager has already seen it.
`gtk_widget_realize` creates the window without mapping it, which is exactly the
gap that was needed. The `show_all` stays, and stays before the reparent, for
the reason the old comment gives.

## Alternatives considered

**The hints alone.** Probably sufficient on GNOME and KDE, and it is the
conservative change. Rejected as the *only* measure because a hint is a request:
a window manager is free to ignore it, and the window really is not one for it
to manage. Setting only the hints would leave the defect latent on whichever
desktop ignores them.

**`gtk_window_set_type_hint(GDK_WINDOW_TYPE_HINT_UTILITY)`** or `DOCK`. Still a
managed window, so still in the window manager's client list — it changes how it
is decorated and stacked, not whether it exists as far as the shell is
concerned.

**Reparent before mapping.** The one fix that would remove the toplevel moment
entirely, and the one the existing comment rules out: realize alone leaves the
WebKit widget inside unmapped, and the result is an empty rectangle. That was
the first attempt at this function.

**`gtk_window_set_type_hint` plus unmapping and remapping after the reparent.**
More X round trips, and it reintroduces the empty-rectangle question for no
benefit over the flag.

## Consequences

**The ghost is gone**, and on every window manager rather than on the ones that
honour hints.

**An override-redirect window gets no window-manager services**, and this one
wanted none of them:

| Lost | Why it does not matter |
|---|---|
| Decorations | Already off — `gtk_window_set_decorated(FALSE)` on the line above |
| Focus from the WM | The page's keyboard comes from being a child of a window that has focus, which is ADR-0442's arrangement and unchanged |
| Placement | It is positioned by `XReparentWindow` on the next line and by `XMoveResizeWindow` after that |

**It is Linux-only**, like the embedding itself. The Windows and macOS branches
of `goldberry_webview_create_embedded` are still unwritten, and each will have
its own version of this: `WS_EX_TOOLWINDOW` and `SetParent` on Win32, and on
Cocoa the question does not arise because a `WKWebView` is an `NSView` and was
never a window.

**Webview ABI 5**, shared with
[ADR-0445](0445-a-page-is-not-shown-before-it-can-be-seen.md) — the two landed
together.

**No automated test.** Whether a window appears in a dock is a fact about the
running desktop's shell, and there is nothing in this repository that can ask
it. It is checked by opening a page and looking, which is what found it.
