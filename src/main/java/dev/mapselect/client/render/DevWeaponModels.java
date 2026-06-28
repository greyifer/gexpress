package dev.mapselect.client.render;
import dev.mapselect.client.role.puppetmaster.ClientPuppetmasterState;
import dev.mapselect.client.role.silent.ClientSilentShadowState;


import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheCosmetics;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.host.HostComponent;
import dev.mapselect.host.TrustedComponent;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.skin.PlayerSkinComponent;
import dev.mapselect.skin.WeaponSkin;
import dev.mapselect.skin.WeaponSkinType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.UUID;
import java.util.Arrays;

public final class DevWeaponModels implements ModelLoadingPlugin {
	public static final Identifier DEV_KNIFE_MODEL = Identifier.of(MapSelect.MOD_ID, "item/knife_dev");
	public static final Identifier DEV_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_dev");
	public static final Identifier HOST_KNIFE_MODEL = Identifier.of(MapSelect.MOD_ID, "item/knife_host");
	public static final Identifier HOST_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_host");
	public static final Identifier TRUSTED_KNIFE_MODEL = Identifier.of(MapSelect.MOD_ID, "item/knife_trusted");
	public static final Identifier TRUSTED_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_trusted");
	public static final Identifier PASSENGER_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_passenger");
	public static final Identifier COLA_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_cola");
	public static final Identifier GOLD_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_gold");
	public static final Identifier JEM_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_jem");
	public static final Identifier BLUE_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_blue");
	public static final Identifier PINK_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_pink");
	public static final Identifier PLAID_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_plaid");
	public static final Identifier BAMBOO_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_bamboo");
	public static final Identifier ATOMIZER_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_atomizer");
	public static final Identifier SHADOW_KNIFE_MODEL = Identifier.of(MapSelect.MOD_ID, "item/knife_shadow");
	public static final Identifier SHADOW_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_shadow");
	private static final Identifier NOELLES_FAKE_KNIFE = Identifier.of("noellesroles", "fake_knife");
	public static final String SKIN_PREVIEW_KEY = "gexpress_skin_preview";

	@Override
	public void onInitializeModelLoader(Context pluginContext) {
		pluginContext.addModels(DEV_KNIFE_MODEL, DEV_REVOLVER_MODEL, HOST_KNIFE_MODEL, HOST_REVOLVER_MODEL,
			TRUSTED_KNIFE_MODEL, TRUSTED_REVOLVER_MODEL, PASSENGER_REVOLVER_MODEL, COLA_REVOLVER_MODEL,
			GOLD_REVOLVER_MODEL, JEM_REVOLVER_MODEL, BLUE_REVOLVER_MODEL, PINK_REVOLVER_MODEL,
			PLAID_REVOLVER_MODEL, BAMBOO_REVOLVER_MODEL, ATOMIZER_REVOLVER_MODEL,
			SHADOW_KNIFE_MODEL, SHADOW_REVOLVER_MODEL);
		Identifier[] imported = Arrays.stream(WeaponSkin.values())
			.flatMap(skin -> Arrays.stream(WeaponSkinType.values()).map(skin::model))
			.filter(java.util.Objects::nonNull)
			.distinct()
			.toArray(Identifier[]::new);
		if (imported.length > 0) pluginContext.addModels(imported);
	}

	public static BakedModel resolve(BakedModelManager manager, ItemStack stack, LivingEntity entity) {
		Identifier id = resolveId(stack, ClientPuppetmasterState.getRenderEntityFor(entity));
		if (id == null) return null;
		return ((FabricBakedModelManager) manager).getModel(id);
	}

