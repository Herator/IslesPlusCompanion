package com.islesplusplus.mixin;

import com.islesplusplus.chattabs.ChatTabs;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// lines stay in the chat history, they're just not shown on this tab. switching tabs refreshes the chat
@Mixin(ChatHud.class)
public class ChatHudMixin {
    @Inject(method = "addVisibleMessage", at = @At("HEAD"), cancellable = true)
    private void islesplusplus$chatTab(ChatHudLine message, CallbackInfo ci) {
        if (ChatTabs.hides(message.content().getString())) ci.cancel();
    }
}
