package dev.mapselect.item;

import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.permissions.GexpressPermissions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public final class DevWeaponSkinStamper {
	private DevWeaponSkinStamper() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				stamp(player);
			}
		});
	}

	public static void stamp(PlayerEntity player) {
		if (!GexpressPermissions.isHostOrDev(player)) return;
		String owner = player.getUuidAsString();
		for (int i = 0; i < player.getInventory().size(); i++) {
			stampStack(player.getInventory().getStack(i), owner);
		}
	}

	private static void stampStack(ItemStack stack, String owner) {
		if (stack.isEmpty()) return;
		if (!stack.isOf(WatheItems.KNIFE) && !stack.isOf(WatheItems.REVOLVER)) return;
		if (!owner.equals(stack.get(WatheDataComponentTypes.OWNER))) {
			stack.set(WatheDataComponentTypes.OWNER, owner);
		}
	}
}
