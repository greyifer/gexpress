package dev.mapselect.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.modifier.ModifierUtils;
import dev.mapselect.registry.MapSelectModifiers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HeldItemFeatureRenderer.class)
public abstract class ParanoidHeldItemFeatureMixin {
	@WrapOperation(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;"))
	private ItemStack gexpress$renderParanoidHeldItem(LivingEntity instance, Operation<ItemStack> original,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		ItemStack stack = original.call(instance);
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || instance == client.player
				|| !ModifierUtils.has(client.player, MapSelectModifiers.PARANOID_ID)) {
			return stack;
		}
		if (!stack.isEmpty()) return stack;
		ItemStack fake = WatheItems.KNIFE.getDefaultStack();
		fake.set(WatheDataComponentTypes.OWNER, instance.getUuidAsString());
		return fake;
	}
}