	private static Identifier resolveId(ItemStack stack, LivingEntity entity) {
		if (ClientSilentShadowState.isShadowed(entity)) {
			if (isKnifeLike(stack)) return SHADOW_KNIFE_MODEL;
			if (isGunLike(stack)) return SHADOW_REVOLVER_MODEL;
		}
		if (!usesDefaultWatheSkin(stack)) return null;

		WeaponSkinType type = isKnifeLike(stack) ? WeaponSkinType.KNIFE
			: isGunLike(stack) ? WeaponSkinType.GUN : null;
		WeaponSkin skin = resolveSkin(stack, entity);
		if (skin == null || !skin.supports(type)) return null;
		Identifier importedModel = skin.model(type);
		if (importedModel != null) {
			return type != WeaponSkinType.GUN || GexpressConfig.use3dGunSkins() ? importedModel : null;
		}
		if (isKnifeLike(stack)) {
			if (skin == WeaponSkin.DEFAULT) return null;
			if (skin == WeaponSkin.DEV) return DEV_KNIFE_MODEL;
			if (skin == WeaponSkin.TRUSTED) return TRUSTED_KNIFE_MODEL;
			if (skin == WeaponSkin.HOST) return HOST_KNIFE_MODEL;
			return null;
		}
		if (isGunLike(stack)) {
			if (!GexpressConfig.use3dGunSkins()) {
				WeaponSkin logical = skin.logical(WeaponSkinType.GUN);
				if (logical == WeaponSkin.GOLD) return HOST_REVOLVER_MODEL;
				if (logical == WeaponSkin.COLA) return TRUSTED_REVOLVER_MODEL;
				return null;
			}
			if (skin == WeaponSkin.DEV) return DEV_REVOLVER_MODEL;
			if (skin == WeaponSkin.DEFAULT || skin == WeaponSkin.PASSENGER) return PASSENGER_REVOLVER_MODEL;
			if (skin == WeaponSkin.TRUSTED || skin == WeaponSkin.COLA) return COLA_REVOLVER_MODEL;
			if (skin == WeaponSkin.HOST || skin == WeaponSkin.GOLD) return GOLD_REVOLVER_MODEL;
			if (skin == WeaponSkin.JEM) return JEM_REVOLVER_MODEL;
			if (skin == WeaponSkin.BLUE) return BLUE_REVOLVER_MODEL;
			if (skin == WeaponSkin.PINK) return PINK_REVOLVER_MODEL;
			if (skin == WeaponSkin.PLAID) return PLAID_REVOLVER_MODEL;
			if (skin == WeaponSkin.BAMBOO) return BAMBOO_REVOLVER_MODEL;
			if (skin == WeaponSkin.ATOMIZER) return ATOMIZER_REVOLVER_MODEL;
		}
		return null;
	}

	public static Identifier skinModelId(WeaponSkin skin, WeaponSkinType type) {
		if (skin == null || type == null || !skin.supports(type)) return null;
		Identifier imported = skin.model(type);
		if (imported != null) return imported;
		if (type == WeaponSkinType.KNIFE) {
			if (skin == WeaponSkin.DEV) return DEV_KNIFE_MODEL;
			if (skin == WeaponSkin.TRUSTED) return TRUSTED_KNIFE_MODEL;
			if (skin == WeaponSkin.HOST) return HOST_KNIFE_MODEL;
			return null;
		}
		if (skin == WeaponSkin.DEV) return DEV_REVOLVER_MODEL;
		if (skin == WeaponSkin.DEFAULT || skin == WeaponSkin.PASSENGER) return PASSENGER_REVOLVER_MODEL;
		if (skin == WeaponSkin.TRUSTED || skin == WeaponSkin.COLA) return COLA_REVOLVER_MODEL;
		if (skin == WeaponSkin.HOST || skin == WeaponSkin.GOLD) return GOLD_REVOLVER_MODEL;
		if (skin == WeaponSkin.JEM) return JEM_REVOLVER_MODEL;
		if (skin == WeaponSkin.BLUE) return BLUE_REVOLVER_MODEL;
		if (skin == WeaponSkin.PINK) return PINK_REVOLVER_MODEL;
		if (skin == WeaponSkin.PLAID) return PLAID_REVOLVER_MODEL;
		if (skin == WeaponSkin.BAMBOO) return BAMBOO_REVOLVER_MODEL;
		if (skin == WeaponSkin.ATOMIZER) return ATOMIZER_REVOLVER_MODEL;
		return null;
	}

