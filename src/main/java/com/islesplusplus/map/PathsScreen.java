package com.islesplusplus.map;

import com.islesplus.ui.Flow;
import com.islesplus.ui.Fonts;
import com.islesplus.ui.Metrics;
import com.islesplus.ui.Theme;
import com.islesplus.ui.Widget;
import com.islesplus.ui.widgets.Button;
import com.islesplus.ui.widgets.ConfirmDialog;
import com.islesplus.ui.widgets.EmptyBox;
import com.islesplus.ui.widgets.Label;
import com.islesplus.ui.widgets.TextField;
import com.islesplusplus.OverlayScreen;
import com.islesplusplus.ScrollDialog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.Function;

import static com.islesplus.IslesClient.sendInfoMessage;

// /path with nothing after it: record, start/stop, rename and delete paths
public final class PathsScreen extends OverlayScreen {
    private static final int LIST_W = 300, ROW_GAP = 4;

    private final Flow.Column top = new Flow.Column(ROW_GAP);
    private final Flow.Column list = new Flow.Column(0);   // the rows bring their own dividers

    public PathsScreen(Screen parent) {
        super("Paths", parent);
    }

    @Override
    protected void init() {
        if (hasOverlays()) return;   // a window resize runs init again; keep what's open
        rebuild();
        Widget body = new Flow.Column(Metrics.DRAWER_GAP).add(top).add(new Scroll(list));
        openOverlay(new ScrollDialog(this, "Paths", body).width(LIST_W));
    }

    // runs a Trails command with the player and says what happened in chat, like the /path one would
    private void run(Function<ClientPlayerEntity, String> command, boolean closeAfter) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        sendInfoMessage(MinecraftClient.getInstance(), command.apply(player));
        if (closeAfter) close();
        else rebuild();
    }

    private void rebuild() {
        top.clear();
        Flow.WrapRow buttons = new Flow.WrapRow(ROW_GAP, ROW_GAP);
        if (Trails.recording) {
            top.add(new Label("Recording · " + Trails.current.nodes.size() + " nodes", Theme.HUD_GOLD, Fonts.SMALL));
            buttons.add(new Button("Add node", Button.Kind.PRIMARY, () -> askName("Add node", "Cave entrance",
                name -> Trails.addNode(MinecraftClient.getInstance().player, name), true)).plus())
                .add(new Button("End", Button.Kind.SECONDARY, () -> run(p -> Trails.end(), false)));
        } else if (Trails.current != null) {
            top.add(new Label("Unsaved path · " + Trails.current.nodes.size() + " nodes", Theme.HUD_GOLD, Fonts.SMALL));
            buttons.add(new Button("Discard", Button.Kind.QUIET, () -> run(p -> Trails.discard(), false)));
        } else {
            buttons.add(new Button("Record path", Button.Kind.PRIMARY, () -> run(Trails::record, true)).plus());
        }
        if (Trails.current != null)
            buttons.add(new Button("Save", Button.Kind.PRIMARY, () -> askName("Save path", Trails.nextName(), Trails::save, false)));
        top.add(buttons);

        list.clear();
        if (Trails.all().isEmpty()) list.add(new EmptyBox("No saved paths. Record one."));
        for (Trails.Trail t : Trails.all()) list.add(row(t));
    }

    private Widget row(Trails.Trail t) {
        boolean started = t == Trails.active();
        Flow.WrapRow body = new Flow.WrapRow(ROW_GAP, ROW_GAP).add(new Marker(() -> started ? Theme.HUD_VERDIGRIS : Theme.RIVET_OFF)).add(new PathLines(t, started));
        body.add(started
                ? new Button("Stop", Button.Kind.SECONDARY, () -> run(p -> Trails.stop(), false)).small()
                : new Button("Start", Button.Kind.PRIMARY, () -> run(p -> Trails.start(t.name), false)).small())
            .add(new Button("Rename", Button.Kind.QUIET, () -> askName("Rename path", t.name,
                name -> Trails.rename(t, name), false)).small())
            .add(new Button("Delete", Button.Kind.QUIET, () -> ConfirmDialog.open(this, "Delete path",
                "Delete " + t.name + "?", "Delete", () -> run(p -> Trails.delete(t.name), false))).small());
        return new Row(body);
    }

    // a name prompt; blank uses the placeholder
    private void askName(String title, String placeholder, Function<String, String> then, boolean closeAfter) {
        String[] draft = {""};
        Widget[] dialogRef = new Widget[1];
        Runnable ok = () -> {
            String name = draft[0].isBlank() ? placeholder : draft[0].trim();
            closeOverlay(dialogRef[0]);
            run(p -> then.apply(name), closeAfter);
        };
        TextField field = new TextField(() -> draft[0], v -> draft[0] = v, placeholder, 40).onCommit(ok);
        Widget body = new Flow.Column(7)
            .add(field)
            .add(new Flow.WrapRow(ROW_GAP, ROW_GAP)
                .add(new Button("Cancel", Button.Kind.QUIET, () -> closeOverlay(dialogRef[0])).fill())
                .add(new Button("OK", Button.Kind.PRIMARY, ok).fill()));
        ScrollDialog dialog = new ScrollDialog(this, title, body).width(LIST_W);
        dialogRef[0] = dialog;
        openOverlay(dialog);
        field.focus();
    }

    private static final class PathLines extends Widget {
        private final Trails.Trail path;
        private final boolean started;
        PathLines(Trails.Trail path, boolean started) { this.path = path; this.started = started; }
        @Override public int layout(int x, int y, int width) {
            this.x = x; this.y = y; w = width;
            h = Fonts.height(Fonts.BODY) + 2 + Fonts.height(Fonts.SMALL);
            return h;
        }
        @Override public void render(DrawContext ctx, int mouseX, int mouseY) {
            Fonts.draw(ctx, Fonts.ellipsize(path.name, w), x, y, Theme.TEXT_STRONG);
            String what = path.nodes.size() + " nodes" + (started ? "  · showing" : "");
            Fonts.draw(ctx, Fonts.ellipsize(what, w, Fonts.SMALL), x, y + Fonts.height(Fonts.BODY) + 2,
                started ? Theme.HUD_VERDIGRIS : Theme.TEXT_META, Fonts.SMALL);
        }
    }
}
