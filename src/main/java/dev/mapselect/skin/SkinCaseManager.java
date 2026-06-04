package dev.mapselect.skin;

import dev.mapselect.config.GexpressConfig;
import dev.mapselect.currency.GcoinComponent;
import dev.mapselect.network.SkinCaseResultPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class SkinCaseManager {
	private SkinCaseManager() {}

	public static boolean open(ServerPlayerEntity player, String caseId) {
		if (player == null || caseId == null || caseId.isBlank()) return false;
		GexpressConfig.SkinCaseEntry skinCase = GexpressConfig.getSkinCaseEntry(caseId);
		if (skinCase == null || skinCase.totalWeight() <= 0) {
			player.sendMessage(Text.literal("Unknown or empty case: " + caseId).formatted(Formatting.RED), false);
			return false;
		}
		PlayerSkinComponent skins = PlayerSkinComponent.KEY.get(player.getServerWorld());
		if (skins.caseCount(player.getUuid(), skinCase.id()) <= 0) {
			player.sendMessage(Text.literal("You do not own a " + skinCase.displayName() + ". Buy one first.")
				.formatted(Formatting.RED), false);
			return false;
		}
		GexpressConfig.SkinCaseReward reward = roll(player, skinCase);
		if (reward == null) return false;
		skins.removeCase(player.getUuid(), skinCase.id(), 1);
		boolean newUnlock = skins.give(player.getUuid(), reward.type(), reward.skin());
		int duplicateRefund = 0;
		if (!newUnlock) {
			duplicateRefund = reward.type() == WeaponSkinType.KNIFE ? 100 : 50;
			GcoinComponent.KEY.get(player.getServerWorld()).add(player.getUuid(), duplicateRefund);
		}
		player.sendMessage(Text.literal("Opened " + skinCase.displayName() + ".").formatted(Formatting.GOLD), false);
		if (ServerPlayNetworking.canSend(player, SkinCaseResultPayload.ID)) {
			ServerPlayNetworking.send(player, new SkinCaseResultPayload(skinCase.id(), reward.type().id(),
				reward.skin().id(), newUnlock, duplicateRefund));
		}
		return true;
	}

	public static boolean buy(ServerPlayerEntity player, String caseId) {
		if (player == null || caseId == null || caseId.isBlank()) return false;
		GexpressConfig.SkinCaseEntry skinCase = GexpressConfig.getSkinCaseEntry(caseId);
		if (skinCase == null || skinCase.totalWeight() <= 0) {
			player.sendMessage(Text.literal("Unknown or empty case: " + caseId).formatted(Formatting.RED), false);
			return false;
		}
		GcoinComponent gcoins = GcoinComponent.KEY.get(player.getServerWorld());
		if (gcoins.balance(player.getUuid()) < skinCase.price()) {
			player.sendMessage(Text.literal("You need " + skinCase.price() + " G'Coins to buy this case.")
				.formatted(Formatting.RED), false);
			return false;
		}
		if (skinCase.price() > 0) gcoins.remove(player.getUuid(), skinCase.price());
		PlayerSkinComponent skins = PlayerSkinComponent.KEY.get(player.getServerWorld());
		skins.giveCase(player.getUuid(), skinCase.id(), 1);
		player.sendMessage(Text.literal("Bought 1 " + skinCase.displayName() + " for " + skinCase.price() + " G'Coins.")
			.formatted(Formatting.GOLD), false);
		return true;
	}

	private static GexpressConfig.SkinCaseReward roll(ServerPlayerEntity player, GexpressConfig.SkinCaseEntry skinCase) {
		int total = skinCase.totalWeight();
		if (total <= 0) return null;
		int cursor = player.getRandom().nextInt(total);
		for (GexpressConfig.SkinCaseReward reward : skinCase.rewards()) {
			cursor -= Math.max(0, reward.weight());
			if (cursor < 0) return reward;
		}
		return skinCase.rewards().isEmpty() ? null : skinCase.rewards().getLast();
	}
}
