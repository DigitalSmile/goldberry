/*
 * Every screenshot in the guide opens at full size on a click.
 *
 * A picture in a `gb-shot` is drawn at the size the widget has on screen, and a
 * whole-window screenshot is scaled down to the column. Both files are taken
 * at 2x, so the full-size view shows detail the page cannot. The picture is a
 * button: a click, Enter or Space opens it over the page; a click anywhere,
 * Escape or the close button puts it away and returns focus to the picture.
 *
 * Without the script the pictures are plain images and nothing else changes.
 * book/test/lightbox.test.mjs runs this file against a small fake document.
 */
(function () {
    var OPEN = "gb-lightbox-open";
    var shots = document.querySelectorAll(".gb-shot");
    if (!shots.length) {
        return;
    }
    var open = null;

    function captionOf(shot) {
        var caption = shot.querySelector("p");
        return caption ? caption.textContent : "";
    }

    function close() {
        if (!open) {
            return;
        }
        var from = open.from;
        open.overlay.remove();
        document.documentElement.classList.remove(OPEN);
        document.removeEventListener("keydown", onKey);
        open = null;
        from.focus();
    }

    function onKey(event) {
        if (event.key === "Escape") {
            event.preventDefault();
            close();
        }
    }

    function show(picture, shot) {
        close();
        var overlay = document.createElement("div");
        overlay.className = "gb-lightbox";
        overlay.setAttribute("role", "dialog");
        overlay.setAttribute("aria-modal", "true");
        overlay.setAttribute("aria-label", picture.getAttribute("alt") || "Screenshot");

        var figure = document.createElement("figure");
        var full = document.createElement("img");
        full.src = picture.currentSrc || picture.src;
        full.alt = picture.getAttribute("alt") || "";
        figure.appendChild(full);
        var text = captionOf(shot);
        if (text) {
            var caption = document.createElement("figcaption");
            caption.textContent = text;
            figure.appendChild(caption);
        }

        var dismiss = document.createElement("button");
        dismiss.type = "button";
        dismiss.setAttribute("aria-label", "Close");
        dismiss.textContent = "×";

        overlay.appendChild(figure);
        overlay.appendChild(dismiss);
        overlay.addEventListener("click", close);
        document.body.appendChild(overlay);
        document.documentElement.classList.add(OPEN);
        document.addEventListener("keydown", onKey);
        open = { overlay: overlay, from: picture };
        dismiss.focus();
    }

    Array.prototype.forEach.call(shots, function (shot) {
        var pictures = shot.querySelectorAll("img");
        if (!pictures.length) {
            return;
        }
        shot.classList.add("gb-zoomable");
        Array.prototype.forEach.call(pictures, function (picture) {
            picture.setAttribute("role", "button");
            picture.setAttribute("tabindex", "0");
            picture.setAttribute("title", "Click to enlarge");
            picture.addEventListener("click", function () {
                show(picture, shot);
            });
            picture.addEventListener("keydown", function (event) {
                if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    show(picture, shot);
                }
            });
        });
    });
})();
