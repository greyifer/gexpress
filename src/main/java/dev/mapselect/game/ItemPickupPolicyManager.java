package dev.mapselect.game;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.registry.MapSelectItems;
import dev.mapselect.role.RoleTeams;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ItemPickupPolicyManager {
	private ItemPickupPolicyManager() {}

	public static boolean canPickup(PlayerEntity player, ItemStack stack) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || player.isCreative()) return true;
		if (stack == null || stack.isEmpty() || !isControlledRoundItem(stack)) return true;
		GexpressConfig.ItemPickupPolicy policy = GexpressConfig.getItemPickupPolicy();
		if (policy == GexpressConfig.ItemPickupPolicy.CIVILIANS_NEUTRALS_KILLERS) return true;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(serverPlayer.getWorld());
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return true;
		String side = RoleTeams.sideKey(game, serverPlayer);
		return side == null || policy.allowsSide(side);
	}

	private static boolean isControlledRoundItem(ItemStack stack) {
		return stack.isOf(WatheItems.KEY)
			|| stack.isOf(WatheItems.LOCKPICK)
			|| stack.isOf(WatheItems.KNIFE)
			|| stack.isOf(WatheItems.BAT)
			|| stack.isOf(WatheItems.CROWBAR)
			|| stack.isOf(WatheItems.GRENADE)
			|| stack.isOf(WatheItems.THROWN_GRENADE)
			|| stack.isOf(WatheItems.FIRECRACKER)
			|| stack.isOf(WatheItems.REVOLVER)
			|| stack.isOf(WatheItems.DERRINGER)
			|| stack.isOf(WatheItems.BODY_BAG)
			|| stack.isOf(WatheItems.LETTER)
			|| stack.isOf(WatheItems.BLACKOUT)
			|| stack.isOf(WatheItems.PSYCHO_MODE)
			|| stack.isOf(WatheItems.POISON_VIAL)
			|| stack.isOf(WatheItems.SCORPION)
			|| stack.isOf(WatheItems.NOTE)
			|| stack.isOf(MapSelectItems.C4)
			|| stack.isOf(MapSelectItems.C4_DETONATOR)
			|| stack.isOf(MapSelectItems.PLIERS)
			|| stack.isOf(MapSelectItems.BULLET)
			|| stack.isOf(MapSelectItems.SPY_BUG);
	}
}
