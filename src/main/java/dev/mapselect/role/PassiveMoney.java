package dev.mapselect.role;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.mafia.MafiaManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class PassiveMoney {
	private static final int OPENING_GRACE_TICKS = 30 * 20;
	private static long lastGrantTick = Long.MIN_VALUE;

	private PassiveMoney() {}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (world.getRegistryKey() == World.OVERWORLD) {
				grant(world, GameWorldComponent.KEY.getNullable(world));
			}
		});
	}

	public static Integer value(ServerWorld world) {
		if (world == null || isOpeningGrace(world)) return 0;
		Integer money = GameConstants.PASSIVE_MONEY_TICKER.apply(world.getTime());
		return money == null ? 0 : money;
	}

	public static void grant(ServerWorld world, GameWorldComponent game) {
		if (world == null || game == null || world.getTime() == lastGrantTick) return;
		Integer base = value(world);
		if (base <= 0) return;
		lastGrantTick = world.getTime();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!GameFunctions.isPlayerAliveAndSurvival(player)) continue;
			int money = amountFor(game, player);
			if (money <= 0) continue;
			PlayerShopComponent.KEY.get(player).addToBalance(money);
			PlayerShopComponent.KEY.sync(player);
		}
	}

	private static int amountFor(GameWorldComponent game, ServerPlayerEntity player) {
		if (MafiaManager.isMafiaRole(player)) return GexpressConfig.getPassiveIncomeMafia();
		Role role = game.getRole(player);
		if (role == null) return 0;
		if (role == WatheRoles.VIGILANTE) return GexpressConfig.getPassiveIncomeVigilante();
		Identifier id = role.identifier();
		if (MapSelectRoles.JUGGERNAUT_ID.equals(id) || MapSelectRoles.VULTURE_ID.equals(id)
				|| MapSelectRoles.GODFATHER_ID.equals(id)) {
			return GexpressConfig.getPassiveIncomeNeutral();
		}
		if (game.canUseKillerFeatures(player)) return GexpressConfig.getPassiveIncomeKiller();
		return role.isInnocent() ? GexpressConfig.getPassiveIncomeCivilian() : GexpressConfig.getPassiveIncomeNeutral();
	}

	private static boolean isOpeningGrace(ServerWorld world) {
		GameTimeComponent time = GameTimeComponent.KEY.getNullable(world);
		if (time == null || time.resetTime <= 0) return false;
		int elapsed = time.resetTime - time.getTime();
		return elapsed >= 0 && elapsed < OPENING_GRACE_TICKS;
	}
}
