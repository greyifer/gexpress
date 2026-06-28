package dev.mapselect.block;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.mapselect.network.coinbarrier.CoinBarrierPurchaseOpenPayload;
import dev.mapselect.network.coinbarrier.CoinBarrierPurchasePayload;
import dev.mapselect.network.coinbarrier.CoinBarrierEditOpenPayload;
import dev.mapselect.network.coinbarrier.CoinBarrierEditSavePayload;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.registry.MapSelectBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class CoinBarrierManager {
	private static final double MAX_PURCHASE_DISTANCE_SQUARED = 8.0D * 8.0D;

	private CoinBarrierManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(CoinBarrierEditOpenPayload.ID, CoinBarrierEditOpenPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CoinBarrierEditSavePayload.ID, CoinBarrierEditSavePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CoinBarrierPurchaseOpenPayload.ID, CoinBarrierPurchaseOpenPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CoinBarrierPurchasePayload.ID, CoinBarrierPurchasePayload.CODEC);
		CoinBarrierTextDisplayManager.register();
		ServerPlayNetworking.registerGlobalReceiver(CoinBarrierEditSavePayload.ID,
			(payload, context) -> context.server().execute(() -> save(context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(CoinBarrierPurchasePayload.ID,
			(payload, context) -> context.server().execute(() -> purchase(context.player(), payload.pos())));
	}

	public static void openEditor(ServerPlayerEntity player, BlockPos pos) {
		if (!canEdit(player)) {
			player.sendMessage(Text.literal("You need setup permission to edit red ribbons.")
				.formatted(Formatting.RED), true);
			return;
		}
		if (!player.getWorld().getBlockState(pos).isOf(MapSelectBlocks.COIN_BARRIER)) return;
		CoinBarrierBlock.Cluster cluster = CoinBarrierBlock.scan(player.getWorld(), pos);
		if (cluster.positions().isEmpty()) return;
		if (ServerPlayNetworking.canSend(player, CoinBarrierEditOpenPayload.ID)) {
			ServerPlayNetworking.send(player,
				new CoinBarrierEditOpenPayload(pos.toImmutable(), cluster.price(), cluster.title()));
		}
	}

	public static boolean openPurchase(ServerPlayerEntity player, BlockPos pos) {
		if (player == null || pos == null || !player.getWorld().getBlockState(pos).isOf(MapSelectBlocks.COIN_BARRIER)) {
			return false;
		}
		CoinBarrierBlock.Cluster cluster = CoinBarrierBlock.scan(player.getWorld(), pos);
		if (cluster.positions().isEmpty()) return false;
		if (ServerPlayNetworking.canSend(player, CoinBarrierPurchaseOpenPayload.ID)) {
			ServerPlayNetworking.send(player,
				new CoinBarrierPurchaseOpenPayload(pos.toImmutable(), cluster.price(), cluster.title()));
		}
		return true;
	}

	private static void save(ServerPlayerEntity player, CoinBarrierEditSavePayload payload) {
		if (!(player.getWorld() instanceof ServerWorld world) || !canEdit(player)) return;
		BlockPos pos = payload.pos();
		if (!world.getBlockState(pos).isOf(MapSelectBlocks.COIN_BARRIER)) return;
		CoinBarrierBlock.Cluster cluster = CoinBarrierBlock.scan(world, pos);
		if (cluster.positions().isEmpty()) return;

		int price = CoinBarrierBlockEntity.clampPrice(payload.price());
		String title = CoinBarrierBlockEntity.sanitizeTitle(payload.title());
		for (BlockPos blockPos : cluster.positions()) {
			if (world.getBlockEntity(blockPos) instanceof CoinBarrierBlockEntity barrier) {
				barrier.setMetadata(price, title);
			}
		}
		String label = title.isBlank() ? "red ribbon" : title;
		player.sendMessage(Text.literal("Updated " + label + " for " + price + " coins.")
			.formatted(Formatting.GOLD), true);
	}

	private static void purchase(ServerPlayerEntity player, BlockPos pos) {
		if (!(player.getWorld() instanceof ServerWorld world) || pos == null) return;
		if (player.squaredDistanceTo(Vec3d.ofCenter(pos)) > MAX_PURCHASE_DISTANCE_SQUARED) return;
		if (!world.getBlockState(pos).isOf(MapSelectBlocks.COIN_BARRIER)) return;
		CoinBarrierBlock.Cluster cluster = CoinBarrierBlock.scan(world, pos);
		if (cluster.positions().isEmpty()) return;

		int price = cluster.price();
		if (!player.isCreative()) {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
			if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
				player.sendMessage(Text.literal("Red ribbons can only be bought during a round.")
					.formatted(Formatting.RED), true);
				return;
			}
			PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
			if (shop.balance < price) {
				player.sendMessage(Text.literal("You need " + price + " coins.")
					.formatted(Formatting.RED), true);
				return;
			}
			shop.setBalance(shop.balance - price);
			PlayerShopComponent.KEY.sync(player);
		}

		for (BlockPos blockPos : cluster.positions()) {
			world.setBlockState(blockPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
		}
		String label = cluster.title().isBlank() ? "Red Ribbon" : cluster.title();
		player.sendMessage(Text.literal(label + " removed for " + price + " coins.")
			.formatted(Formatting.GOLD), true);
	}

	private static boolean canEdit(ServerPlayerEntity player) {
		return player != null && (player.isCreative() || GexpressPermissions.canEditSetupOptions(player));
	}
}
