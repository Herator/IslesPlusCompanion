package com.islesplusplus.chattabs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// tabs above the chat box, each one only shows the lines that match its filter (see ChatHudMixin)
public final class ChatTabs {
    // filter: comma separated words, a line shows if it has any of them (or there are none)
    // and none of the "-word" ones. case doesn't matter
    public record Tab(String name, String filter) {}

    public static boolean enabled = true;
    public static final List<Tab> tabs = new ArrayList<>();
    private static int active;

    private static final int H = 11, GAP = 2;
    private static final String EDIT = "+";

    private ChatTabs() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen)) return;
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> draw(s, ctx, mx, my));
            ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> {
                // left click picks a tab, right click edits it. the + opens the tab list
                int i = click.button() <= 1 ? tabAt(s, click.x(), click.y()) : -1;
                if (i < 0) return true;
                if (i == tabs.size()) client.setScreen(new ChatTabsScreen(s));
                else if (click.button() == 1) client.setScreen(ChatTabsScreen.editing(s, i));
                else select(i);
                return false;
            });
        });
    }

    static boolean matches(String filter, String line) {
        String text = line.toLowerCase(Locale.ROOT);
        boolean wanted = false, anyWanted = false;
        for (String term : filter.toLowerCase(Locale.ROOT).split(",")) {
            term = term.strip();
            if (term.startsWith("-")) {
                term = term.substring(1).strip();
                if (!term.isEmpty() && text.contains(term)) return false;
            } else if (!term.isEmpty()) {
                anyWanted = true;
                wanted |= text.contains(term);
            }
        }
        return wanted || !anyWanted;
    }

    // filter -> {"show, words", "hide, words"}, for the editor
    static String[] split(String filter) {
        StringBuilder show = new StringBuilder(), hide = new StringBuilder();
        for (String term : filter.split(",")) {
            term = term.strip();
            boolean neg = term.startsWith("-");
            if (neg) term = term.substring(1).strip();
            if (term.isEmpty()) continue;
            StringBuilder to = neg ? hide : show;
            to.append(to.isEmpty() ? "" : ", ").append(term);
        }
        return new String[]{show.toString(), hide.toString()};
    }

    static String join(String show, String hide) {
        StringBuilder out = new StringBuilder();
        for (String term : show.split(",")) if (!term.isBlank()) out.append(out.isEmpty() ? "" : ", ").append(term.strip());
        for (String term : hide.split(",")) if (!term.isBlank()) out.append(out.isEmpty() ? "" : ", ").append('-').append(term.strip());
        return out.toString();
    }

    public static boolean hides(String line) {
        return enabled && active < tabs.size() && !matches(tabs.get(active).filter(), line);
    }

    public static void select(int i) {
        active = i;
        refreshChat();
    }

    public static void refreshChat() {
        if (active >= tabs.size()) active = 0;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) client.inGameHud.getChatHud().reset();
    }

    public static Tab find(String name) {
        for (Tab t : tabs) if (t.name().equalsIgnoreCase(name)) return t;
        return null;
    }

    // add, or change the filter of an existing tab
    public static void put(String name, String filter) {
        Tab old = find(name);
        if (old != null) tabs.set(tabs.indexOf(old), new Tab(old.name(), filter));
        else tabs.add(new Tab(name, filter));
        refreshChat();
    }

    public static boolean remove(String name) {
        Tab t = find(name);
        if (t == null) return false;
        tabs.remove(t);
        refreshChat();
        return true;
    }

    // just above the chat input box
    private static int y(Screen screen) { return screen.height - 14 - GAP - H; }

    private static String label(int i) { return i < tabs.size() ? tabs.get(i).name() : EDIT; }

    // the last one (i == tabs.size()) is the + button
    private static int tabAt(Screen screen, double mx, double my) {
        if (!enabled || my < y(screen) || my >= y(screen) + H) return -1;
        var font = MinecraftClient.getInstance().textRenderer;
        int x = 2;
        for (int i = 0; i <= tabs.size(); i++) {
            int w = font.getWidth(label(i)) + 8;
            if (mx >= x && mx < x + w) return i;
            x += w + GAP;
        }
        return -1;
    }

    private static void draw(Screen screen, DrawContext ctx, int mx, int my) {
        if (!enabled) return;
        var font = MinecraftClient.getInstance().textRenderer;
        int x = 2, y = y(screen);
        for (int i = 0; i <= tabs.size(); i++) {
            String name = label(i);
            int w = font.getWidth(name) + 8;
            boolean hot = mx >= x && mx < x + w && my >= y && my < y + H;
            ctx.fill(x, y, x + w, y + H, i == active ? 0xC0000000 : hot ? 0x90000000 : 0x60000000);
            if (i == active) ctx.fill(x, y + H - 1, x + w, y + H, 0xFFFFFFFF);
            ctx.drawTextWithShadow(font, name, x + 4, y + 2, i == active ? 0xFFFFFFFF : 0xFFAAAAAA);
            x += w + GAP;
        }
    }

    public static void load(JsonObject o) {
        enabled = !o.has("chatTabsEnabled") || o.get("chatTabsEnabled").getAsBoolean();
        tabs.clear();
        if (o.has("chatTabs")) {
            for (JsonElement e : o.getAsJsonArray("chatTabs")) {
                JsonObject t = e.getAsJsonObject();
                tabs.add(new Tab(t.get("name").getAsString(), t.has("filter") ? t.get("filter").getAsString() : ""));
            }
        } else {
            // isles chat lines are "<tag> name » message", server messages don't have the »
            tabs.add(new Tab("All", ""));
            tabs.add(new Tab("Chat", "»"));
            tabs.add(new Tab("Guild", "GUILD"));
            tabs.add(new Tab("Server", "-»"));
        }
        active = 0;
    }

    public static void save(JsonObject o) {
        o.addProperty("chatTabsEnabled", enabled);
        JsonArray arr = new JsonArray();
        for (Tab t : tabs) {
            JsonObject j = new JsonObject();
            j.addProperty("name", t.name());
            j.addProperty("filter", t.filter());
            arr.add(j);
        }
        o.add("chatTabs", arr);
    }
}
