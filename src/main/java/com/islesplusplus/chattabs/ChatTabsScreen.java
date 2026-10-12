package com.islesplusplus.chattabs;

import com.islesplus.IslesPlusConfig;
import com.islesplus.ui.Flow;
import com.islesplus.ui.Fonts;
import com.islesplus.ui.Metrics;
import com.islesplus.ui.Theme;
import com.islesplus.ui.Widget;
import com.islesplus.ui.widgets.Button;
import com.islesplus.ui.widgets.ConfirmDialog;
import com.islesplus.ui.widgets.Dialog;
import com.islesplus.ui.widgets.EmptyBox;
import com.islesplus.ui.widgets.FootnoteBox;
import com.islesplus.ui.widgets.Label;
import com.islesplus.ui.widgets.Panel;
import com.islesplus.ui.widgets.TextField;
import com.islesplusplus.OverlayScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

// add / edit / reorder / delete chat tabs. editing() opens just the edit dialog (right click a tab in chat)
public final class ChatTabsScreen extends OverlayScreen {
    private static final int LIST_W = 300, ROW_GAP = 4;

    private final Flow.Column list = new Flow.Column(ROW_GAP);
    // null = show the list, -1 = add a tab, else the tab to edit
    private final Integer only;

    public ChatTabsScreen(Screen parent) {
        this(parent, null);
    }

    private ChatTabsScreen(Screen parent, Integer only) {
        super("Chat Tabs", parent);
        this.only = only;
    }

    public static ChatTabsScreen editing(Screen parent, int index) {
        return new ChatTabsScreen(parent, index);
    }

    @Override
    protected void init() {
        if (hasOverlays()) return;   // a window resize runs init again; keep what's open
        if (only != null) {
            edit(only);
            return;
        }
        rebuild();
        Widget body = new Flow.Column(Metrics.DRAWER_GAP)
            .add(new Flow.WrapRow(ROW_GAP, ROW_GAP)
                .add(new Button("Add tab", Button.Kind.PRIMARY, () -> edit(-1)).plus()))
            .add(new Scroll(list));
        openOverlay(new Dialog(this, "Chat Tabs", body, null).width(LIST_W));
    }

    private void changed() {
        ChatTabs.refreshChat();
        IslesPlusConfig.save();
        rebuild();
    }

    private void rebuild() {
        list.clear();
        if (ChatTabs.tabs.isEmpty()) list.add(new EmptyBox("No tabs, chat shows everything. Add one."));
        for (int i = 0; i < ChatTabs.tabs.size(); i++) list.add(row(i));
    }

    private Widget row(int i) {
        ChatTabs.Tab tab = ChatTabs.tabs.get(i);
        Flow.WrapRow body = new Flow.WrapRow(ROW_GAP, ROW_GAP).add(new TabLines(tab));
        if (i > 0) body.add(new Button("Up", Button.Kind.SECONDARY, () -> {
            ChatTabs.tabs.add(i - 1, ChatTabs.tabs.remove(i));
            changed();
        }).small());
        body.add(new Button("Edit", Button.Kind.SECONDARY, () -> edit(i)).small())
            .add(new Button("Delete", Button.Kind.QUIET, () -> ConfirmDialog.open(this, "Delete tab",
                "Delete " + tab.name() + "?", "Delete", () -> {
                    ChatTabs.tabs.remove(tab);
                    changed();
                })).small());
        return new Panel(body, 5, Theme.SURFACE, Theme.RAISED, Theme.SECONDARY, Theme.SURFACE_RING);
    }

    // -1 = new tab. works on a copy, nothing changes until Save
    private void edit(int index) {
        ChatTabs.Tab existing = index >= 0 ? ChatTabs.tabs.get(index) : null;
        String[] parts = ChatTabs.split(existing != null ? existing.filter() : "");
        String[] draft = {existing != null ? existing.name() : "", parts[0], parts[1]};
        Widget[] dialogRef = new Widget[1];

        int labelW = Fonts.labelColumn(Fonts.SMALL, "Show lines with", "Hide lines with");
        Label error = new Label("Give it a name.", Theme.OXBLOOD, Fonts.SMALL);
        error.visible = false;
        Flow.WrapRow buttons = new Flow.WrapRow(ROW_GAP, ROW_GAP)
            .add(new Button("Cancel", Button.Kind.QUIET, () -> closeOverlay(dialogRef[0])).fill());
        if (existing != null) buttons.add(new Button("Delete", Button.Kind.QUIET, () -> ConfirmDialog.open(this, "Delete tab",
            "Delete " + existing.name() + "?", "Delete", () -> {
                ChatTabs.tabs.remove(existing);
                changed();
                closeOverlay(dialogRef[0]);
            })).fill());
        Widget body = new Flow.Column(7)
            .add(field("Name", labelW, new TextField(() -> draft[0], v -> draft[0] = v, "Guild", 16)))
            .add(field("Show lines with", labelW, new TextField(() -> draft[1], v -> draft[1] = v, "anything", 120)))
            .add(field("Hide lines with", labelW, new TextField(() -> draft[2], v -> draft[2] = v, "nothing", 120)))
            .add(new FootnoteBox("Words separated by commas, capitals don't matter.",
                "Player chat has » in it, server messages don't."))
            .add(error)
            .add(buttons
                .add(new Button("Save", Button.Kind.PRIMARY, () -> {
                    error.visible = draft[0].isBlank();
                    if (error.visible) return;
                    ChatTabs.Tab tab = new ChatTabs.Tab(draft[0].trim(), ChatTabs.join(draft[1], draft[2]));
                    if (existing != null) ChatTabs.tabs.set(index, tab);
                    else ChatTabs.tabs.add(tab);
                    changed();
                    closeOverlay(dialogRef[0]);
                }).fill()));

        Dialog dialog = new Dialog(this, existing != null ? "Edit tab" : "Add tab", body, null).width(LIST_W);
        dialogRef[0] = dialog;
        openOverlay(dialog);
    }

    private static Widget field(String label, int labelW, TextField field) {
        return new Flow.WrapRow(ROW_GAP, ROW_GAP)
            .add(new Label(label, Theme.TEXT_LABEL, Fonts.SMALL).fixed(labelW))
            .add(field);
    }

    private static final class TabLines extends Widget {
        private final ChatTabs.Tab tab;
        TabLines(ChatTabs.Tab tab) { this.tab = tab; }
        @Override public int layout(int x, int y, int width) {
            this.x = x; this.y = y; w = width;
            h = Fonts.height(Fonts.BODY) + 2 + Fonts.height(Fonts.SMALL);
            return h;
        }
        @Override public void render(DrawContext ctx, int mouseX, int mouseY) {
            Fonts.draw(ctx, Fonts.ellipsize(tab.name(), w), x, y, Theme.TEXT_STRONG);
            String[] p = ChatTabs.split(tab.filter());
            String what = (p[0].isEmpty() ? "everything" : p[0]) + (p[1].isEmpty() ? "" : "  · not " + p[1]);
            Fonts.draw(ctx, Fonts.ellipsize(what, w, Fonts.SMALL), x, y + Fonts.height(Fonts.BODY) + 2, Theme.TEXT_META, Fonts.SMALL);
        }
    }
}
