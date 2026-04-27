package dev.mapselect.client;

import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheCosmetics;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.MapSelect;
import dev.mapselect.host.HostComponent;
import dev.mapselect.permissions.GexpressPermissions;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class DevWeaponModels implements ModelLoadingPlugin {
	public static final Identifier DEV_KNIFE_MODEL = Identifier.of(MapSelect.MOD_ID, "item/knife_dev");
	public static final Identifier DEV_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_dev");
	public static final Identifier HOST_KNIFE_MODEL = Identifier.of(MapSelect.MOD_ID, "item/knife_host");
	public static final Identifier HOST_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_host");
	public static final Identifier SHADOW_KNIFE_MODEL = Identifier.of(MapSelect.MOD_ID, "item/knife_shadow");
	public static final Identifier SHADOW_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_shadow");

	@Override
	public void onInitializeModelLoader(Context pluginContext) {
		pluginContext.addModels(DEV_KNIFE_MODEL, DEV_REVOLVER_MODEL, HOST_KNIFE_MODEL, HOST_REVOLVER_MODEL,
			SHADOW_KNIFE_MODEL, SHADOW_REVOLVER_MODEL);
	}

	public static BakedModel resolve(BakedModelManager manager, ItemStack stack, LivingEntity entity) {
		Identifier id = resolveId(stack, ClientPuppetmasterState.getRenderEntityFor(entity));
		if (id == null) return null;
		return ((FabricBakedModelManager) manager).getModel(id);
	}

	private static Identifier resolveId(ItemStack stack, LivingEntity entity) {
		if (ClientSilentShadowState.isShadowed(entity)) {
			if (stack.isOf(WatheItems.KNIFE)) return SHADOW_KNIFE_MODEL;
			if (stack.isOf(WatheItems.REVOLVER)) return SHADOW_REVOLVER_MODEL;
		}
		if (!usesDefaultWatheSkin(stack)) return null;

		WeaponSkin skin = resolveSkin(stack, entity);
		if (skin == WeaponSkin.NONE) return null;
		if (stack.isOf(WatheItems.KNIFE)) return skin == WeaponSkin.DEV ? DEV_KNIFE_MODEL : HOST_KNIFE_MODEL;
		if (stack.isOf(WatheItems.REVOLVER)) return skin == WeaponSkin.DEV ? DEV_REVOLVER_MODEL : HOST_REVOLVER_MODEL;
		return null;
	}

	private static WeaponSkin resolveSkin(ItemStack stack, LivingEntity entity) {
		if (entity instanceof PlayerEntity player) {
			if (GexpressPermissions.isDev(player)) return WeaponSkin.DEV;
			if (HostComponent.isHost(player)) return WeaponSkin.HOST;
		}
		String owner = stack.get(WatheDataComponentTypes.OWNER);
		if (GexpressPermissions.isDevUuidString(owner)) return WeaponSkin.DEV;
		return isHostUuidString(owner) ? WeaponSkin.HOST : WeaponSkin.NONE;
	}

	private static boolean usesDefaultWatheSkin(ItemStack stack) {
		if (!stack.isOf(WatheItems.KNIFE)) return true;
		try {
			return "default".equalsIgnoreCase(WatheCosmetics.getSkin(stack));
		} catch (Throwable ignored) {
			return true;
		}
	}

	private static boolean isHostUuidString(String uuid) {
		if (uuid == null || uuid.isBlank()) return false;
		try {
			if (MinecraftClient.getInstance().world == null) return false;
			HostComponent hosts = HostComponent.KEY.getNullable(MinecraftClient.getInstance().world);
			return hosts != null && hosts.isHost(UUID.fromString(uuid));
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	private enum WeaponSkin {
		NONE,
		DEV,
		HOST
	}
}
