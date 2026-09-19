# 437. A focus name resolves in the composite the keyboard is in

Date: 2026-09-19

## Status

Accepted. Closes the residue
[ADR-0212](0212-a-list-owns-the-models-a-tree-borrowed.md) left behind and
`TODO.md` had been carrying since: "a row's focus name still collides between two
unnamed lists".

## Context

`host.focus(id)` takes a name that is **global to the window**
([ADR-0176](0176-a-dialog-is-a-widget-and-showing-one-is-not.md)). The router
walks the focus root and takes the first element whose `id` matches. That is the
right namespace for the names an application writes down, because an id *is* the
document's name for a node and a stylesheet resolves it the same way.

The names in this entry are not written down by anybody. A `list` builds a row
per item and has to be able to put the keyboard on one by name — `Home`, `End`,
type-to-select — so it **manufactures** one: the item's identity, prefixed with
the list's own `id`. The prefix was a guess that an application had named the
list, which is a good guess (a screen with two lists on it needs to tell them
apart for the stylesheet anyway) and not a rule. Two lists, neither named, over
items with the same identity both call their rows `list-Iceland`, and `End` in
the second one moved the focus into the first.

`tree` had it worse and the entry says so. A tree names its rows `tree-<node>`
with no prefix at all, so two trees sharing a node id answered each other's keys
**whether or not the application named them**. There was no spelling of a tree
that avoided it.

The entry names the fix precisely: *a focus name that is relative to a subtree,
which the router has no notion of.* That is the right diagnosis. Manufacturing a
name into somebody else's global namespace and hoping is the bug, and lengthening
the prefix is haggling with it.

## Decision

**`focusById` resolves a name in the composites the focused node is inside,
innermost first, and in the window only if none of them holds it.**

```java
private @Nullable Element findNamed(String id) {
    for (var scope = enclosingScope(focused); scope != null; scope = enclosingScope(scope)) {
        var within = findById(scope, id);
        if (within != null) {
            return within;
        }
    }
    return findById(focusRoot, id);
}
```

### The subtree is the focus scope, and the focus scope was already there

The first question was whether `focus-scope` — a traversal boundary since
[ADR-0073](0073-a-composite-is-one-tab-stop.md), with an axis since
[ADR-0078](0078-a-focus-scope-has-an-axis.md) — is also the right *naming*
boundary. It is, and not by convenience.

A composite is one Tab stop whose items the arrow keys move between. The
elements a widget manufactures names for are exactly the elements the keyboard
has to move among, because that is **why** it manufactures them: a row gets a
name so that `End` can reach it. So the set of nodes a composite names and the
set of nodes a composite roves over are the same set, described twice. `list`,
`tree`, `menu`, `tabs` and the rest already say they are scopes, every one of
them because it needed arrow keys — which is not a coincidence, it is the same
fact.

So nothing new is declared. No namespace attribute, no explicit scope widget, no
id forced onto a list, and no widget in the catalog changed except its comments.
The notion the router was missing turns out to be one it already had, used for
one thing and not the other.

Outwards scope by scope rather than the nearest one only, so a composite nested
in another answers before the one containing it, and a name the inner one does
not hold is still found in the outer one before the window is asked.

### The anchor is the focus, and that is not a guess about the caller

Resolution is anchored on the focused node. That reads like a guess at who is
asking, and it is not: **every caller of a manufactured name is a key pressed on
a row.** `Home` and `End` on a list row, type-to-select on a list or a tree row,
a tree's `Left` walking to its parent. There is no fifth. The focused node is
where the key landed, so it is the asking subtree by construction rather than by
approximation.

The cost is real and worth naming: **resolution now depends on state outside the
call.** The same string can reach two different elements depending on where the
keyboard is. That is only true of a duplicated id, which had no defined answer
before — "the first in document order" was an implementation fact, not a promise
— but it is a new coupling, and it is the kind that is invisible in a stack
trace. A test that asks for a duplicated name with nothing focused sees document
order; the same call after a click on a row sees something else. That is the
price of not changing the published spelling, and the next paragraph is what it
buys.

