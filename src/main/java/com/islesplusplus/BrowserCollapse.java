package com.islesplusplus;

import com.islesplus.IslesClient;
import com.islesplus.IslesPlusConfig;
import com.islesplus.features.itembrowser.ItemBrowser;
import com.islesplus.hud.HudLayout;
import com.islesplus.mixin.HandledScreenAccessor;
import com.islesplus.sync.FeatureFlags;
import com.islesplus.ui.Draw;
import com.islesplus.ui.Fonts;
import com.islesplus.world.PlayerWorld;
import com.islesplus.world.WorldIdentification;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

// the little ‹ › button that folds the Isles+ item browser away (see ItemBrowserMixin)
public final class BrowserCollapse {
    public static final int SIZE = 14;
    public static boolean collapsed;
    public static boolean fits;      // set by the browser's own layout, only meaningful while open
    public static int buttonY = 8;   // remembered from the open layout so the button doesn't jump

    private BrowserCollapse() {}

    public static void toggle() {
        collapsed ^= true;
        IslesClient.playMenuClickSound();
        IslesPlusConfig.save();
    }

    private static boolean shown(Screen screen) {
        if (!(screen instanceof HandledScreen<?> hs)) return false;
        if (!collapsed) return ItemBrowser.active() && fits;
        // same checks as ItemBrowser.active(), minus our collapse
        if (!ItemBrowser.enabled || FeatureFlags.isKilled(ItemBrowser.KILL_KEY) || WorldIdentification.world == PlayerWorld.OTHER) return false;
        HandledScreenAccessor acc = (HandledScreenAccessor) hs;
        return hs.width - (acc.getGuiX() + acc.getGuiWidth()) - 16 >= ItemBrowser.MIN_PANEL;
    }

    private static int x(Screen screen) { return screen.width - 8 - SIZE; }

    // collapsed, Isles+ moves the search bar back to its HUD spot: keep it next to the expand button
    public static HudLayout.Box searchBox(HudLayout.Box placed) {
        Screen screen = MinecraftClient.getInstance().currentScreen;
        if (!collapsed || !shown(screen)) return placed;
        return new HudLayout.Box(x(screen) - 2 - placed.w(), buttonY, placed.w(), placed.h(), placed.scale(), placed.opacity());
    }

    public static boolean over(Screen screen, double mx, double my) {
        return shown(screen) && mx >= x(screen) && mx < x(screen) + SIZE && my >= buttonY && my < buttonY + 16;
    }

    public static void draw(Screen screen, DrawContext ctx, int mx, int my) {
        if (!shown(screen)) return;
        int x = x(screen);
        // colors match the browser's calculator button
        Draw.bevel(ctx, x, buttonY, SIZE, 16, over(screen, mx, my) ? -8698564 : -9553101, -8040379, -12639715, -12965352);
        Fonts.drawControlCentered(ctx, collapsed ? "‹" : "›", x + SIZE / 2, buttonY + 4, -594732, 1f);
    }
}
