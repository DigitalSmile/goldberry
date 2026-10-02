package dev.goldberry.widgets.menu;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.icon.Icon;
import dev.goldberry.layout.Length;
import dev.goldberry.log.Logs;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.Icons;

/// The column before an [Item]'s label: a part, so a stylesheet can select it
/// as `item-lead` and a document cannot write it.
///
/// One column holding one of three things: a tick, an icon, or nothing, and
/// never a tick *and* an icon, which is what every desktop menu does. The slot
/// is one part with one width. A menu reserves it when **anything in it** has
/// an icon or is checkable, and then every row has one, which is what keeps the
/// labels in a line.
///
/// @param checked whether to draw a tick
/// @param icon    the row's icon, drawn when there is no tick to draw
record ItemLead(boolean checked, @Nullable Icon icon) implements Widget.Leaf, Styled, Paints {

    private static final Logger LOG = Logs.of(ItemLead.class);

    /// Which `(name, built size, slot width)` triples have already been mentioned.
    ///
    /// [ScrollContent]'s shape exactly: `render` runs per row per paint, so an
    /// unguarded line here would be sixty a second for as long as a menu is open.
    /// The key is the triple rather than the name, because the interesting fact is
    /// *"this icon does not fit this column"* and an application that puts the same
    /// 20px glyph in five menus has made one choice.
    private static final Set<String> REPORTED_OVERHANG = ConcurrentHashMap.newKeySet();

    /// A cap, so a document that names a thousand icons cannot turn this into the
    /// leak the dedup set exists to prevent. Over the cap they all go through,
    /// which is `ComputedStyle.dropped`'s choice: a diagnostic that goes silent
    /// after N is worse than one that repeats.
    private static final int REPORT_LIMIT = 256;

    /// Forgets what has been reported, for a test that draws the same menu twice.
    /// `ScrollContent.forgetReportedGrow`'s reason exactly.
    static void forgetReportedOverhang() {
        REPORTED_OVERHANG.clear();
    }

    /// What has been reported, so a test can say *once* rather than merely *at
    /// all*. There is no appender on the classpath here to read the log back from,
    /// so the set is what an assertion can see.
    static Set<String> reportedOverhang() {
        return Set.copyOf(REPORTED_OVERHANG);
    }

    @Override
    public String cssType() {
        return "item-lead";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// Mirrored to `:checked`, so a tick is a stylesheet's business and not a
    /// second drawing.
    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var box = Box.of().style(style);
        if (checked) {
            return box.mark(new Box.Mark(Box.Mark.Kind.CHECK, style.color(), 2));
        }
        if (icon == null) {
            return box;
        }
        reportIfItOverhangs(style);
        // An icon where the tick would be. `Box.icon` sizes the box to the icon
        // and then `style` is applied **over** it, so the column's own width from
        // `controls.css` wins — which is right: the icon is what has to line up
        // with the tick, and the column is what decides where that is. An
        // oversized glyph is centred in the column and overhangs it.
        return Box.icon(icon, style.color()).style(style);
    }

    /// Says, once, that this icon is bigger than the column it is centred in.
    ///
    /// **`debug` and not `warn`**, because nothing here is broken: an oversized
    /// glyph is centred on purpose, and the showcase itself builds its menu
    /// icons at 20, so a `WARN` would fire on the toolkit's own demo, on every
    /// row of every menu, and claim a defect where there is a preference. A
    /// `WARN` means something is broken.
    ///
    /// What it is for is the moment somebody asks *why is this icon large* and
    /// turns the level up. It names the icon, both numbers and the fix: an
    /// application that wants them to fit builds them at [Icons#SLOT].
    ///
    /// **Only against an explicit points width.** A `Length.Percent` or an `AUTO`
    /// column has no number to be bigger than, and guessing one is how a
    /// diagnostic starts reporting arithmetic. There is no tolerance either, and
    /// it needs none: these are two numbers an author typed, not a sum of insets —
    /// the epsilon is for the `double`, not for the layout.
    private void reportIfItOverhangs(ComputedStyle style) {
        var drawn = Objects.requireNonNull(this.icon, "only a lead with an icon can overhang");
        if (!(style.width() instanceof Length.Points(var width)) || !(drawn.size() > width + 0.5)) {
            return;
        }
        var key = drawn.name() + '/' + drawn.size() + '/' + width;
        if (REPORTED_OVERHANG.size() >= REPORT_LIMIT || REPORTED_OVERHANG.add(key)) {
            LOG.debug(
                    "icon \"{}\" is {}px in a {}px `item-lead`, so it is centred and overhangs the column."
                            + " An icon is built at a size and cannot be rescaled:"
                            + " build it at {} — Icons.SLOT, or icons.bind(\"{}\") — to fit.",
                    drawn.name(),
                    drawn.size(),
                    width,
                    Icons.SLOT,
                    drawn.name());
        }
    }
}
