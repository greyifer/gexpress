package dev.mapselect.mixin.client;

import dev.mapselect.client.role.puppetmaster.ClientPuppetmasterState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(HeldItemRenderer.class)
public abstract class PuppetmasterHeldItemRendererMixin {
	@Shadow private ItemStack mainHand;
	@Shadow private ItemStack offHand;
	@Shadow private float equipProgressMainHand;
	@Shadow private float prevEquipProgressMainHand;
	@Shadow private float equipProgressOffHand;
	@Shadow private float prevEquipProgressOffHand;

	@Inject(method = "updateHeldItems", at = @At("HEAD"), cancellable = true)
	private void gexpress$updatePuppetHeldItems(CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		AbstractClientPlayerEntity target = ClientPuppetmasterState.getTargetPlayer(client);
		if (target == null) return;
		ItemStack desiredMain = ClientPuppetmasterState.getSelectedStack();
		if (desiredMain.isEmpty()) desiredMain = target.getMainHandStack();
		ItemStack desiredOff = target.getOffHandStack();
		prevEquipProgressMainHand = equipProgressMainHand;
		prevEquipProgressOffHand = equipProgressOffHand;
		boolean sameMain = ItemStack.areEqual(mainHand, desiredMain);
		boolean sameOff = ItemStack.areEqual(offHand, desiredOff);
		equipProgressMainHand += MathHelper.clamp((sameMain ? 1.0F : 0.0F) - equipProgressMainHand,
			-0.4F, 0.4F);
		equipProgressOffHand += MathHelper.clamp((sameOff ? 1.0F : 0.0F) - equipProgressOffHand,
			-0.4F, 0.4F);
		if (equipProgressMainHand < 0.10F) mainHand = desiredMain;
		if (equipProgressOffHand < 0.10F) offHand = desiredOff;
		ci.cancel();
	}

	@ModifyArgs(
		method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V")
	)
	private void gexpress$renderPuppetHandsAndStack(Args args) {
		AbstractClientPlayerEntity target = ClientPuppetmasterState.getTargetPlayer(MinecraftClient.getInstance());
		if (target == null) return;
		args.set(0, target);
		Hand hand = args.get(3);
		ItemStack stack = hand == Hand.MAIN_HAND
			? ClientPuppetmasterState.getSelectedStack()
			: target.getOffHandStack();
		if (stack.isEmpty()) {
			stack = target.getStackInHand(hand);
		}
		args.set(5, stack);
	}
}
