package com.islesplusplus;

import com.islesplus.ui.Draw;
import com.islesplus.ui.Fonts;
import com.islesplus.ui.OverlayHost;
import com.islesplus.ui.Theme;
import com.islesplus.ui.Widget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;

// a dialog on a paper scroll, drawn in code (no textures), unrolls when it opens. replaces the Isles+ Dialog
public final class ScrollDialog extends OverlayScreen.Wrap {
    private static final float INSET_RATIO = 0.05f;
    private static final int ROLL_H = 10, ROLL_OVERHANG = 3, KNOB_W = 6, KNOB_OUT = 2;
    private static final int TITLE_GAP = 6, PAD_TOP = 6, PAD_BOTTOM = 10, CLOSE = 9;
    private static final long UNROLL_MS = 150;
    // the "Unroll animation" setting (Map row): off and scrolls and the map just appear
    public static boolean unroll = true;

    private static final int PAPER = 0xFFE9DBB2, PAPER_EDGE = 0xFFDCCA9C, PAPER_TEAR = 0xFFC2A974;
    private static final int FLECK_LIGHT = 0xFFF2E8CC, FLECK_DARK = 0xFFDFCFA5;
    // the rolled paper, top row to bottom: lit on top, rounding off into shadow
    private static final int[] ROLL_ROWS = {0xFF4A3420, 0xFFF6EDD3, 0xFFEFE2BF, 0xFFE6D6AC, 0xFFE0CEA2,
        0xFFD6C192, 0xFFCAB283, 0xFFB89F70, 0xFF9C845A, 0xFF4A3420};
    private static final int WOOD = 0xFF8B5A2F, WOOD_LIT = 0xFFB27A45, WOOD_SHADE = 0xFF5F3B1D, WOOD_INK = 0xFF3B2414;
    private static final int SHADOW = 0x40000000;

    private final OverlayHost host;
    private final String title;
    private final long openedAt = Util.getMeasuringTimeMs();
    private int contentW = 300;
    private int sheetX, sheetY, sheetW, sheetH, titleY, closeX, closeY;

    public ScrollDialog(OverlayHost host, String title, Widget body) {
        super(body);
        this.host = host;
        this.title = title;
    }

    // width of the content, not counting the paper's margins
    public ScrollDialog width(int w) {
        contentW = w;
        return this;
    }

    @Override
    public int layout(int x, int y, int width) {
        int inset = Math.max(10, Math.round(contentW * INSET_RATIO / (1 - 2 * INSET_RATIO)));
        sheetW = Math.min(contentW + 2 * inset, host.screenWidth() - 2 * ROLL_OVERHANG - 8);
        int innerW = sheetW - 2 * inset;
        int titleH = Fonts.height(Fonts.TITLE);
        int bodyH = child.layout(0, 0, innerW);
        sheetH = ROLL_H / 2 + PAD_TOP + titleH + TITLE_GAP + bodyH + PAD_BOTTOM + ROLL_H / 2;
        sheetX = (host.screenWidth() - sheetW) / 2;
        sheetY = Math.max(ROLL_H / 2, (host.screenHeight() - sheetH) / 2);
        titleY = sheetY + ROLL_H / 2 + PAD_TOP;
        child.layout(sheetX + inset, titleY + titleH + TITLE_GAP, innerW);
        closeX = sheetX + sheetW - inset - CLOSE;
        closeY = titleY + (titleH - CLOSE) / 2;
        this.x = sheetX - ROLL_OVERHANG - KNOB_W;
        this.y = sheetY - ROLL_H / 2 - KNOB_OUT;
        this.w = sheetW + 2 * (ROLL_OVERHANG + KNOB_W);
        this.h = sheetH + ROLL_H + 2 * KNOB_OUT;
        return h;
    }

    // 0..1, how far it has unrolled
    private float open() {
        return progress(openedAt, UNROLL_MS);
    }

    // 0..1 since openedAt, 1 straight away with the setting off
    public static float progress(long openedAt, long ms) {
        return unroll ? Math.min(1f, (Util.getMeasuringTimeMs() - openedAt) / (float) ms) : 1f;
    }

    // ease out, so it settles rather than stops
    private static float eased(float t) {
        return 1 - (1 - t) * (1 - t);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY) {
        ctx.fill(0, 0, host.screenWidth(), host.screenHeight(), Theme.SCRIM);
        float t = open();
        sheetStart(ctx, sheetX, sheetY, sheetW, sheetH, t);
        Fonts.drawHeading(ctx, title, sheetX + (sheetW - Fonts.width(title, Fonts.TITLE)) / 2, titleY, Theme.TEXT_TITLE, Fonts.TITLE);
        boolean overClose = t >= 1f && onClose(mouseX, mouseY);
        Draw.cross(ctx, closeX + CLOSE / 2, closeY + CLOSE / 2, overClose ? Theme.OXBLOOD : Theme.TEXT_META);
        if (t >= 1f) child.render(ctx, mouseX, mouseY);
        else child.render(ctx, -1, -1);
        sheetEnd(ctx, sheetX, sheetY, sheetW, sheetH, t);
    }

    // room a sheet's rollers and knobs take past its edges
    public static final int ROLL_OUT_X = ROLL_OVERHANG + KNOB_W;

