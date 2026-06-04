package dev.mapselect.block;

import dev.mapselect.network.CoinBarrierEditOpenPayload;
import dev.mapselect.network.CoinBarrierEditSavePayload;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.registry.MapSelectBlocks;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public final class CoinBarrierManager {
	private CoinBarrierManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(CoinBarrierEditOpenPayload.ID, CoinBarrierEditOpenPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CoinBarrierEditSavePayload.ID, CoinBarrierEditSavePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CoinBarrierEditSavePayload.ID,
			(payload, context) -> context.server().execute(() -> save(context.player(), payload)));
	}

	public static void openEditor(ServerPlayerEntity player, BlockPos pos) {
		if (!canEdit(player)) {
			player.sendMessage(Text.literal("You need setup permission to edit coin barriers.")
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
		String label = title.isBlank() ? "coin barrier" : title;
		player.sendMessage(Text.literal("Updated " + label + " for " + price + " coins.")
			.formatted(Formatting.GOLD), true);
	}

	private static boolean canEdit(ServerPlayerEntity player) {
		return player != null && (player.isCreative() || GexpressPermissions.canEditSetupOptions(player));
	}
}
