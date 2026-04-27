package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.client.DevWeaponModels;
import dev.mapselect.client.ClientSilentShadowState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.item.ItemModels;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.List;

@Mixin(ItemRenderer.class)
public abstract class DevWeaponItemRendererMixin {
	@Unique private static final ThreadLocal<Boolean> GEXPRESS_SHADOW_WEAPON_RENDER = ThreadLocal.withInitial(() -> false);

	@Shadow
	public abstract ItemModels getModels();

	@Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
	private void gexpress$devWeaponModel(ItemStack stack, World world, LivingEntity entity, int seed,
			CallbackInfoReturnable<BakedModel> cir) {
		BakedModel model = DevWeaponModels.resolve(this.getModels().getModelManager(), stack, entity);
		if (model != null) {
			cir.setReturnValue(model);
		}
	}

	@Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V",
		at = @At("HEAD"))
	private void gexpress$beginShadowWeaponRender(LivingEntity entity, ItemStack stack,
			ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices,
			net.minecraft.client.render.VertexConsumerProvider vertexConsumers, World world,
			int light, int overlay, int seed, CallbackInfo ci) {
		GEXPRESS_SHADOW_WEAPON_RENDER.set(shouldShadowWeapon(entity, stack));
	}

	@Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V",
		at = @At("RETURN"))
	private void gexpress$endShadowWeaponRender(LivingEntity entity, ItemStack stack,
			ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices,
			net.minecraft.client.render.VertexConsumerProvider vertexConsumers, World world,
			int light, int overlay, int seed, CallbackInfo ci) {
		GEXPRESS_SHADOW_WEAPON_RENDER.remove();
	}

	@ModifyArgs(
		method = "renderBakedItemQuads",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;FFFFII)V")
	)
	private void gexpress$shadowWeaponQuadColor(Args args, MatrixStack matrices, VertexConsumer vertices,
			List<BakedQuad> quads, ItemStack stack, int light, int overlay) {
		if (!GEXPRESS_SHADOW_WEAPON_RENDER.get()) return;

		float alpha = ((ClientSilentShadowState.shadowColor() >>> 24) & 0xFF) / 255.0F;
		args.set(2, 0.0F); // red
		args.set(3, 0.0F); // green
		args.set(4, 0.0F); // blue
		args.set(5, alpha);
	}

	@Unique
	private static boolean shouldShadowWeapon(LivingEntity entity, ItemStack stack) {
		if (entity == null || stack == null || stack.isEmpty()) return false;
		if (!ClientSilentShadowState.isShadowed(entity)) return false;
		return stack.isOf(WatheItems.KNIFE) || stack.isOf(WatheItems.REVOLVER);
	}
}