    // a sheet t of the way open: sheetStart draws paper and clips to the open band, sheetEnd draws the rollers
    public static void sheetStart(DrawContext ctx, int x, int y, int w, int h, float t) {
        int shown = Math.round(h * eased(t)), top = y + (h - shown) / 2;
        if (t < 1f) ctx.enableScissor(x - 1, top, x + w + 1, top + shown);
        paper(ctx, x, y, w, h, top - y, top - y + shown);
    }

    public static void sheetEnd(DrawContext ctx, int x, int y, int w, int h, float t) {
        int shown = Math.round(h * eased(t)), top = y + (h - shown) / 2, bottom = top + shown;
        if (t < 1f) ctx.disableScissor();
        ctx.fill(x - ROLL_OVERHANG + 1, bottom + ROLL_H / 2, x + w + ROLL_OVERHANG - 1, bottom + ROLL_H / 2 + 2, SHADOW);
        roll(ctx, x - ROLL_OVERHANG, top - ROLL_H / 2, w + 2 * ROLL_OVERHANG);
        roll(ctx, x - ROLL_OVERHANG, bottom - ROLL_H / 2, w + 2 * ROLL_OVERHANG);
    }

    // fades the paper in over the edge of what's drawn on the sheet, so it doesn't end in a hard line
    public static void fadeEdges(DrawContext ctx, int x, int y, int w, int h, int px) {
        for (int i = 0; i < px; i++) {
            int c = (255 * (px - i) / (px + 1)) << 24 | (PAPER & 0xFFFFFF);
            int l = x + i, r = x + w - i, t = y + i, b = y + h - i;
            ctx.fill(l, t, r, t + 1, c);
            ctx.fill(l, b - 1, r, b, c);
            ctx.fill(l, t + 1, l + 1, b - 1, c);
            ctx.fill(r - 1, t + 1, r, b - 1, c);
        }
    }

    // same every frame for the same spot, so the flecks and tears hold still
    private static int hash(int a, int b) {
        int h = a * 73856093 ^ b * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ h >>> 15) & 0x7fffffff;
    }

    // parchment rows from..to: torn sides, a darker band along them, faint flecks
    private static void paper(DrawContext ctx, int x, int y, int w, int h, int from, int to) {
        h = Math.min(h, to);
        ctx.fill(x + 2, y + from, x + w - 2, y + h, PAPER);
        for (int row = from & ~1; row < h; row += 2) {
            int l = hash(row / 2, 1) % 3, r = hash(row / 2, 2) % 3, yy = y + row, y2 = Math.min(y + h, yy + 2);
            ctx.fill(x + l, yy, x + l + 1, y2, PAPER_TEAR);
            ctx.fill(x + l + 1, yy, x + l + 3, y2, PAPER_EDGE);
            ctx.fill(x + w - r - 1, yy, x + w - r, y2, PAPER_TEAR);
            ctx.fill(x + w - r - 3, yy, x + w - r - 1, y2, PAPER_EDGE);
        }
        for (int cy = Math.max(2, from - from % 5 + 2); cy < h - 2; cy += 5) {
            for (int cx = 6; cx < w - 8; cx += 7) {
                int v = hash(cx, cy) % 11;
                if (v > 1) continue;
                int fx = x + cx + hash(cy, cx) % 4, fw = 2 + hash(cx + 1, cy) % 3;
                ctx.fill(fx, y + cy, fx + fw, y + cy + 1, v == 0 ? FLECK_LIGHT : FLECK_DARK);
            }
        }
    }

    // the rolled-up end: a paper tube shaded top to bottom, a wooden knob sticking out each side
    private static void roll(DrawContext ctx, int x, int y, int w) {
        for (int i = 0; i < ROLL_ROWS.length; i++) ctx.fill(x, y + i, x + w, y + i + 1, ROLL_ROWS[i]);
        ctx.fill(x, y + 1, x + 1, y + ROLL_H - 1, ROLL_ROWS[0]);
        ctx.fill(x + w - 1, y + 1, x + w, y + ROLL_H - 1, ROLL_ROWS[0]);
        knob(ctx, x - KNOB_W, y - KNOB_OUT);
        knob(ctx, x + w, y - KNOB_OUT);
    }

    private static void knob(DrawContext ctx, int x, int y) {
        int h = ROLL_H + 2 * KNOB_OUT;
        Draw.ring(ctx, x + 1, y + 1, KNOB_W - 2, h - 2, WOOD_INK);
        ctx.fill(x + 1, y + 1, x + KNOB_W - 1, y + h - 1, WOOD);
        ctx.fill(x + 1, y + 1, x + 2, y + h - 1, WOOD_LIT);
        ctx.fill(x + KNOB_W - 2, y + 1, x + KNOB_W - 1, y + h - 1, WOOD_SHADE);
        ctx.fill(x + 1, y + h / 2, x + KNOB_W - 1, y + h / 2 + 1, WOOD_SHADE);   // a turned groove round the middle
    }

    private boolean onClose(double mx, double my) {
        return mx >= closeX - 2 && mx < closeX + CLOSE + 2 && my >= closeY - 2 && my < closeY + CLOSE + 2;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (open() < 1f) return true;
        if (button == 0 && onClose(mx, my)) {
            host.closeOverlay(this);
            return true;
        }
        child.mouseClicked(mx, my, button);
        return true;
    }
}
