package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientPuppetmasterState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(InGameHud.class)
public abstract class PuppetmasterHotbarRenderMixin {
	@ModifyArgs(
		method = "renderHotbar",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHotbarItem(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/client/render/RenderTickCounter;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;I)V")
	)
	private void gexpress$renderPuppetHotbarStacks(Args args) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!ClientPuppetmasterState.isLocalController(client) || !ClientPuppetmasterState.hasSyncedHotbar()) return;
		int seed = args.get(6);
		if (seed >= 1 && seed <= 9) {
			args.set(5, ClientPuppetmasterState.getHotbarStack(seed - 1));
			AbstractClientPlayerEntity target = ClientPuppetmasterState.getTargetPlayer(client);
			if (target != null) args.set(4, target);
		}
	}
}
