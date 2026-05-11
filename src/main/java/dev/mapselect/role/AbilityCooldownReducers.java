package dev.mapselect.role;

import dev.mapselect.role.mafia.MafiaManager;
import dev.mapselect.role.medic.MedicShieldComponent;
import dev.mapselect.role.puppetmaster.PuppetmasterManager;
import dev.mapselect.role.scatterbrain.ScatterBrainManager;
import dev.mapselect.role.silent.SilentShadowComponent;
import dev.mapselect.role.skincrawler.SkincrawlerManager;
import dev.mapselect.role.timemaster.TimeMasterComponent;
import dev.mapselect.role.tracker.TrackerManager;
import dev.mapselect.role.trickster.DancingCartsManager;
import dev.mapselect.role.trickster.TricksterManager;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.role.warlock.WarlockComponent;
import net.minecraft.server.network.ServerPlayerEntity;

public final class AbilityCooldownReducers {
	private AbilityCooldownReducers() {}

	public static void reduceForTask(ServerPlayerEntity player) {
		reduce(player, 5L * 20L);
	}

	public static void reduce(ServerPlayerEntity player, long ticks) {
		if (player == null || ticks <= 0L || player.getWorld() == null) return;
		MedicShieldComponent medic = MedicShieldComponent.KEY.getNullable(player.getWorld());
		if (medic != null) medic.reduceCooldown(player.getUuid(), ticks);

		SilentShadowComponent silent = SilentShadowComponent.KEY.getNullable(player.getWorld());
		if (silent != null) silent.reduceCooldown(player.getUuid(), ticks);

		WarlockComponent warlock = WarlockComponent.KEY.getNullable(player.getWorld());
		if (warlock != null) {
			warlock.reduceMarkCooldown(player.getUuid(), ticks);
			warlock.reduceKillCooldown(player.getUuid(), ticks);
		}

		TimeMasterComponent timeMaster = TimeMasterComponent.KEY.getNullable(player.getWorld());
		if (timeMaster != null) {
			timeMaster.reduceRewindCooldown(player.getUuid(), ticks);
			timeMaster.reduceFreezeCooldown(player.getUuid(), ticks);
		}

		PuppetmasterManager.reduceCooldown(player, ticks);
		VultureManager.reduceEatCooldown(player, ticks);
		TricksterManager.reduceMasqueradeCooldown(player, ticks);
		DancingCartsManager.reduceCooldown(player, ticks);
		ScatterBrainManager.reduceCooldown(player, ticks);
		TrackerManager.reduceCooldown(player, ticks);
		SkincrawlerManager.reduceCooldown(player, ticks);
		MafiaManager.reduceCooldowns(player, ticks);
	}
}
