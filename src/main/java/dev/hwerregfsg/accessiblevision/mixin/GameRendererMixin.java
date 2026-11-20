package dev.hwerregfsg.accessiblevision.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;
import dev.hwerregfsg.accessiblevision.AccessibleVisionClient;

@Mixin(GameRenderer.class)
public final class GameRendererMixin {
	@Inject(method = "close", at = @At("RETURN"))
	private void accessiblevision$closeRenderer(CallbackInfo ci) {
		AccessibleVisionClient.getInstance().close();
	}
}