	private static WeaponSkin resolveSkin(ItemStack stack, LivingEntity entity) {
		WeaponSkin preview = previewSkin(stack);
		if (preview != null) return preview;
		WeaponSkinType type = isKnifeLike(stack) ? WeaponSkinType.KNIFE
			: isGunLike(stack) ? WeaponSkinType.GUN : null;
		if (entity instanceof PlayerEntity player) {
			WeaponSkin equipped = equippedSkin(player.getUuid(), type);
			if (equipped != null) return equipped;
			if (GexpressPermissions.isDev(player)) return WeaponSkin.DEV;
			if (TrustedComponent.isTrusted(player)) return WeaponSkin.TRUSTED;
			if (HostComponent.isHost(player)) return WeaponSkin.HOST;
		}
		String owner = stack.get(WatheDataComponentTypes.OWNER);
		WeaponSkin equipped = equippedSkin(parseUuid(owner), type);
		if (equipped != null) return equipped;
		if (GexpressPermissions.isDevUuidString(owner)) return WeaponSkin.DEV;
		if (isTrustedUuidString(owner)) return WeaponSkin.TRUSTED;
		return isHostUuidString(owner) ? WeaponSkin.HOST : WeaponSkin.DEFAULT;
	}

	private static WeaponSkin previewSkin(ItemStack stack) {
		NbtComponent data = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		String raw = data.copyNbt().getString(SKIN_PREVIEW_KEY);
		return WeaponSkin.byId(raw);
	}

	private static WeaponSkin equippedSkin(UUID playerId, WeaponSkinType type) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null || playerId == null || type == null) return null;
		PlayerSkinComponent component = PlayerSkinComponent.KEY.getNullable(client.world);
		if (component == null || !component.hasEquipped(playerId, type)) return null;
		return component.equipped(playerId, type);
	}

	private static boolean usesDefaultWatheSkin(ItemStack stack) {
		if (!stack.isOf(WatheItems.KNIFE)) return true;
		try {
			return "default".equalsIgnoreCase(WatheCosmetics.getSkin(stack));
		} catch (Throwable ignored) {
			return true;
		}
	}

	public static boolean isKnifeLike(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		return stack.isOf(WatheItems.KNIFE) || isNoellesFakeKnife(stack);
	}

	public static boolean isGunLike(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.isOf(WatheItems.REVOLVER);
	}

	public static ItemStack fakeKnifeRenderStack(ItemStack stack, LivingEntity entity) {
		if (!isNoellesFakeKnife(stack) || entity == null) return stack;
		ItemStack knife = WatheItems.KNIFE.getDefaultStack();
		knife.set(WatheDataComponentTypes.OWNER, entity.getUuidAsString());
		NbtComponent preview = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (preview != null && preview.copyNbt().contains(SKIN_PREVIEW_KEY)) {
			knife.set(DataComponentTypes.CUSTOM_DATA, preview);
		}
		return knife;
	}

	private static boolean isNoellesFakeKnife(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		Identifier id = Registries.ITEM.getId(stack.getItem());
		return NOELLES_FAKE_KNIFE.equals(id) || "fake_knife".equals(id.getPath());
	}

	private static boolean isHostUuidString(String uuid) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) return false;
		return isUuidInComponent(uuid, HostComponent.KEY.getNullable(client.world));
	}

	private static boolean isTrustedUuidString(String uuid) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) return false;
		return isUuidInComponent(uuid, TrustedComponent.KEY.getNullable(client.world));
	}

	private static boolean isUuidInComponent(String uuid, Object component) {
		if (uuid == null || uuid.isBlank()) return false;
		try {
			UUID parsed = UUID.fromString(uuid);
			if (component instanceof HostComponent hosts) return hosts.isHost(parsed);
			if (component instanceof TrustedComponent trusted) return trusted.isTrusted(parsed);
			return false;
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	private static UUID parseUuid(String uuid) {
		if (uuid == null || uuid.isBlank()) return null;
		try {
			return UUID.fromString(uuid);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

}
