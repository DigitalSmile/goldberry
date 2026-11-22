package dev.goldberry.camera.view;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.i18n.Labels;
import dev.goldberry.i18n.ToolkitLabel;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The node a stylesheet means by `camera-view`: the picture, or the line
/// that says why there is none.
///
/// @param content the picture's view, or the message
/// @param id      the widget's id
/// @param classes the widget's classes, with the camera's state
record CameraBox(Widget content, @Nullable String id, Set<String> classes)
        implements Widget.Leaf, Styled, Paints, Semantics {

    CameraBox {
        classes = Set.copyOf(classes);
    }

    @Override
    public String cssType() {
        return "camera-view";
    }

    @Override
    public List<Widget> children() {
        return List.of(content);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().children(boxes.toArray(Box[]::new)).style(style);
    }

    @Override
    public Role role() {
        return Role.FIGURE;
    }

    @Override
    public String accessibleName() {
        return Labels.text(ToolkitLabel.CAMERA);
    }
}
