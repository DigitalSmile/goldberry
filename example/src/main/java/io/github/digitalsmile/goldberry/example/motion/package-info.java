/// The Motion screen's canvas choreography: a floor of glazed tiles that settles
/// into place and then slowly re-glazes itself (`docs/gaps.md` G41, ADR-0354).
///
/// A tile's pose is a pure function of time, with no clock and no state.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.example.motion;

import org.jspecify.annotations.NullMarked;
