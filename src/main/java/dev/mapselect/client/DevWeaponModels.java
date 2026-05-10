package dev.mapselect.client;

import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheCosmetics;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.MapSelect;
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
import net.minecraft.util.Identifier;

import java.util.UUID;

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
	public static final Identifier SHADOW_KNIFE_MODEL = Identifier.of(MapSelect.MOD_ID, "item/knife_shadow");
	public static final Identifier SHADOW_REVOLVER_MODEL = Identifier.of(MapSelect.MOD_ID, "item/revolver_shadow");
	public static final String SKIN_PREVIEW_KEY = "gexpress_skin_preview";

	@Override
	public void onInitializeModelLoader(Context pluginContext) {
		pluginContext.addModels(DEV_KNIFE_MODEL, DEV_REVOLVER_MODEL, HOST_KNIFE_MODEL, HOST_REVOLVER_MODEL,
			TRUSTED_KNIFE_MODEL, TRUSTED_REVOLVER_MODEL, PASSENGER_REVOLVER_MODEL, COLA_REVOLVER_MODEL,
			GOLD_REVOLVER_MODEL, JEM_REVOLVER_MODEL, SHADOW_KNIFE_MODEL, SHADOW_REVOLVER_MODEL);
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

		WeaponSkinType type = stack.isOf(WatheItems.KNIFE) ? WeaponSkinType.KNIFE
			: stack.isOf(WatheItems.REVOLVER) ? WeaponSkinType.GUN : null;
		WeaponSkin skin = resolveSkin(stack, entity);
		if (skin == null || skin == WeaponSkin.DEFAULT || !skin.supports(type)) return null;
		if (stack.isOf(WatheItems.KNIFE)) {
			return switch (skin) {
				case DEV -> DEV_KNIFE_MODEL;
				case TRUSTED -> TRUSTED_KNIFE_MODEL;
				case HOST -> HOST_KNIFE_MODEL;
				default -> null;
			};
		}
		if (stack.isOf(WatheItems.REVOLVER)) {
			return switch (skin) {
				case DEV -> DEV_REVOLVER_MODEL;
				case TRUSTED -> TRUSTED_REVOLVER_MODEL;
				case HOST -> HOST_REVOLVER_MODEL;
				case PASSENGER -> PASSENGER_REVOLVER_MODEL;
				case COLA -> COLA_REVOLVER_MODEL;
				case GOLD -> GOLD_REVOLVER_MODEL;
				case JEM -> JEM_REVOLVER_MODEL;
				default -> null;
			};
		}
		return null;
	}

	private static WeaponSkin resolveSkin(ItemStack stack, LivingEntity entity) {
		WeaponSkin preview = previewSkin(stack);
		if (preview != null) return preview;
		WeaponSkinType type = stack.isOf(WatheItems.KNIFE) ? WeaponSkinType.KNIFE
			: stack.isOf(WatheItems.REVOLVER) ? WeaponSkinType.GUN : null;
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
