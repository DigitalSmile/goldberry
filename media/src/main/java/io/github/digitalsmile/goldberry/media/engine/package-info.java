/// The Engine (`docs/goldberry-media.md` §3): the demux thread, the decode
/// threads, the packet queues and Serial, the audio clock, and the state machine
/// behind [io.github.digitalsmile.goldberry.media.MediaPlayer].
///
/// Not exported. Platform threads, not virtual ones: every one of them spends
/// its life in native calls, where a virtual thread would pin its carrier.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.media.engine;

import org.jspecify.annotations.NullMarked;
