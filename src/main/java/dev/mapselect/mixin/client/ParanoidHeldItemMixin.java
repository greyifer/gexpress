package dev.mapselect.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.modifier.ModifierUtils;
import dev.mapselect.registry.MapSelectModifiers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntityRenderer.class)
public abstract class ParanoidHeldItemMixin {
	@ModifyExpressionValue(method = "getArmPose", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getStackInHand(Lnet/minecraft/util/Hand;)Lnet/minecraft/item/ItemStack;"))
	private static ItemStack gexpress$paranoidAlwaysSeesItems(ItemStack original, AbstractClientPlayerEntity player, Hand hand) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (hand != Hand.MAIN_HAND || client == null || client.player == null || player == null
				|| player == client.player || !ModifierUtils.has(client.player, MapSelectModifiers.PARANOID_ID)) {
			return original;
		}
		if (!original.isEmpty()) return original;
		ItemStack fake = WatheItems.KNIFE.getDefaultStack();
		fake.set(WatheDataComponentTypes.OWNER, player.getUuidAsString());
		return fake;
	}
}
