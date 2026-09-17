package io.github.digitalsmile.goldberry.widgets.nav.wizard;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.nav.steps.Step;
import io.github.digitalsmile.goldberry.widgets.nav.steps.Steps;

/// A [Wizard]'s one piece of state: which page it last showed, so that a change
/// of page — and only a change — moves the keyboard into the new one.
///
/// Everything else about a wizard is a pure function of its pages and its
/// index, and is built here from scratch on every build.
final class WizardState extends State<Wizard> {

    /// The window this is being built into, for the focus request.
    private @Nullable Host host;

    /// The page shown by the last build, or -1 before the first.
    private int shown = -1;

    /// The zero-delay timer that asks for focus, held so it can be cancelled
    /// — a wizard unmounted in the turn it advanced would otherwise leave one
    /// pointing at a tree that is gone.
    private EventLoop.@Nullable Timer focusing;

    @Override
    protected void dispose() {
        if (focusing != null) {
            focusing.cancel();
            focusing = null;
        }
        super.dispose();
    }

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var wizard = widget();
        var pages = wizard.pages();
        var current = wizard.resolvedCurrent();
        var contentId = contentId(wizard.attributes());

        if (shown >= 0 && shown != current) {
            askForFocus(contentId);
        }
        shown = current;

        var steps = new ArrayList<Widget>(pages.size());
        for (var page : pages) {
            steps.add(new Step(page.label(), page.description())
                    .error(page.error())
                    .reachable(page.reachable()));
        }
        var indicator = new Steps(
                steps,
                current,
                null,
                Steps.Direction.HORIZONTAL,
                wizard.goTo() != null,
                wizard.goTo(),
                Attributes.NONE);

        var page = pages.isEmpty() ? null : pages.get(current);
        var content = new WizardContent(
                page == null ? List.of() : page.children(),
                (page == null ? Attributes.NONE : page.attributes()).id(contentId));

        var actions = new WizardActions(buttons(wizard, current, pages.size()));
        var name = page == null ? "" : page.label() + ", step " + (current + 1) + " of " + pages.size();
        return new WizardPanel(List.of(indicator, content, actions), name, wizard.attributes());
    }

    /// Back, then Next or Finish — dismissive then affirmative, which is the
    /// canonical order the theme may reverse. Back is disabled rather than
    /// absent on the first page, so the bar does not change shape as the user
    /// moves through it; a wizard that was given no `back` has no Back at all.
    private static List<Widget> buttons(Wizard wizard, int current, int count) {
        var buttons = new ArrayList<Widget>(2);
        var labels = wizard.labels();
        if (wizard.onBack() != null) {
            buttons.add(new Button(labels.back(), wizard.onBack())
                    .disabled(current == 0)
                    .withAttributes(Attributes.NONE.id(id(wizard, "back"))));
        }
        var last = current >= count - 1;
        var affirmative = last ? wizard.onFinish() : wizard.onNext();
        if (affirmative != null) {
            buttons.add(new Button(last ? labels.finish() : labels.next(), affirmative)
                    .withAttributes(Attributes.NONE
                            .id(id(wizard, last ? "finish" : "next"))
                            .classes("primary")));
        }
        return buttons;
    }

    /// The content area's id: the wizard's own with `-content` after it, or a
    /// name of this state's own for a wizard the document did not name — a
    /// focus request needs an id, and a page change without one would move
    /// nothing.
    private String contentId(Attributes attributes) {
        var id = attributes.id();
        return id == null
                ? "wizard-" + Integer.toHexString(System.identityHashCode(this)) + "-content"
                : id + "-content";
    }

    private static @Nullable String id(Wizard wizard, String part) {
        var id = wizard.attributes().id();
        return id == null ? null : id + "-" + part;
    }

    private void askForFocus(String contentId) {
        if (host == null) {
            return;
        }
        if (focusing != null) {
            focusing.cancel();
        }
        var window = host;
        focusing = window.after(Duration.ZERO, () -> window.focus(contentId, false));
    }
}
