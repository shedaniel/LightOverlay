package me.shedaniel.lightoverlay.mixin;

import me.shedaniel.lightoverlay.LightOverlay;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
    
    @Inject(method = "handleInputEvents()V", at = @At("RETURN"))
    private void handleInputEvents(CallbackInfo callbackInfo) {
        while (LightOverlay.getEnableOverlay().wasPressed())
            LightOverlay.setEnabled(!LightOverlay.isEnabled());
    }
    
}
