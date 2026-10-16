package dev.mapselect.server;

import dev.mapselect.network.axiom.AxiomEditRefreshPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class AxiomServerRefresh {
	private static final int MAX_NOTIFIED_BLOCKS = 65536;

	private AxiomServerRefresh() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(AxiomEditRefreshPayload.ID, AxiomEditRefreshPayload.CODEC);
	}

	public static void refreshAxiomBuffer(Object buffer, ServerWorld world) {
		if (buffer == null || world == null || !FabricLoader.getInstance().isModLoaded("axiom")) return;
		try {
			Class<?> consumerClass = Class.forName("com.moulberry.axiom.collections.PositionConsumer", false,
				AxiomServerRefresh.class.getClassLoader());
			Method forEach = buffer.getClass().getMethod("forEach", consumerClass);
			AtomicInteger notified = new AtomicInteger();
			AtomicBoolean changed = new AtomicBoolean();
			Object consumer = Proxy.newProxyInstance(AxiomServerRefresh.class.getClassLoader(),
				new Class<?>[]{consumerClass}, (proxy, method, args) -> {
					if (!"accept".equals(method.getName()) || args == null || args.length < 4) return null;
					int count = notified.incrementAndGet();
					if (count > MAX_NOTIFIED_BLOCKS) return null;
					int x = (Integer) args[0];
					int y = (Integer) args[1];
					int z = (Integer) args[2];
					notifyBlock(world, new BlockPos(x, y, z));
					changed.set(true);
					return null;
				});
			forEach.invoke(buffer, consumer);
			if (changed.get()) afterAxiomWorldEdit(world);
		} catch (Throwable ignored) {
		}
	}

	public static void refreshAxiomBlockMap(Object blocks, ServerWorld world) {
		if (!(blocks instanceof Map<?, ?> map) || world == null || !FabricLoader.getInstance().isModLoaded("axiom")) {
			return;
		}
		AtomicInteger notified = new AtomicInteger();
		boolean changed = false;
		for (Object key : map.keySet()) {
			if (!(key instanceof BlockPos pos)) continue;
			if (notified.incrementAndGet() > MAX_NOTIFIED_BLOCKS) break;
			notifyBlock(world, pos);
			changed = true;
		}
		if (changed) afterAxiomWorldEdit(world);
	}

	private static void afterAxiomWorldEdit(ServerWorld world) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (ServerPlayNetworking.canSend(player, AxiomEditRefreshPayload.ID)) {
				ServerPlayNetworking.send(player, new AxiomEditRefreshPayload());
			}
		}
	}

	private static void notifyBlock(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
		world.getChunkManager().markForUpdate(pos);
	}
}
