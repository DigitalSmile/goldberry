# 346. A link is a word, and the desktop opens the rest

Date: 2026-09-17

## Status

Accepted. Builds `docs/core-widgets.md` §2's `link`, and binds `SDL_OpenURL`
to open its `href` through.

## Context

§2's `link` was unblocked when `text-decoration` landed (ADR-0321) and stayed
unwritten. It is "text that does something": `action=` for in-app navigation,
`href=` for an external target "opened through the platform", `visited` as the
application's word, focusable and in the Tab order, `Enter` activates, an
underline on hover, and — the sentence with the decision in it — "external
links carry a trailing 12px `external-link` icon and their accessible name
says so, because 'opens outside this window' is not something a colour can
convey".

Two things stood in the way. Nothing in the toolkit could open a URL: the
deep-link direction is not Goldberry's (ADR-0291), but the outbound one is
one SDL call on every platform and was not bound. And an `Icon` is a value a
widget must not own (ADR-0043) — every icon in the catalog is the
application's — while this widget has to make one of its own.

## Decision

**`link` is a stateful word; its `href` goes to the desktop through
`Host#openExternal`, which is `SDL_OpenURL`; and the icon is the state's.**

- **`SDL_OpenURL`** joins the export list as an optional symbol, like the
  theme call (ADR-0322): a `libgoldberry` built before it must keep opening
  windows, and "the platform would not" is an answer a link already has to
  handle. `Backend.openUrl` defaults to false; the SDL backend hands the string
  over; the headless one records it and answers true. `Host.openExternal` is
  the door, with a default of false so the test hosts keep compiling. It is a
  request, not a result: SDL returns once the desktop has been asked, and on
  Linux it hands even an empty string to `xdg-open` and says yes — so there is
  no "refuses nonsense" test, because it would open nonsense.
- **`Link`** is `Widget.Stateful` building a `LinkText` (the CSS type,
  ADR-0109). The state holds the host and, for an external link, the
  `external-link` icon at 12px, made on the first build and closed on dispose
  — the one lifetime an icon can have inside the toolkit. A press runs the
  action, opens the target, or both; a link with neither is a word in the link
  ink that takes no focus. A refused open is logged, not thrown: a link that
  silently did nothing is the one failure a user cannot tell from a missed
  click.
- **`Enter` activates and `Space` does not**, which is §2's word and every
  browser's: `Space` scrolls a page. `visited` and `external` are classes; the
  underline on hover is the stylesheet's, through `text-decoration`.
- **It is block-level.** A row of a word and, sometimes, an icon, so the icon
  sits beside the word in the same ink without joining its text run. A link
  mid-sentence is `text`'s `span class="link"`, as §2 says.
- **`Role.BUTTON`**, for `crumb`'s reason: `Role` has no link, a role nothing
  can consume is a value written for a bridge that does not exist, and
  "something you press to make it happen" is true of a link. The accessible
  name of an external one ends with "opens outside this window".

## Consequences

- `link` is in `Primitives.builtInTypes()`, beside `text`, and the parity and
  immutability tests hand both a word, since neither exists without one.
- An application can open a URL: `host.openExternal("https://…")`.
- `libgoldberry` has to be rebuilt to export the symbol; a stale one answers
  false and the link logs it.
- The showcase's Basic screen has three links, one of them external.
