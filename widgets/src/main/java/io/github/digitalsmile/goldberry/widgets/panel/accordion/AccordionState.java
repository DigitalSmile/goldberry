package io.github.digitalsmile.goldberry.widgets.panel.accordion;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.panel.collapse.Collapse;
import java.util.ArrayList;

/// Which of an [Accordion]'s sections is open.
///
/// One integer, and the whole of what makes "one at a time" true: a section
/// cannot know about its siblings, so the container holds the answer and hands
/// each section its own half of it.
final class AccordionState extends State<Accordion> {

    /// Only meaningful while the widget is uncontrolled.
    private int open;

    @Override
    protected void initState() {
        open = widget().open();
    }

    private int resolved() {
        return widget().isControlled() ? widget().open() : open;
    }

    @Override
    public Widget build(BuildContext context) {
        var accordion = widget();
        var current = resolved();
        var wired = new ArrayList<Widget>(accordion.children().size());
        for (var index = 0; index < accordion.children().size(); index++) {
            wired.add(section(accordion.children().get(index), index, current));
        }
        return new AccordionColumn(wired, accordion.attributes());
    }

    /// One child, wired to the group if it is a section.
    private Widget section(Widget child, int index, int current) {
        if (!(child instanceof Collapse collapse)) {
            // A heading, a rule, anything else the author put between the
            // sections. Not everything in an accordion is a section.
            return child;
        }
        if (collapse.isControlled()) {
            // The application is already deciding this one, and two things
            // deciding one boolean is a bug. It asked first.
            return collapse;
        }
        return new Collapse(collapse.title(), index == current,
                wanted -> set(wanted ? index : Accordion.NONE),
                collapse.children(), collapse.attributes());
    }

    /// Opening a section closes whatever was open, which is the whole rule —
    /// and it falls out of holding *one* number rather than a boolean per
    /// section, which is why there is no state here that can disagree with
    /// itself.
    private void set(int next) {
        if (next == resolved()) {
            return;
        }
        if (widget().isControlled()) {
            widget().onOpen().accept(next);
            return;
        }
        setState(() -> open = next);
    }
}
