package dev.mapselect.role;

import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public final class PassiveMoney {
	private static final int OPENING_GRACE_TICKS = 30 * 20;

	private PassiveMoney() {}

	public static Integer value(ServerWorld world) {
		if (world == null || isOpeningGrace(world)) return 0;
		Integer money = GameConstants.PASSIVE_MONEY_TICKER.apply(world.getTime());
		return money == null ? 0 : money;
	}

	public static void grant(ServerWorld world, GameWorldComponent game) {
		if (world == null || game == null) return;
		Integer money = value(world);
		if (money <= 0) return;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (game.canUseKillerFeatures(player)) {
				PlayerShopComponent.KEY.get(player).addToBalance(money);
			}
		}
	}

	private static boolean isOpeningGrace(ServerWorld world) {
		GameTimeComponent time = GameTimeComponent.KEY.getNullable(world);
		if (time == null || time.resetTime <= 0) return false;
		int elapsed = time.resetTime - time.getTime();
		return elapsed >= 0 && elapsed < OPENING_GRACE_TICKS;
	}
}
