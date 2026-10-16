package dev.mapselect.mixin;

import dev.mapselect.MapSelect;
import dev.mapselect.server.AxiomServerRefresh;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.packets.AxiomServerboundSetBuffer", remap = false)
public abstract class AxiomServerBufferCompatMixin {
	@Unique
	private static Field gexpress$bufferField;
	@Unique
	private static Field gexpress$worldField;
	@Unique
	private static boolean gexpress$warnedBufferField;
	@Unique
	private static boolean gexpress$warnedWorldField;

	@Inject(method = "handle", at = @At("RETURN"), require = 0, remap = false)
	private void gexpress$refreshAxiomEditedBlocks(MinecraftServer server, ServerPlayerEntity player,
			CallbackInfo ci) {
		if (server == null || player == null) return;
		AxiomServerRefresh.refreshAxiomBuffer(gexpress$buffer(), gexpress$packetWorld(server, player));
	}

	@Unique
	private Object gexpress$buffer() {
		try {
			if (gexpress$bufferField == null) {
				gexpress$bufferField = getClass().getDeclaredField("buffer");
				gexpress$bufferField.setAccessible(true);
			}
			return gexpress$bufferField.get(this);
		} catch (Throwable t) {
			if (!gexpress$warnedBufferField) {
				gexpress$warnedBufferField = true;
				MapSelect.LOGGER.warn("Axiom buffer edit compat could not read packet field 'buffer'; live block refresh after Axiom buffer edits is disabled for this packet shape.", t);
			}
			return null;
		}
	}

	@Unique
	@SuppressWarnings("unchecked")
	private ServerWorld gexpress$packetWorld(MinecraftServer server, ServerPlayerEntity player) {
		try {
			if (gexpress$worldField == null) {
				gexpress$worldField = getClass().getDeclaredField("world");
				gexpress$worldField.setAccessible(true);
			}
			Object key = gexpress$worldField.get(this);
			if (key instanceof RegistryKey<?> registryKey) {
				ServerWorld world = server.getWorld((RegistryKey<World>) registryKey);
				if (world != null) return world;
			}
		} catch (Throwable t) {
			if (!gexpress$warnedWorldField) {
				gexpress$warnedWorldField = true;
				MapSelect.LOGGER.warn("Axiom buffer edit compat could not read packet field 'world'; falling back to the editing player's current world.", t);
			}
		}
		return player.getServerWorld();
	}
}
