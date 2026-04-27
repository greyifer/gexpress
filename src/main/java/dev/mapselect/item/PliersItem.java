package dev.mapselect.item;

import dev.mapselect.role.bombspecialist.C4BackComponent;
import dev.mapselect.role.bombspecialist.C4Detonation;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.MapSelect;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * The EOD Specialist's tool. Right-click on a player who has a C4 attached to attempt a
 * defuse. Outcome is a single roll against {@link GexpressConfig#getWrongWirePercent()}:
 *
 * <ul>
 *   <li><b>Rolled &lt; wrongWirePercent</b> — clipped the wrong wire → instantly detonates
 *       the charge at the carrier's current position. The EOD player is typically killed by
 *       their own defuse attempt (cut close enough to be in the blast radius).</li>
 *   <li><b>Otherwise</b> — success. The component entry is removed, a "defused" click plays,
 *       and the pliers are consumed (one-shot tool, matches C4's one-shot economy).</li>
 * </ul>
 *
 * No target restriction: the EOD player can defuse C4 on teammates, enemies, or themselves
 * (e.g. if a Bomb Specialist managed to tag them — the bomb sticks regardless of side).
 */
public class PliersItem extends Item {
	public PliersItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		if (!(entity instanceof PlayerEntity target)) return ActionResult.PASS;

		World world = user.getWorld();
		if (world.isClient) return ActionResult.SUCCESS;

		C4BackComponent comp = C4BackComponent.KEY.getNullable(world);
		if (comp == null) return ActionResult.FAIL;
		if (!comp.hasC4(target.getUuid())) return ActionResult.FAIL;

		Random rng = world.getRandom();
		int wrongWire = GexpressConfig.getWrongWirePercent();
		boolean clippedWrongWire = rng.nextInt(100) < wrongWire;

		if (clippedWrongWire) {
			MapSelect.LOGGER.info("Pliers MISFIRE on {} (wrongWirePercent={}% roll failed) — detonating now",
				target.getName().getString(), wrongWire);
			comp.removeC4(target.getUuid());
			if (world instanceof ServerWorld sw) {
				// Blame the defuser for the explosion — they're the one who pulled the wire.
				C4Detonation.detonateAt(sw, target, user);
			}
		} else {
			MapSelect.LOGGER.info("Pliers SUCCESS on {} (wrongWirePercent={}% roll passed) — C4 removed",
				target.getName().getString(), wrongWire);
			comp.removeC4(target.getUuid());
			world.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.BLOCK_TRIPWIRE_CLICK_OFF, SoundCategory.PLAYERS, 0.9F, 1.2F);
			// ITEM_SHEARS_SNIP doesn't exist in 1.21.1 (added in 1.21.5) — ENTITY_SHEEP_SHEAR is
			// the canonical snip SFX across this version and conveys "cut the wire" just as well.
			world.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 1.0F, 1.2F);
		}

		if (!user.getAbilities().creativeMode) {
			stack.decrement(1);
		}

		return ActionResult.CONSUME;
	}
}
