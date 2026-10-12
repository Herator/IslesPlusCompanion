package com.islesplusplus.mixin;

import com.islesplusplus.itempickup.ItemPickupLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ItemPickupMixin {
    // HEAD, before the entity is removed. only the main thread pass, it runs on the network thread first
    @Inject(method = "onItemPickupAnimation", at = @At("HEAD"))
    private void islesplusplus$onItemPickup(ItemPickupAnimationS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread() || client.player == null || client.world == null) return;
        if (packet.getCollectorEntityId() != client.player.getId()) return;
        if (client.world.getEntityById(packet.getEntityId()) instanceof ItemEntity item) {
            ItemPickupLog.onPickup(item.getStack(), packet.getStackAmount());
        }
    }
}
