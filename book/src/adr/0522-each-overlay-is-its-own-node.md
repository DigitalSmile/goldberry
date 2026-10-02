# ADR-0522: Each overlay is its own node

- **Status:** Accepted
- **Date:** 2026-10-02
- **Relates to:** [ADR-0100](0100-a-window-has-a-layer-above-its-application.md),
  [ADR-0176](0176-a-dialog-is-a-widget-and-showing-one-is-not.md),
  [ADR-0234](0234-the-overlay-lifecycle-is-a-departure-and-a-phase.md),
  [ADR-0523](0523-a-dialog-takes-itself-off-the-window.md),
  `docs/goldberry-gaps.md` entries 1 and 2

## Context

Deploy Orc reported that its window could not be clicked after the welcome
dialog. "Get started" removed the welcome dialog and showed the sign-in dialog
in the same turn, and from then on every press landed on an invisible scrim.

The cause was reconciliation. `WindowRoot.children()` returned the content and
then each overlay's widget, and the reconciler matched those children like any
others: by class and key. Every dialog that arrives without an id is given the
id `dialog`, and an id doubles as a key. So the sign-in dialog matched the
element the welcome dialog had left, and adopted its `DialogState`. That
state's `Departure` was already over, so the scrim drew nothing and still filled
the window.

The router showed it plainly: one overlay, `Dialog(Sign in, key=dialog)`, no
modal, and `dialog-scrim` at the centre of the window.

The application's workaround was `d.id(Dialogs.DEFAULT_ID).keyed(new Object())`.
That works only because the id is set first: `Dialogs.show` applied the default
id with `Attributes.id`, which also sets the key, so a caller's `keyed(…)` on an
anonymous dialog was silently replaced (entry 2).

Nothing about this is particular to dialogs. Any two overlays of the same class
and key, two toasters or a `message` shown twice, were one node to the
reconciler. Matching by widget is the right rule inside an application's tree,
where a key is the author's statement of identity. On the overlay layer the
author has already stated identity, by holding an `Overlay` handle.

## Decision

**The overlay layer matches overlays by their handle.**

- `WindowRoot.children()` wraps each overlay's widget in an `OverlaySlot` keyed by
  the `Overlay` instance. `Overlay` has identity rather than value equality, so
  each handle is a new key and an element never passes from one overlay to
  another.
- The slot is neither styled nor painted. The renderer passes its child's boxes
  straight to the root, and no selector sees it, so layout, paint and the
  existing stylesheets are unchanged.
- `WindowRoot.overlayFor`, which places each box in its overlay's corner, reads
  the overlay from the slot it finds above the box instead of counting children
  by index.
- `WindowRoot.overlayOf(BuildContext)` gives a widget on the layer its own handle.
  A dialog is a value built before its handle existed, and ADR-0523 needs it to
  reach that handle.
- `Dialogs.show` keeps a caller's key when it gives an anonymous dialog
  `DEFAULT_ID`. `Attributes.id` is unchanged: an id that is not also a key would
  stop matching a focused control across a rebuild everywhere else in a tree.

## Consequences

- Removing one dialog and showing another in the same turn shows the new one,
  with fresh state, and the centre of the window hits its panel. The same holds
  for every overlay. `DialogLayerTest` and `OverlayLayerTest` cover both.
- Deploy Orc can drop the `keyed(new Object())` workaround. A `keyed(…)` on an
  anonymous dialog now survives `Dialogs.show`, though overlay identity no longer
  depends on it.
- A child selector from `window-root` to an overlay's widget no longer matches,
  because the slot is an element between them. None exists in the toolkit's
  sheets, and a stateful overlay such as a dialog already had its own element
  in that position.
- `DEFAULT_ID` stays a constant, because golden images depend on it. Two
  anonymous dialogs at once still share a focus name. That is the rare case
  ADR-0176 accepted, and the nodes no longer share state.
