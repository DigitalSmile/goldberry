# 449. A page follows the value that describes it

Date: 2026-09-20

## Status

Accepted.

## Context

A `web-view` loaded whatever its `WebPage` named when it opened, and then
nothing could change it. An address bar — a field, a Go button, a page that
follows — was not expressible, and neither was the ordinary case behind it: an
application that shows a document chosen somewhere else on the screen.

The obvious answer is a handle to call `navigate(url)` on. The toolkit's own
rule points elsewhere: a widget is a value rebuilt every frame, and the widget
**already takes the page as a value**. Nothing was reading it after the first
frame.

## Decision

**The widget follows its page value.** The application holds the page it wants,
rebuilds, and `web-view` navigates:

```java
private WebPage showing = WebPage.of(START);
// ...
new WebView(showing)
// and the Go button is:
setState(() -> showing = WebPage.of(typed));
```

No new API, no handle, nothing to hold on to, and no second way of saying what
the page shows that could disagree with the first.

### Compared by what it shows, not by `equals`

`WebPage.showsSameAs` compares the url and the document and deliberately nothing
else.

It has to. [ADR-0448] put callbacks on the page value, a callback is a lambda,
and lambdas have no value equality — so two rebuilds of the same `on(...)`
expression are unequal pages and a widget navigating on `equals` would reload
for ever. Even without them the right comparison is this one: a title, a window
size and the inspector are things about a page that do not change what is on it,
and re-navigating to put a different word in a titlebar is a reload nobody asked
for.

### The spinner comes back, and only for this

[ADR-0445] latched `shown` after the first load, because a page that re-parked
on every navigation would blank itself when a *site* redirected — which is what
a real run showed GitHub doing six seconds in. A browser keeps the old document
up until the new one commits.

An application saying "show this instead" is a different event, and the latch is
released for it. What is on screen is no longer what was asked for, and several
seconds of stale content with no sign of life is worse than a spinner. That
distinction is the whole reason navigation is driven from the *value*: the
widget can tell the two apart because one of them arrives as a rebuild and the
other does not.

## Alternatives considered

**An imperative `navigate(url)` handle.** Direct, familiar, and it adds a second
source of truth: after `page.navigate(b)` the widget's own value still says `a`,
and the next rebuild for any unrelated reason would navigate back. Making that
safe means the handle writing into the widget's state, which is the declarative
design with extra steps.

**Comparing with `equals` and telling applications not to use lambdas in a page
that may be rebuilt.** A rule nobody would remember, enforced by an infinite
reload.

**Re-navigating on any page change including the title.** Simpler to describe
and wrong: it reloads a document to change a window title.

## Consequences

**An address bar is fifteen lines of application code**, and the showcase has
one. Typing changes a field; pressing Go changes the page; the widget does the
rest.

**`WebPage` grows `showsSameAs`**, which is a method that exists because
`equals` cannot be used — worth saying plainly, since a reader will otherwise
reach for `equals` first.

**A page can be swapped for a document and back**, because `showsSameAs`
compares the html as well as the url.

**Navigation is one frame late**, like everything else `web-view` does from its
painter. Nobody can see it.

**Nothing observes the *result*.** The application knows what it asked for and
not whether it arrived: a url that 404s or never resolves looks the same from
outside as one that worked, because the widget keeps the load state to itself.
An application wanting to show "could not load that" needs something this
decision does not provide, and the state is already there to expose when
somebody needs it.

[ADR-0445]: 0445-a-page-is-not-shown-before-it-can-be-seen.md
[ADR-0448]: 0448-a-page-calls-back-through-a-name-it-was-given.md
