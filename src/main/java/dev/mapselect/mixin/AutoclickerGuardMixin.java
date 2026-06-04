package dev.mapselect.mixin;

import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class AutoclickerGuardMixin {
	@Unique private static final long GEXPRESS$CLICK_WINDOW_MS = 1000L;
	@Unique private static final long GEXPRESS$CLICK_BURST_MS = 250L;
	@Unique private static final long GEXPRESS$CLICK_DEDUPE_MS = 12L;
	@Unique private static final long GEXPRESS$VIOLATION_RESET_MS = 3500L;
	@Unique private static final int GEXPRESS$MAX_CLICKS_PER_WINDOW = 36;
	@Unique private static final int GEXPRESS$MAX_CLICKS_PER_BURST = 14;
	@Unique private static final int GEXPRESS$SEVERE_CLICKS_PER_WINDOW = 55;
	@Unique private static final int GEXPRESS$SEVERE_CLICKS_PER_BURST = 24;
	@Unique private static final int GEXPRESS$MAX_CLICK_VIOLATIONS = 3;
	@Unique private static final Map<UUID, ArrayDeque<Long>> GEXPRESS$CLICK_WINDOWS = new ConcurrentHashMap<>();
	@Unique private static final Map<UUID, Long> GEXPRESS$LAST_CLICK_AT = new ConcurrentHashMap<>();
	@Unique private static final Map<UUID, Long> GEXPRESS$LAST_VIOLATION_AT = new ConcurrentHashMap<>();
	@Unique private static final Map<UUID, Integer> GEXPRESS$CLICK_VIOLATIONS = new ConcurrentHashMap<>();

	@Shadow public ServerPlayerEntity player;

	@Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
	private void gexpress$guardBlockAttack(PlayerActionC2SPacket packet, CallbackInfo ci) {
		if (packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
			&& gexpress$recordClick(player)) {
			ci.cancel();
		}
	}

	@Inject(method = "onHandSwing", at = @At("HEAD"), cancellable = true)
	private void gexpress$guardHandSwing(HandSwingC2SPacket packet, CallbackInfo ci) {
		if (gexpress$recordClick(player)) ci.cancel();
	}

	@Inject(method = "onPlayerInteractItem", at = @At("HEAD"), cancellable = true)
	private void gexpress$guardItemUse(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
		if (gexpress$recordClick(player)) ci.cancel();
	}

	@Inject(method = "onPlayerInteractBlock", at = @At("HEAD"), cancellable = true)
	private void gexpress$guardBlockUse(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
		if (gexpress$recordClick(player)) ci.cancel();
	}

	@Inject(method = "onPlayerInteractEntity", at = @At("HEAD"), cancellable = true)
	private void gexpress$guardEntityUse(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
		if (gexpress$recordClick(player)) ci.cancel();
	}

	@Unique
	private static boolean gexpress$recordClick(ServerPlayerEntity player) {
		if (player == null || player.isCreative() || player.isSpectator()) return false;

		UUID playerId = player.getUuid();
		long now = System.currentTimeMillis();
		Long lastClickAt = GEXPRESS$LAST_CLICK_AT.get(playerId);
		if (lastClickAt != null && now - lastClickAt < GEXPRESS$CLICK_DEDUPE_MS) {
			return false;
		}
		GEXPRESS$LAST_CLICK_AT.put(playerId, now);

		ArrayDeque<Long> clicks = GEXPRESS$CLICK_WINDOWS.computeIfAbsent(playerId, id -> new ArrayDeque<>());
		int clicksInWindow;
		int clicksInBurst;
		synchronized (clicks) {
			while (!clicks.isEmpty() && now - clicks.peekFirst() > GEXPRESS$CLICK_WINDOW_MS) {
				clicks.removeFirst();
			}
			clicks.addLast(now);
			clicksInWindow = clicks.size();
			clicksInBurst = gexpress$countBurst(clicks, now);
		}

		boolean overLimit = clicksInWindow > GEXPRESS$MAX_CLICKS_PER_WINDOW
			|| clicksInBurst > GEXPRESS$MAX_CLICKS_PER_BURST;
		if (!overLimit) return false;

		Long lastViolationAt = GEXPRESS$LAST_VIOLATION_AT.get(playerId);
		int violations = lastViolationAt == null || now - lastViolationAt > GEXPRESS$VIOLATION_RESET_MS
			? 1
			: GEXPRESS$CLICK_VIOLATIONS.getOrDefault(playerId, 0) + 1;
		GEXPRESS$LAST_VIOLATION_AT.put(playerId, now);
		GEXPRESS$CLICK_VIOLATIONS.put(playerId, violations);

		boolean severe = clicksInWindow > GEXPRESS$SEVERE_CLICKS_PER_WINDOW
			|| clicksInBurst > GEXPRESS$SEVERE_CLICKS_PER_BURST;
		if (severe || violations >= GEXPRESS$MAX_CLICK_VIOLATIONS) {
			gexpress$clearPlayer(playerId);
			player.networkHandler.disconnect(Text.literal("Kicked for using an auto clicker."));
		} else {
			player.sendMessage(Text.literal("Slow down your clicks.").formatted(Formatting.RED), true);
		}
		return true;
	}

	@Unique
	private static void gexpress$clearPlayer(UUID playerId) {
		GEXPRESS$CLICK_WINDOWS.remove(playerId);
		GEXPRESS$LAST_CLICK_AT.remove(playerId);
		GEXPRESS$LAST_VIOLATION_AT.remove(playerId);
		GEXPRESS$CLICK_VIOLATIONS.remove(playerId);
	}

	@Unique
	private static int gexpress$countBurst(ArrayDeque<Long> clicks, long now) {
		int count = 0;
		Iterator<Long> iterator = clicks.descendingIterator();
		while (iterator.hasNext()) {
			long click = iterator.next();
			if (now - click > GEXPRESS$CLICK_BURST_MS) break;
			count++;
		}
		return count;
	}
}
