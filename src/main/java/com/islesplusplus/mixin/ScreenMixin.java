package com.islesplusplus.mixin;

import com.islesplus.ui.Fonts;
import com.islesplusplus.BrowserCollapse;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// while the item browser is collapsed Isles+ draws nothing there, so the expand button is ours to draw
@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "renderWithTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawDeferredElements()V"))
    private void islesplusplus$drawExpandButton(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!BrowserCollapse.collapsed || !((Object) this instanceof HandledScreen)) return;
        ctx.createNewRootLayer();
        Fonts.islesUi(() -> BrowserCollapse.draw((Screen) (Object) this, ctx, mouseX, mouseY));
    }
}
