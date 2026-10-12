package com.islesplusplus.mixin;

import com.islesplus.features.inventorysearch.InventorySearch;
import com.islesplus.features.itembrowser.ItemBrowser;
import com.islesplus.features.itembrowser.WikiCatalog;
import com.islesplusplus.BrowserCollapse;
import com.islesplusplus.IslesPlusPlus;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// collapse button + bookmark key on the Isles+ item browser (bookmarks are its pins)
@Mixin(value = ItemBrowser.class, remap = false)
public abstract class ItemBrowserMixin {
    @Shadow private static WikiCatalog.Item hoveredItem(Screen screen) { throw new AssertionError(); }
    @Shadow private static void togglePin(WikiCatalog.Item item) { throw new AssertionError(); }

    // collapsed = Isles+ thinks the browser is off, so the search box goes back to its usual spot
    @Inject(method = "active", at = @At("HEAD"), cancellable = true)
    private static void islesplusplus$collapsed(CallbackInfoReturnable<Boolean> cir) {
        if (BrowserCollapse.collapsed) cir.setReturnValue(false);
    }

    @Inject(method = "computeLayout", at = @At("HEAD"))
    private static void islesplusplus$resetFits(HandledScreen<?> screen, CallbackInfoReturnable<?> cir) {
        BrowserCollapse.fits = false;
    }

    // make room for the button at the right end of the search box
    @ModifyArg(method = "computeLayout",
        at = @At(value = "INVOKE", target = "Lcom/islesplus/features/itembrowser/ItemBrowser$Layout;<init>(IIIIIIIIIIIII)V"),
        index = 6)
    private static int islesplusplus$shrinkBox(int x0, int y0, int w, int h, int boxX, int boxY, int boxW,
                                               int gridX, int gridY, int cols, int rows, int footY, int cell) {
        BrowserCollapse.fits = true;
        BrowserCollapse.buttonY = boxY;
        return boxW - BrowserCollapse.SIZE - 2;
    }

    // before the search box, so item tooltips still draw over the button
    @Inject(method = "renderUi", at = @At(value = "INVOKE",
        target = "Lcom/islesplus/features/inventorysearch/InventorySearch;drawBox(Lnet/minecraft/client/gui/DrawContext;IIILjava/lang/String;)V"))
    private static void islesplusplus$drawButton(HandledScreen<?> screen, DrawContext ctx, int mx, int my, CallbackInfo ci) {
        BrowserCollapse.draw(screen, ctx, mx, my);
    }

    @Inject(method = "onMouseClick", at = @At("HEAD"), cancellable = true)
    private static void islesplusplus$click(Screen screen, Click click, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() == 0 && BrowserCollapse.over(screen, click.x(), click.y())) {
            BrowserCollapse.toggle();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onKeyPress", at = @At("HEAD"), cancellable = true)
    private static void islesplusplus$bookmark(Screen screen, KeyInput key, CallbackInfoReturnable<Boolean> cir) {
        if (!ItemBrowser.active() || InventorySearch.focused() || !IslesPlusPlus.BOOKMARK_KEY.matchesKey(key)) return;
        WikiCatalog.Item item = hoveredItem(screen);
        if (item == null) return;
        togglePin(item);   // saves the Isles+ config itself
        cir.setReturnValue(false);
    }
}
