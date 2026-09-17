package io.github.digitalsmile.goldberry.widgets.text;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// A [Link]'s two facts: the window it opens an `href` through, and the icon
/// an external link draws.
///
/// The icon is why this is stateful at all. An `Icon` is a value the toolkit
/// builds from path data and holds until it is closed; a widget is rebuilt
/// every frame and must not own one (ADR-0043). A state outlives its rebuilds
/// and has a `dispose`, which is exactly the lifetime an icon needs.
final class LinkState extends State<Link> {

    private static final Logger LOG = Logs.of(LinkState.class);

    /// §2's "trailing 12px `external-link` icon".
    static final String EXTERNAL_ICON = "external-link";

    static final double EXTERNAL_ICON_SIZE = 12;

    private @Nullable Host host;

    private @Nullable Icon external;

    @Override
    protected void dispose() {
        if (external != null) {
            external.close();
            external = null;
        }
        super.dispose();
    }

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var link = widget();
        if (link.isExternal() && external == null) {
            external = Icon.bundled(EXTERNAL_ICON, EXTERNAL_ICON_SIZE);
        }
        var follow = follow(link);
        return new LinkText(link.label(), follow, link.isExternal(), link.visited(), external, link.attributes());
    }

    /// What pressing the link does: the action, the platform, or nothing.
    private @Nullable Runnable follow(Link link) {
        var action = link.onPress();
        var href = link.href();
        if (action == null && href == null) {
            return null;
        }
        return () -> {
            if (action != null) {
                action.run();
            }
            if (href != null) {
                open(href);
            }
        };
    }

    /// Hands the URL to the desktop, and says so when the desktop would not:
    /// a link that silently did nothing is the one failure a user cannot tell
    /// from their own missed click.
    private void open(String href) {
        if (host == null || !host.openExternal(href)) {
            LOG.warn("the platform would not open {}", href);
        }
    }
}
