package dev.mapselect.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import dev.doctor4t.wathe.client.WatheClient;
import dev.mapselect.client.replay.ClientReplay;
import dev.mapselect.replay.ReplaySceneryOffsets;
import dev.mapselect.MapSelect;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.ByteBuffer;
import java.lang.reflect.Field;

@Pseudo
@Mixin(value = DefaultChunkRenderer.class, priority = 900, remap = false)
public abstract class ReplaySodiumSceneryMixin {
    // Resolve after both mixins have been applied; injected fields cannot safely be shadowed
    // during preprocessing, and @Unique may rename Wathe's field when another addon conflicts.
    @Unique private static Field gexpress$bufferField;
    @Unique private static boolean gexpress$bufferResolved;
    @Unique private static ByteBuffer gexpress$sceneryBuffer() {
        if (!gexpress$bufferResolved) {
            gexpress$bufferResolved = true;
            for (Field field : DefaultChunkRenderer.class.getDeclaredFields()) {
                if (field.getType() == ByteBuffer.class && field.getName().contains("wathe_buffer")) {
                    field.setAccessible(true); gexpress$bufferField = field; break;
                }
            }
            if (gexpress$bufferField == null) MapSelect.LOGGER.warn("Wathe's Sodium scenery buffer was not found; replay scenery correction is unavailable");
        }
        if (gexpress$bufferField == null) return null;
        try { return (ByteBuffer)gexpress$bufferField.get(null); }
        catch (IllegalAccessException e) { throw new IllegalStateException("Cannot access Wathe's scenery buffer", e); }
    }

    @Inject(method = "fillCommandBuffer", at = @At(value = "INVOKE",
        target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataUnsafe;getSliceMask(J)I", shift = At.Shift.AFTER))
    private static void gexpress$recordedScenery(MultiDrawBatch batch, RenderRegion region,
        SectionRenderDataStorage storage, ChunkRenderList list, CameraTransform camera,
        TerrainRenderPass pass, boolean culling, CallbackInfo ci, @Local(name = "sectionIndex") int section) {
        if (!ClientReplay.active || !ClientReplay.hasTrain || !WatheClient.isTrainMoving()) return;
        ByteBuffer buffer = gexpress$sceneryBuffer(); if (buffer == null) return;
        var translation = ClientReplay.stageTranslation();
        int x = region.getOriginX() + LocalSectionIndex.unpackX(section) * 16 - translation.getX();
        int y = region.getOriginY() + LocalSectionIndex.unpackY(section) * 16 - translation.getY();
        int z = region.getOriginZ() + LocalSectionIndex.unpackZ(section) * 16 - translation.getZ();
        ReplaySceneryOffsets.write(buffer, section, x, y, z, camera.intZ - translation.getZ(),
            camera.fracX, camera.fracY, camera.fracZ, ClientReplay.sceneryTime(), WatheClient.getTrainSpeed());
    }
}
