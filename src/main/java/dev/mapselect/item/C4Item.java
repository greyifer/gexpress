package dev.mapselect.item;

import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.role.bombspecialist.C4BackComponent;
import dev.mapselect.role.bombspecialist.C4Detonation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
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
		if (user.getWorld().isClient) return ActionResult.SUCCESS;

		return plantOnPlayer(stack, user, target);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (stack.isEmpty()) return TypedActionResult.pass(stack);

		PlayerEntity targetedPlayer = findTargetedPlayer(user);
		if (targetedPlayer != null) {
			if (!world.isClient) {
				plantOnPlayer(stack, user, targetedPlayer);
			}
			return TypedActionResult.success(stack, world.isClient);
		}

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

	private static ActionResult plantOnPlayer(ItemStack stack, PlayerEntity user, PlayerEntity target) {
		World world = user.getWorld();
		C4BackComponent comp = C4BackComponent.KEY.getNullable(world);
		if (comp == null) return ActionResult.FAIL;

		if (comp.hasC4(target.getUuid())) return ActionResult.CONSUME;

		int fuseSeconds = GexpressConfig.getC4FuseSeconds();
		int firstBeepSeconds = GexpressConfig.getC4FirstBeepSeconds();
		MapSelect.LOGGER.info("C4 planted on {} by {} - fuse={}s (detonates at tick {} from now={})",
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

	private static PlayerEntity findTargetedPlayer(PlayerEntity user) {
		double range = Math.max(3.0D, user.getEntityInteractionRange());
		Vec3d start = user.getEyePos();
		Vec3d direction = user.getRotationVec(1.0F).normalize();
		Vec3d end = start.add(direction.multiply(range));
		Box searchBox = user.getBoundingBox().stretch(direction.multiply(range)).expand(1.0D);
		EntityHitResult hit = ProjectileUtil.raycast(user, start, end, searchBox,
			entity -> canPlantOnEntity(user, entity), range * range);
		if (hit == null || !(hit.getEntity() instanceof PlayerEntity target)) return null;
		return target;
	}

	private static boolean canPlantOnEntity(PlayerEntity user, Entity entity) {
		return entity instanceof PlayerEntity target
			&& target != user
			&& !target.isSpectator()
			&& target.canHit();
	}
}
