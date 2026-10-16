package dev.mapselect.replay;

import dev.mapselect.mixin.ReplayDataTrackerAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ReplayEntityState {
    private ReplayEntityState() {}
    private static final Set<String> MOTION_TAGS = Set.of("Pos", "Motion", "Rotation", "FallDistance", "Air", "Fire", "OnGround", "HurtTime", "DeathTime", "PortalCooldown", "TicksFrozen");
    public static NbtCompound persistentState(NbtCompound saved) {
        NbtCompound state = new NbtCompound();
        for (String key : saved.getKeys()) if (!key.startsWith("replay") && !MOTION_TAGS.contains(key)) state.put(key, saved.get(key));
        return state;
    }
    public static UUID ghostUuid(UUID original) {
        return UUID.nameUUIDFromBytes(("gexpress:replay:" + original).getBytes(StandardCharsets.UTF_8));
    }
    public static void capture(Entity entity, NbtCompound saved) {
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), entity.getRegistryManager());
        try {
            for (DataTracker.Entry<?> entry : ((ReplayDataTrackerAccessor)entity.getDataTracker()).gexpress$replayEntries()) entry.toSerialized().write(buf);
            buf.writeByte(255);
            byte[] bytes = new byte[buf.readableBytes()]; buf.readBytes(bytes); saved.putByteArray("replayTracked", bytes);
            saved.putInt("replayAge", entity.age);
        } finally { buf.release(); }
    }
    public static void apply(Entity entity, NbtCompound saved) {
        if (!saved.contains("replayTracked")) return; // Older recordings remain readable.
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.wrappedBuffer(saved.getByteArray("replayTracked")), entity.getRegistryManager());
        try {
            List<DataTracker.SerializedEntry<?>> entries = new ArrayList<>();
            for (int id = buf.readUnsignedByte(); id != 255; id = buf.readUnsignedByte()) entries.add(DataTracker.SerializedEntry.fromBuf(buf, id));
            // ServerPlayer has a few tracker entries that OtherClientPlayer does not have.
            DataTracker.Entry<?>[] supported = ((ReplayDataTrackerAccessor)entity.getDataTracker()).gexpress$replayEntries();
            entries.removeIf(e -> e.id() >= supported.length || supported[e.id()].getData().dataType() != e.handler() || Objects.equals(supported[e.id()].get(), e.value()));
            if (!entries.isEmpty()) entity.getDataTracker().writeUpdatedEntries(entries);
            entity.age = saved.getInt("replayAge");
        } finally { buf.release(); }
    }
}
