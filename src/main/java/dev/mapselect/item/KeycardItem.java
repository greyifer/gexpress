package dev.mapselect.item;

import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.mapselect.block.LargeWoodDoorBlock;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.registry.MapSelectItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KeycardItem extends Item {
	private static final int MAX_KEY_NAME_LENGTH = 64;
	private static final Map<UUID, PendingDoor> PENDING_DOORS = new ConcurrentHashMap<>();
	private final Tier tier;

	public KeycardItem(Tier tier, Settings settings) {
		super(settings);
		this.tier = tier;
	}

	public Tier tier() {
		return tier;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (world.isClient) return ActionResult.SUCCESS;
		if (!(context.getPlayer() instanceof ServerPlayerEntity player)) return ActionResult.PASS;
		if (!canAssign(player)) return ActionResult.PASS;

		String keyName = keyName(context.getStack());
		if (keyName.isBlank()) {
			player.sendMessage(Text.literal("This keycard has no access label.").formatted(Formatting.RED), true);
			return ActionResult.CONSUME;
		}

		DoorBlockEntity door = findDoor(world, context.getBlockPos());
		if (door == null) return ActionResult.PASS;

		Tier tier = tier(context.getStack());
		if (tier == null) return ActionResult.PASS;
		assignDoorForLink(player, world, door, keyName, tier);
		return ActionResult.CONSUME;
	}

	public static ItemStack createStack(Tier tier, String keyName) {
		String sanitized = sanitizeKeyName(keyName);
		ItemStack stack = new ItemStack(itemFor(tier));
		if (!sanitized.isBlank()) {
			stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal(sanitized)
				.setStyle(Style.EMPTY.withItalic(false).withColor(tier.color())))));
		}
		return stack;
	}

	public static boolean isKeycard(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof KeycardItem;
	}

	public static @Nullable Tier tier(ItemStack stack) {
		return stack.getItem() instanceof KeycardItem keycard ? keycard.tier() : null;
	}

	public static String keyName(ItemStack stack) {
		if (!isKeycard(stack)) return "";
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		if (lore == null || lore.lines().isEmpty()) return "";
		return sanitizeKeyName(lore.lines().getFirst().getString());
	}

	public static boolean keyMatches(ItemStack stack, String keyName) {
		String cardKey = keyName(stack);
		return !cardKey.isBlank() && cardKey.equals(sanitizeKeyName(keyName));
	}

	public static boolean canAssign(ServerPlayerEntity player) {
		return player != null && ((player.isCreative() && player.shouldCancelInteraction())
			|| GexpressPermissions.canEditSetupOptions(player));
	}

	public static @Nullable PendingDoor pendingDoor(ServerPlayerEntity player, World world) {
		if (player == null || world == null) return null;
		PendingDoor pending = PENDING_DOORS.get(player.getUuid());
		return pending != null && pending.worldKey().equals(world.getRegistryKey()) ? pending : null;
	}

	public static void clearPendingDoor(ServerPlayerEntity player) {
		if (player != null) PENDING_DOORS.remove(player.getUuid());
	}

	public static void assignDoorForLink(ServerPlayerEntity player, World world, DoorBlockEntity door, String keyName, Tier tier) {
		String sanitized = sanitizeKeyName(keyName);
		setDoorKeyName(world, door, sanitized);
		PENDING_DOORS.put(player.getUuid(), new PendingDoor(world.getRegistryKey(), door.getPos().toImmutable(), sanitized, tier));
		player.sendMessage(Text.literal("Keycard assigned. Right-click a keycard reader to link it.")
			.formatted(Formatting.AQUA), true);
	}

	public static @Nullable DoorBlockEntity findDoor(World world, BlockPos pos) {
		if (world == null || pos == null) return null;
		BlockState clickedState = world.getBlockState(pos);
		boolean clickedDoor = clickedState.getBlock() instanceof dev.doctor4t.wathe.block.SmallDoorBlock
			|| clickedState.getBlock() instanceof LargeWoodDoorBlock
			|| world.getBlockEntity(pos) instanceof DoorBlockEntity;
		if (!clickedDoor) return null;

		for (int offset = 0; offset <= 2; offset++) {
			if (world.getBlockEntity(pos.down(offset)) instanceof DoorBlockEntity door) {
				return door;
			}
		}
		return null;
	}

	public static void setDoorKeyName(World world, DoorBlockEntity door, String keyName) {
		door.setKeyName(sanitizeKeyName(keyName));
		door.markDirty();
		if (world != null && !world.isClient) {
			world.updateListeners(door.getPos(), door.getCachedState(), door.getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	public static String sanitizeKeyName(String value) {
		if (value == null) return "";
		String normalized = value.replace("\r", "").replace("\n", " ").strip();
		return normalized.length() > MAX_KEY_NAME_LENGTH ? normalized.substring(0, MAX_KEY_NAME_LENGTH) : normalized;
	}

	private static Item itemFor(Tier tier) {
		return switch (tier) {
			case BASIC -> MapSelectItems.KEYCARD_BASIC;
			case STAFF -> MapSelectItems.KEYCARD_STAFF;
			case VIP -> MapSelectItems.KEYCARD_VIP;
			case VAULT -> MapSelectItems.KEYCARD_VAULT;
		};
	}

	public enum Tier {
		BASIC("basic", "Basic Keycard", 0xFF6048),
		STAFF("staff", "Staff Keycard", 0x8DDB62),
		VIP("vip", "VIP Keycard", 0xF7B45A),
		VAULT("vault", "Vault Keycard", 0xEDEDED);

		private final String id;
		private final String displayName;
		private final int color;

		Tier(String id, String displayName, int color) {
			this.id = id;
			this.displayName = displayName;
			this.color = color;
		}

		public String id() {
			return id;
		}

		public String displayName() {
			return displayName;
		}

		public int color() {
			return color;
		}

		public static @Nullable Tier byId(String id) {
			if (id == null) return null;
			for (Tier tier : values()) {
				if (tier.id.equalsIgnoreCase(id)) return tier;
			}
			return null;
		}
	}

	public record PendingDoor(net.minecraft.registry.RegistryKey<World> worldKey, BlockPos doorPos, String keyName, Tier tier) {}
}
