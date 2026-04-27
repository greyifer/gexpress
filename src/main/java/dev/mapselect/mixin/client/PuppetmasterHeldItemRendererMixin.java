package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientPuppetmasterState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(HeldItemRenderer.class)
public abstract class PuppetmasterHeldItemRendererMixin {
	@ModifyArgs(
		method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V")
	)
	private void gexpress$renderPuppetHandsAndStack(Args args) {
		AbstractClientPlayerEntity target = ClientPuppetmasterState.getTargetPlayer(MinecraftClient.getInstance());
		if (target == null) return;
		args.set(0, target);
	}
}
