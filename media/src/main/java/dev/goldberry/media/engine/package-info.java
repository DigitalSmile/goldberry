/// The Engine: the demux thread, one decode thread
/// per track, the packet queues and Serial, the frame queue, the master clock,
/// and the state machine behind [dev.goldberry.media.MediaPlayer].
///
/// Not exported. Platform threads, not virtual ones: every one of them spends
/// its life in native calls, where a virtual thread would pin its carrier.
/// Null-marked.
///
/// Read more: [A player](https://goldberry.dev/docs/components/media.html#a-player).
@NullMarked
package dev.goldberry.media.engine;

import org.jspecify.annotations.NullMarked;
