package dev.goldberry.example.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.bind.Model;
import dev.goldberry.bind.runtime.Actions;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.example.ShowcaseWiring;
import dev.goldberry.example.media.ShowcasePlayers;
import dev.goldberry.example.model.controls.ControlsModel;

/// The split itself: what has to stay true for a model of eight slices to
/// read, to a document, as the one model it replaced.
@DisplayName("the showcase's model, sliced")
class ShowcaseModelTest {

    private static final Path MODULE_INFO = Path.of("src/main/java/module-info.java");

    /// `opens a.b.c to x, y;` across however many lines it is written on.
    private static final Pattern OPENS = Pattern.compile("opens\\s+([\\w.]+)\\s+to\\s+([^;]+);", Pattern.DOTALL);

    private final ShowcaseModel model = new ShowcaseModel();
    private final ShowcaseActions actions = new ShowcaseActions(model);

    @AfterEach
    void closePlayers() {
        model.media().close();
    }

    @Test
    @DisplayName("every slice is a model, and every one of them is listed")
    void everySliceIsListed() {
        // Read off the record rather than off `slices()`, so a slice added as a
        // component and forgotten in the list is what fails here: it would be a
        // model whose paths no document can see.
        var components = Arrays.stream(ShowcaseModel.class.getRecordComponents())
                .filter(component -> component.getType().isAnnotationPresent(Model.class))
                .<Class<?>>map(RecordComponent::getType)
                .toList();
        var listed = model.slices().stream().map(Object::getClass).toList();

        assertEquals(components, listed, "slices() lists the @Model components, in the record's order");
        assertFalse(
                ShowcasePlayers.class.isAnnotationPresent(Model.class),
                "the players are named objects, so nothing about them is bound");
    }

    @Test
    @DisplayName("every slice's actions are an actions record over that slice, in the same order")
    void actionsFollowTheSlices() {
        var slices = model.slices();
        var records = actions.slices();

        assertEquals(slices.size(), records.size());
        for (var i = 0; i < slices.size(); i++) {
            var record = records.get(i);
            assertTrue(record.getClass().isAnnotationPresent(Actions.class), () -> record + " is not @Actions");
            assertSame(
                    record.getClass().getEnclosingClass(),
                    slices.get(i).getClass(),
                    "a slice's actions are nested in the slice they write");
        }
        assertTrue(actions.write(model), "the actions write the model they were built from");
        assertFalse(
                actions.write(new ShowcaseModel(
                        model.shell(),
                        model.appearance(),
                        new ControlsModel(),
                        model.forms(),
                        model.pickers(),
                        model.documents(),
                        model.navigation(),
                        model.sheets(),
                        model.media())),
                "one slice of another showcase is a different model");
    }

    @Test
    @DisplayName("no two slices publish one name, and the merge keeps every one")
    void pathsAreDisjoint() {
        var paths = new HashSet<String>();
        var names = new HashSet<String>();
        var expectedPaths = 0;
        var expectedNames = 0;
        for (var slice : model.slices()) {
            var bound = Models.bindings(slice).bound().keySet();
            expectedPaths += bound.size();
            paths.addAll(bound);
        }
        for (var record : actions.slices()) {
            var bound = Models.actions(record).bound().keySet();
            expectedNames += bound.size();
            names.addAll(bound);
        }

        assertEquals(expectedPaths, paths.size(), "two slices bind one path");
        assertEquals(expectedNames, names.size(), "two slices bind one action");
        // Which is what the window's own merge refuses, so it has to succeed.
        var wiring = ShowcaseWiring.of(model, actions);
        assertEquals(paths, wiring.bindings().bound().keySet());
        assertEquals(names, wiring.actions().bound().keySet());
    }

    @Test
    @DisplayName("the paths the documents name are each where the split put them")
    void pathsLandOnTheirSlice() {
        assertOwns(model.shell(), "app.screen", "app.reading", "app.sidebar", "app.status", "app.startup", "app.hud");
        assertOwns(model.appearance(), "app.theme", "app.light", "app.density", "app.scrollbars");
        assertOwns(model.controls(), "app.clicks", "app.prose", "app.partly", "app.gain", "app.tags");
        assertOwns(model.forms(), "app.name", "app.port", "app.signup-status", "app.code", "app.bio", "app.notes");
        assertOwns(model.pickers(), "trip.date", "trip.time", "paint.colour");
        assertOwns(model.documents(), "md.source", "md.followed", "html.source", "html.followed");
        assertOwns(model.navigation(), "app.tabs", "app.tab", "app.tabs-added", "app.path");
        assertOwns(model.sheets(), "app.icon-query", "app.emoji-query");
    }

    private static void assertOwns(Object slice, String... paths) {
        var bound = Models.bindings(slice).bound().keySet();
        for (var path : paths) {
            assertTrue(bound.contains(path), () -> slice.getClass().getSimpleName() + " does not bind " + path);
        }
    }

    @Test
    @DisplayName("the documents' named objects are registered once, from their slices")
    void namedObjects() {
        var named = model.named().bound();

        assertEquals(
                Set.of("app.signup-form", "app.port-rule", "app.assets", "audio.player", "video.player"),
                named.keySet());
        assertSame(model.media().audioPlayer(), named.get("audio.player"));
        assertSame(model.media().videoPlayer(), named.get("video.player"));
    }

    @Test
    @DisplayName("a write through one slice's actions is seen through the merged registry")
    void oneWriteOneProperty() {
        var clicks = ShowcaseWiring.observable(model, "app.clicks");

        actions.controls().click();
        actions.controls().click();

        assertSame(Models.observable(model.controls(), "app.clicks"), clicks);
        assertEquals(2, clicks.get());
        assertEquals(2, model.controls().clicks());
    }

    /// The reflective binder needs the slice's package open to `:core`, and only
    /// on the module path — which this suite does not run on, so a slice in a
    /// package nobody opened binds here and fails in a jar.
    @Test
    @DisplayName("every slice's package is open to the binder")
    void slicePackagesAreOpen() throws IOException {
        var source = Files.readString(MODULE_INFO);
        var opened = new HashSet<String>();
        var matcher = OPENS.matcher(source);
        while (matcher.find()) {
            if (matcher.group(2).contains("dev.goldberry.core")) {
                opened.add(matcher.group(1));
            }
        }
        var missing = new ArrayList<String>();
        for (var slice : model.slices()) {
            var pkg = slice.getClass().getPackageName();
            if (!opened.contains(pkg)) {
                missing.add(pkg);
            }
        }

        assertEquals(List.of(), missing, "open these to dev.goldberry.core in " + MODULE_INFO);
    }
}
