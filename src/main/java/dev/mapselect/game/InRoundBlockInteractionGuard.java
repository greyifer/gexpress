package dev.mapselect.game;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

/** Blocks optional-mod utility blocks that must not be usable during an active round. */
public final class InRoundBlockInteractionGuard {
	private static final Identifier SUPPLEMENTARIES_DOORMAT = Identifier.of("supplementaries", "doormat");

	private InRoundBlockInteractionGuard() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
			if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
				return ActionResult.PASS;
			}
			Identifier blockId = Registries.BLOCK.getId(
				world.getBlockState(hitResult.getBlockPos()).getBlock());
			return SUPPLEMENTARIES_DOORMAT.equals(blockId) ? ActionResult.FAIL : ActionResult.PASS;
		});
	}
}
