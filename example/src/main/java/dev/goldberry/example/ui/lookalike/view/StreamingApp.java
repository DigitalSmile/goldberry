package dev.goldberry.example.ui.lookalike.view;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.example.ui.lookalike.library.Library;
import dev.goldberry.example.ui.lookalike.player.Command;
import dev.goldberry.example.ui.lookalike.player.Session;
import dev.goldberry.render.event.EventLoop;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;

/// A music streaming app's window, drawn inside a tab: a sidebar, the open
/// playlist, an optional now-playing panel, and the player bar.
///
/// **Stateful, and the state is one [Session].** Every control hands a
/// [Command] to [StreamingAppState#dispatch], which swaps the session for the
/// one [Session#apply] answers and rebuilds. Nothing else is held but the timer
/// that moves a playing track along, once a second.
///
/// @param library what there is to play
/// @param opening the session the window opens on
public record StreamingApp(Library library, Session opening) implements Widget.Stateful {

    /// How often a playing track's clock moves.
    static final Duration TICK = Duration.ofSeconds(1);

    @Override
    public State<?> createState() {
        return new StreamingAppState();
    }

    /// The session, and the timer that plays it.
    static final class StreamingAppState extends State<StreamingApp> {

        @SuppressWarnings("NullAway.Init") // set in initState()
        private Session session;

        private @Nullable Host host;

        /// The next tick, or null while nothing plays.
        private EventLoop.@Nullable Timer ticking;

        @Override
        protected void initState() {
            super.initState();
            session = widget().opening();
        }

        @Override
        public Widget build(BuildContext context) {
            if (host == null) {
                host = context.host().orElse(null);
                follow();
            }
            var library = widget().library();
            var body = new ArrayList<Widget>(List.of(
                    new Sidebar(library, session, this::dispatch), new PlaylistView(library, session, this::dispatch)));
            if (session.panel()) {
                body.add(new NowPlayingPanel(library, session, this::dispatch));
            }
            return Parts.column(
                            List.of(Parts.row(body, "stream-body"), new PlayerBar(library, session, this::dispatch)),
                            "stream-app")
                    .id("stream-app");
        }

        /// What every control calls.
        void dispatch(Command command) {
            setState(() -> session = session.apply(command, widget().library()));
            follow();
        }

        /// Starts the clock when the session plays and stops it when it does not.
        private void follow() {
            if (!session.playing()) {
                stop();
                return;
            }
            if (ticking == null && host != null) {
                ticking = host.after(TICK, this::tick);
            }
        }

        private void tick() {
            ticking = null;
            if (!isMounted()) {
                return;
            }
            dispatch(new Command.Tick(TICK));
        }

        private void stop() {
            if (ticking != null) {
                ticking.cancel();
                ticking = null;
            }
        }

        @Override
        protected void dispose() {
            stop();
            super.dispose();
        }
    }
}
