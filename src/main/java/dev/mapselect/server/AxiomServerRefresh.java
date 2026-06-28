package dev.mapselect.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class AxiomServerRefresh {
	private static final int MAX_NOTIFIED_BLOCKS = 65536;

	private AxiomServerRefresh() {}

	public static void refreshAxiomBuffer(Object buffer, ServerWorld world) {
		if (buffer == null || world == null || !FabricLoader.getInstance().isModLoaded("axiom")) return;
		try {
			Class<?> consumerClass = Class.forName("com.moulberry.axiom.collections.PositionConsumer", false,
				AxiomServerRefresh.class.getClassLoader());
			Method forEach = buffer.getClass().getMethod("forEach", consumerClass);
			AtomicInteger notified = new AtomicInteger();
			Object consumer = Proxy.newProxyInstance(AxiomServerRefresh.class.getClassLoader(),
				new Class<?>[]{consumerClass}, (proxy, method, args) -> {
					if (!"accept".equals(method.getName()) || args == null || args.length < 4) return null;
					int count = notified.incrementAndGet();
					if (count > MAX_NOTIFIED_BLOCKS) return null;
					int x = (Integer) args[0];
					int y = (Integer) args[1];
					int z = (Integer) args[2];
					notifyBlock(world, new BlockPos(x, y, z));
					return null;
				});
			forEach.invoke(buffer, consumer);
		} catch (Throwable ignored) {
		}
	}

	public static void refreshAxiomBlockMap(Object blocks, ServerWorld world) {
		if (!(blocks instanceof Map<?, ?> map) || world == null || !FabricLoader.getInstance().isModLoaded("axiom")) {
			return;
		}
		AtomicInteger notified = new AtomicInteger();
		for (Object key : map.keySet()) {
			if (!(key instanceof BlockPos pos)) continue;
			if (notified.incrementAndGet() > MAX_NOTIFIED_BLOCKS) return;
			notifyBlock(world, pos);
		}
	}

	private static void notifyBlock(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
		world.getChunkManager().markForUpdate(pos);
	}
}
