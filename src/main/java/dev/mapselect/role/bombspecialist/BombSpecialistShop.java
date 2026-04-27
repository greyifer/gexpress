package dev.mapselect.role.bombspecialist;

import dev.doctor4t.wathe.util.ShopEntry;
import dev.mapselect.role.GexpressRoleShop;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;

/** Compatibility wrapper for older internal references. */
@Deprecated
public final class BombSpecialistShop {
	private BombSpecialistShop() {}

	public static List<ShopEntry> resolve(PlayerEntity player) {
		return GexpressRoleShop.resolve(player);
	}

	public static List<ShopEntry> customList() {
		return GexpressRoleShop.bombSpecialistList();
	}
}
