package dev.mapselect.block;

import com.mojang.serialization.MapCodec;
import dev.doctor4t.wathe.block.FoodPlatterBlock;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.modifier.ModifierUtils;
import dev.mapselect.registry.MapSelectModifiers;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GoldFoodPlatterBlock extends FoodPlatterBlock {
	public static final MapCodec<GoldFoodPlatterBlock> CODEC = createCodec(GoldFoodPlatterBlock::new);
	public static final BooleanProperty PAID = BooleanProperty.of("paid");

	public GoldFoodPlatterBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(PAID, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (player.isSneaking()) {
			if (!world.isClient) {
				boolean paid = !state.get(PAID);
				world.setBlockState(pos, state.with(PAID, paid), Block.NOTIFY_ALL);
				player.sendMessage(Text.literal("Paid mode " + (paid ? "enabled" : "disabled") + ".")
					.formatted(paid ? Formatting.GOLD : Formatting.GRAY), true);
			}
			return ActionResult.SUCCESS;
		}
		if (world.isClient) return ActionResult.SUCCESS;
		if (!(world.getBlockEntity(pos) instanceof GoldBeveragePlateBlockEntity plate)) return ActionResult.PASS;

		ItemStack held = player.getStackInHand(Hand.MAIN_HAND);
		if (player.isCreative() && !held.isEmpty()) {
			plate.addItem(held);
			return ActionResult.SUCCESS;
		}
		if (!held.isEmpty() && held.isOf(WatheItems.POISON_VIAL) && plate.getPoisoner() == null) {
			plate.setPoisoner(player.getUuidAsString());
			held.decrement(1);
			player.playSoundToPlayer(SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.5F, 1.0F);
			return ActionResult.SUCCESS;
		}
		if (held.isEmpty()) return takeItem(state, world, player, plate);
		return ActionResult.PASS;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(PAID);
	}

	@Override
	public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		GoldBeveragePlateBlockEntity plate = new GoldBeveragePlateBlockEntity(pos, state);
		plate.setDrink(false);
		return plate;
	}

	protected ActionResult takeItem(BlockState state, World world, PlayerEntity player, GoldBeveragePlateBlockEntity plate) {
		List<ItemStack> platter = plate.getStoredItems();
		if (platter.isEmpty()) return ActionResult.SUCCESS;
		int matchingItems = countMatchingPlatterItems(player, platter);
		boolean hungry = ModifierUtils.has(player, MapSelectModifiers.HUNGRY_ID);
		if (!hungry && matchingItems > 0) return ActionResult.PASS;
		if (hungry && matchingItems >= GexpressConfig.getHungryFoodLimit()) return ActionResult.SUCCESS;
		int price = GexpressConfig.getGoldFoodPlatterPrice();
		if (state.get(PAID) && !player.isCreative() && price > 0 && !tryCharge(player, price)) {
			return ActionResult.SUCCESS;
		}
		ItemStack randomItem = platter.get(world.random.nextInt(platter.size())).copy();
		randomItem.setCount(1);
		randomItem.set(DataComponentTypes.MAX_STACK_SIZE, 1);
		String poisoner = plate.getPoisoner();
		if (poisoner != null) {
			randomItem.set(WatheDataComponentTypes.POISONER, poisoner);
			plate.setPoisoner(null);
		}
		player.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 1.0F, 1.0F);
		player.setStackInHand(Hand.MAIN_HAND, randomItem);
		return ActionResult.SUCCESS;
	}

	private static int countMatchingPlatterItems(PlayerEntity player, List<ItemStack> platter) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			ItemStack stack = player.getInventory().getStack(slot);
			if (stack.isEmpty()) continue;
			for (ItemStack platterItem : platter) {
				if (!platterItem.isEmpty() && stack.isOf(platterItem.getItem())) {
					count += stack.getCount();
					break;
				}
			}
		}
		return count;
	}

	private boolean tryCharge(PlayerEntity player, int price) {
		PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
		if (shop.balance < price) {
			player.sendMessage(Text.literal("You need " + price + " coins.").formatted(Formatting.RED), true);
			return false;
		}
		shop.setBalance(shop.balance - price);
		PlayerShopComponent.KEY.sync(player);
		return true;
	}
}
