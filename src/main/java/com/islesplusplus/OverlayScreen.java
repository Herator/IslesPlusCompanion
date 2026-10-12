package com.islesplusplus;

import com.islesplus.ui.Fonts;
import com.islesplus.ui.OverlayHost;
import com.islesplus.ui.Theme;
import com.islesplus.ui.Widget;
import com.islesplusplus.map.IslesMap;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

// a screen that's just Isles+ dialogs stacked on each other: a list at the bottom, edit dialogs over it
public abstract class OverlayScreen extends Screen implements OverlayHost {
    private final Screen parent;
    private final List<Widget> overlays = new ArrayList<>();

    protected OverlayScreen(String title, Screen parent) {
        super(Text.literal(title));
        this.parent = parent;
    }

    @Override public void openOverlay(Widget overlay) { overlays.add(overlay); }

    // closing the bottom one closes the screen
    @Override public void closeOverlay(Widget overlay) {
        int i = overlays.indexOf(overlay);
        if (i < 0) return;
        while (overlays.size() > i) overlays.remove(overlays.size() - 1).unfocus();
        if (overlays.isEmpty()) close();
    }

    protected boolean hasOverlays() { return !overlays.isEmpty(); }

    @Override public int screenWidth() { return width; }
    @Override public int screenHeight() { return height; }
    @Override public void closeScreen() { close(); }

    @Override
    public void close() {
        for (Widget o : new ArrayList<>(overlays)) o.unfocus();
        overlays.clear();
        if (client != null) client.setScreen(parent);
    }

    private Widget top() { return overlays.isEmpty() ? null : overlays.get(overlays.size() - 1); }

    @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {}   // the dialogs bring their scrim

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Fonts.islesUi(() -> {
            for (Widget o : new ArrayList<>(overlays)) {
                ctx.createNewRootLayer();
                boolean isTop = o == top();
                o.layout(0, 0, width);
                o.render(ctx, isTop ? mouseX : -1, isTop ? mouseY : -1);
            }
        });
    }

    @Override public boolean mouseClicked(Click click, boolean doubled) {
        Widget t = top();
        if (t != null) Fonts.islesUi(() -> t.mouseClicked(click.x(), click.y(), click.button()));
        return true;
    }

    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        Widget t = top();
        if (t != null) Fonts.islesUi(() -> t.mouseDragged(click.x(), click.y()));
        return true;
    }

    @Override public boolean mouseReleased(Click click) {
        Widget t = top();
        if (t != null) Fonts.islesUi(t::mouseReleased);
        return true;
    }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        Widget t = top();
        if (t != null) Fonts.islesUi(() -> t.mouseScrolled(mx, my, vertical));
        return true;
    }

    @Override public boolean keyPressed(KeyInput input) {
        Widget t = top();
        if (t == null) return super.keyPressed(input);
        Fonts.islesUi(() -> {
            if (!t.keyPressed(input) && input.key() == GLFW.GLFW_KEY_ESCAPE) closeOverlay(t);
        });
        return true;
    }

    @Override public boolean charTyped(CharInput input) {
        Widget t = top();
        if (t != null) Fonts.islesUi(() -> t.charTyped(input));
        return true;
    }

    @Override public boolean shouldPause() { return false; }

    // a widget around another one: input goes straight through to it unless overridden
    abstract static class Wrap extends Widget {
        protected final Widget child;
        Wrap(Widget child) { this.child = child; }

        @Override public boolean mouseClicked(double mx, double my, int button) { return child.mouseClicked(mx, my, button); }
        @Override public boolean mouseDragged(double mx, double my) { return child.mouseDragged(mx, my); }
        @Override public void mouseReleased() { child.mouseReleased(); }
        @Override public boolean mouseScrolled(double mx, double my, double amount) { return child.mouseScrolled(mx, my, amount); }
        @Override public boolean keyPressed(KeyInput in) { return child.keyPressed(in); }
        @Override public boolean charTyped(CharInput in) { return child.charTyped(in); }
        @Override public void unfocus() { child.unfocus(); }
    }

    // scrolls when it's too long
    protected final class Scroll extends Wrap {
        private static final int STEP = 12;
        private int scroll, contentH;
        public Scroll(Widget child) { super(child); }

        @Override public int layout(int x, int y, int width) {
            this.x = x; this.y = y; this.w = width;
            contentH = child.layout(x, 0, width);
            h = Math.min(contentH, Math.max(40, height - 90));
            scroll = Math.max(0, Math.min(scroll, contentH - h));
            child.layout(x, y - scroll, width);
            return h;
        }

        @Override public void render(DrawContext ctx, int mouseX, int mouseY) {
            boolean in = contains(mouseX, mouseY);
            ctx.enableScissor(x - 1, y, x + w + 1, y + h);
            child.render(ctx, in ? mouseX : -1, in ? mouseY : -1);
            ctx.disableScissor();
            if (contentH > h) {
                int thumb = Math.max(8, h * h / contentH);
                int ty = y + scroll * (h - thumb) / (contentH - h);
                ctx.fill(x + w + 2, ty, x + w + 4, ty + thumb, Theme.TEXT_META);
            }
        }

        @Override public boolean mouseClicked(double mx, double my, int button) {
            if (!contains(mx, my)) { child.unfocus(); return false; }
            return child.mouseClicked(mx, my, button);
        }
        @Override public boolean mouseScrolled(double mx, double my, double amount) {
            if (contentH <= h) return child.mouseScrolled(mx, my, amount);
            scroll -= (int) Math.signum(amount) * STEP;
            return true;
        }
    }

    // a list row with no box, a carved line under it, lit under the mouse
    protected static final class Row extends Wrap {
        private static final int PAD_X = 3, PAD_Y = 5;
        private static final int HOVER = 0x24FFF4D8, GROOVE = 0x50704A28, GROOVE_LIT = 0x60FFF8E8;
        public Row(Widget child) { super(child); }

        @Override public int layout(int x, int y, int width) {
            this.x = x; this.y = y; this.w = width;
            h = child.layout(x + PAD_X, y + PAD_Y, width - 2 * PAD_X) + 2 * PAD_Y + 2;
            return h;
        }

        @Override public void render(DrawContext ctx, int mouseX, int mouseY) {
            if (contains(mouseX, mouseY)) ctx.fill(x, y, x + w, y + h - 2, HOVER);
            child.render(ctx, mouseX, mouseY);
            ctx.fill(x, y + h - 2, x + w, y + h - 1, GROOVE);
            ctx.fill(x, y + h - 1, x + w, y + h, GROOVE_LIT);
        }
    }

    // a row's diamond marker, as tall as a name + meta pair so it sits in the middle
    protected static final class Marker extends Widget {
        private static final int W = 9;
        private final java.util.function.IntSupplier rgb;
        public Marker(java.util.function.IntSupplier rgb) { this.rgb = rgb; }

        @Override public int prefWidth() { return W; }
        @Override public int layout(int x, int y, int width) {
            this.x = x; this.y = y; w = W;
            h = Fonts.height(Fonts.BODY) + 2 + Fonts.height(Fonts.SMALL);
            return h;
        }

        @Override public void render(DrawContext ctx, int mouseX, int mouseY) {
            IslesMap.diamond(ctx, x + W / 2, y + h / 2, 3, 0xFF000000 | rgb.getAsInt(), Theme.INK_DEEP);
        }
    }
}
