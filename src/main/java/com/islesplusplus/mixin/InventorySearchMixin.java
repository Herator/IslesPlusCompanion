package com.islesplusplus.mixin;

import com.islesplus.features.inventorysearch.InventorySearch;
import com.islesplus.hud.HudElement;
import com.islesplus.hud.HudLayout;
import com.islesplusplus.BrowserCollapse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// the search bar stays put when the item browser is collapsed (see BrowserCollapse.searchBox)
@Mixin(value = InventorySearch.class, remap = false)
public abstract class InventorySearchMixin {
    @Redirect(method = {"onRender", "onMouseClick"},
        at = @At(value = "INVOKE", target = "Lcom/islesplus/hud/HudLayout;place(Lcom/islesplus/hud/HudElement;IIZ)Lcom/islesplus/hud/HudLayout$Box;"))
    private static HudLayout.Box islesplusplus$besideBrowser(HudElement element, int w, int h, boolean editing) {
        return BrowserCollapse.searchBox(HudLayout.place(element, w, h, editing));
    }
}