### The published spelling does not change and no caller moves

`Host#focus(String, boolean)` keeps its signature, keeps its meaning for every
name that names one node, and keeps its meaning for a caller outside every
composite. There is no new overload to reach for, nothing deprecated and nothing
to migrate, because there is nothing to opt into: an application's own
`host.focus("save")` means today what it meant last week.

What an application gives up is the ability to say "the second list's Iceland"
from outside — which it never had, and which it gets by naming the second list,
which is what it should do and what its stylesheet already wants.

### What this was weighed against

**A second, scoped `Host.focus`,** taking the asking widget's subtree as a
handle. It is the honest reading of the entry and it is four more published
methods — `Host`, the launcher, `Popup`, the router — plus a handle each widget
has to bank across rebuilds and pass at every call site, to arrive at the subtree
the focused node already names for free. It also leaves the old spelling doing
the old wrong thing for everything that does not move to the new one, so the bug
survives in the API rather than in the code.

**Minting a unique prefix for an unnamed list** — `list7-Iceland` from a counter.
The cheapest of the three, and it puts a counter in a CSS id: a row's name would
depend on how many lists had been constructed before it, so building an unrelated
screen first would rename it, and no stylesheet could address it. It fixes a
collision by making the names unaddressable, which is a worse namespace rather
than a smaller one. It also churns every assertion that names a row — and those
are in `select`'s tests as well as the list's and the tree's.

### `tree` needed no fix of its own, and is fixed more than `list` is

Both are composites; both now resolve a row name in their own. A list's prefix
already settled the named case, so what this adds there is the unnamed one; a
tree had no prefix, so what it adds there is *both*. `tree` was deliberately left
without the prefix rather than given one to match: a prefix is the guess this ADR
is replacing, and adding one now would churn every row assertion in the tree's
and `select`'s tests to buy a disambiguation that has already been bought.

### The residue, written down rather than fixed

A **virtualized** list's row that has not been built yet is not inside its own
scope to be found. `ListState#reach` widens the window and retries by name across
a frame ([ADR-0213](0213-a-virtual-list-is-two-spacers-and-a-window.md)), and on
an attempt where the row does not exist the window fallback can still hand back
another list's row. It takes two lists, both unnamed, both virtualized, over the
same identities, on the frame before the rebuild lands; the steady state is
right, and the retry is bounded at two. Closing it properly means the list
telling the router which subtree it means — the rejected design above — and it is
not worth that API for this corner.

A request made with the focus nowhere resolves in document order, unchanged. That
is not residue: with no keyboard anywhere there is no subtree for a name to be
relative to.

## Consequences

- `PointerRouter#focusById` goes through a new `findNamed`, which is the only
  code change in `:core`. `findById` is untouched and still what the fallback
  uses.
- `ListState#rowId` keeps the list's `id` as a prefix. It is no longer what keeps
  `End` in one list out of another; it stays for what it was also doing, which is
  giving a row a name that means something from *outside* every list — to a
  stylesheet, or to an application focusing one.
- `FocusNameScopeTest` in `:core` states the rule over bare widgets, the way
  `FocusTrapTest` states the trap: a duplicated name resolves in the keyboard's
  composite, in document order when the keyboard is in none, in the window when
  the composite does not hold it, and innermost-first when composites nest.
- `ListFocusScopeTest` and `TreeFocusScopeTest` in `:widgets` are the entry's own
  case, end to end — two unnamed lists and two unnamed trees over the same
  identities in one window, a real router, a real key, and an assertion about
  *which element* the focus is on afterwards rather than about what the widget
  asked for. Both also assert that the first one is not simply always losing,
  because a rule that preferred the later widget would pass every other case here
  and be just as wrong.
