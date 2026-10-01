/// Input dispatch: turning pointer positions into events, pseudo-classes and
/// focus (§7, ADR-0054), and saying which arrow keys rove inside a composite that
/// is one Tab stop (ADR-0073).
///
/// The router's state — hovered, pressed, focused — is held against elements
/// rather than widgets, because a widget is rebuilt constantly and could not
/// remember any of it (ADR-0052). The vocabulary around it lives in subpackages by
/// the role each plays (ADR-0172): `input.event`, `input.key`, `input.hit` and
/// `input.handler`, and the gestures `input.tap` and `input.drop`.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.input;

import org.jspecify.annotations.NullMarked;
