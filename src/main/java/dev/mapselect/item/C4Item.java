package dev.mapselect.item;

import dev.mapselect.role.bombspecialist.C4BackComponent;
import dev.mapselect.role.bombspecialist.C4Detonation;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.MapSelect;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class C4Item extends Item {
	public C4Item(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		if (!(entity instanceof PlayerEntity target)) return ActionResult.PASS;
		if (target == user) return ActionResult.PASS;

		World world = user.getWorld();
		if (world.isClient) return ActionResult.SUCCESS;

		C4BackComponent comp = C4BackComponent.KEY.getNullable(world);
		if (comp == null) return ActionResult.FAIL;

		if (comp.hasC4(target.getUuid())) return ActionResult.FAIL;

		int fuseSeconds = GexpressConfig.getC4FuseSeconds();
		int firstBeepSeconds = GexpressConfig.getC4FirstBeepSeconds();
		MapSelect.LOGGER.info("C4 planted on {} by {} — fuse={}s (detonates at tick {} from now={})",
			target.getName().getString(), user.getName().getString(),
			fuseSeconds, world.getTime() + (long) (firstBeepSeconds + fuseSeconds) * 20L, world.getTime());

		if (!comp.addC4(target.getUuid())) return ActionResult.FAIL;

		if (!user.getAbilities().creativeMode) {
			stack.decrement(1);
		}

		world.playSound(null, target.getX(), target.getY(), target.getZ(),
			SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 0.8F, 1.3F);

		return ActionResult.CONSUME;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (stack.isEmpty()) return TypedActionResult.pass(stack);

		if (!world.isClient) {
			ItemStack thrownStack = stack.copyWithCount(1);
			Vec3d eye = user.getEyePos();
			Vec3d velocity = user.getRotationVec(1.0F).normalize().multiply(0.85D)
				.add(0.0D, 0.12D, 0.0D);
			ItemEntity entity = new ItemEntity(world, eye.x, eye.y - 0.2D, eye.z,
				thrownStack, velocity.x, velocity.y, velocity.z);
			entity.setOwner(user.getUuid());
			entity.setPickupDelayInfinite();
			entity.setNeverDespawn();
			world.spawnEntity(entity);
			C4Detonation.registerThrownCharge(entity, user.getUuid());
			world.playSound(null, user.getX(), user.getY(), user.getZ(),
				SoundEvents.ENTITY_EGG_THROW, SoundCategory.PLAYERS, 0.55F, 0.75F);
			if (!user.getAbilities().creativeMode) {
				stack.decrement(1);
			}
		}

		return TypedActionResult.success(stack, world.isClient);
	}
}
