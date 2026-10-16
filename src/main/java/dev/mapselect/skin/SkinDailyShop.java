package dev.mapselect.skin;

import dev.mapselect.currency.GcoinComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/** Server-authoritative, stable-per-rotation weapon-skin offers personalized for each player. */
public final class SkinDailyShop {
	public static final int MAX_OFFER_COUNT = 8;
	private static final long ROTATION_SALT = 0x47_45_58_50_52_45_53_53L;

	private SkinDailyShop() {}

	public static List<Offer> offers(UUID playerId) {
		if (playerId == null || !SkinShopConfig.dailyOffersEnabled()) return List.of();
		return offers(rotationId(), playerId);
	}

	static List<Offer> offers(long rotationId, UUID playerId) {
		List<Offer> candidates = new ArrayList<>();
		for (SkinShopConfig.SkinRule rule : SkinShopConfig.enabledSkins()) {
			WeaponSkinType type = WeaponSkinType.byId(rule.type());
			WeaponSkin skin = WeaponSkin.byId(rule.skin());
			if (type == null || skin == null || !SkinShopConfig.isEligibleSkin(type, skin)) continue;
			Offer offer = new Offer(type, skin.logical(type), rule.price());
			if (!candidates.contains(offer)) candidates.add(offer);
		}
		long playerSeed = playerId.getMostSignificantBits()
			^ Long.rotateLeft(playerId.getLeastSignificantBits(), 23);
		Collections.shuffle(candidates, new Random(rotationId ^ playerSeed ^ ROTATION_SALT));
		return List.copyOf(candidates.subList(0,
			Math.min(Math.min(MAX_OFFER_COUNT, SkinShopConfig.offerCount()), candidates.size())));
	}

	public static long rotationId() {
		return Math.floorDiv(Instant.now().getEpochSecond(), rotationSeconds());
	}

	public static long nextRotationEpochSecond() {
		long seconds = rotationSeconds();
		return Math.multiplyExact(rotationId() + 1L, seconds);
	}

	public static boolean buy(ServerPlayerEntity player, WeaponSkinType type, WeaponSkin skin) {
		if (player == null || type == null || skin == null) return false;
		Offer offer = offers(player.getUuid()).stream()
			.filter(candidate -> candidate.type() == type && candidate.skin().equals(skin.logical(type)))
			.findFirst()
			.orElse(null);
		if (offer == null) {
			player.sendMessage(Text.literal("That skin is not in today's rotation.").formatted(Formatting.RED), false);
			return false;
		}

		PlayerSkinComponent skins = PlayerSkinComponent.KEY.get(player.getServerWorld());
		if (skins.isUnlocked(player.getUuid(), offer.type(), offer.skin())) {
			player.sendMessage(Text.literal("You already own " + offer.skin().displayName() + ".")
				.formatted(Formatting.YELLOW), false);
			return false;
		}

		GcoinComponent gcoins = GcoinComponent.KEY.get(player.getServerWorld());
		if (gcoins.balance(player.getUuid()) < offer.price()) {
			player.sendMessage(Text.literal("You need " + offer.price() + " G'Coins to buy this skin.")
				.formatted(Formatting.RED), false);
			return false;
		}

		gcoins.remove(player.getUuid(), offer.price());
		if (!skins.give(player.getUuid(), offer.type(), offer.skin())) {
			gcoins.add(player.getUuid(), offer.price());
			return false;
		}
		player.sendMessage(Text.literal("Unlocked " + offer.skin().displayName() + " "
			+ offer.type().displayName() + " skin for " + offer.price() + " G'Coins.")
			.formatted(Formatting.GOLD), false);
		return true;
	}

	private static long rotationSeconds() {
		return Math.max(1L, SkinShopConfig.rotationHours()) * 3600L;
	}

	public record Offer(WeaponSkinType type, WeaponSkin skin, int price) {
		public Offer {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(skin, "skin");
			price = Math.max(0, price);
		}
	}
}
