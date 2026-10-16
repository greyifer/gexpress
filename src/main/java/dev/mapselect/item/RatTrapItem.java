package dev.mapselect.item;

import dev.doctor4t.wathe.util.AdventureUsable;
import dev.mapselect.entity.RatTrapEntity;
import dev.mapselect.registry.MapSelectEntities;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Direction;

/** A floor-placed trap entity, matching Wathe's Firecracker placement behavior. */
public final class RatTrapItem extends Item implements AdventureUsable {
	public RatTrapItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (context.getSide() != Direction.UP || context.getPlayer() == null) return ActionResult.PASS;
		if (!context.getWorld().isClient) {
			RatTrapEntity trap = MapSelectEntities.RAT_TRAP.create(context.getWorld());
			if (trap == null) return ActionResult.FAIL;
			trap.refreshPositionAndAngles(context.getHitPos().x, context.getHitPos().y + 0.015D,
				context.getHitPos().z, context.getPlayer().getYaw(), 0.0F);
			if (!context.getWorld().spawnEntity(trap)) return ActionResult.FAIL;
			if (!context.getPlayer().isCreative()) context.getStack().decrement(1);
		}
		return ActionResult.success(context.getWorld().isClient);
	}
}
