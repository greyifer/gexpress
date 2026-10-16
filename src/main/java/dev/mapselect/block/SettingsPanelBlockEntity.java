package dev.mapselect.block;

import dev.mapselect.registry.MapSelectBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

public final class SettingsPanelBlockEntity extends BlockEntity {
    public static final String[] SECTIONS = {"Game", "Maps & Trains", "Roles", "Modifiers"};
    private int section, category, offset, total;
    private NbtList rows = new NbtList();
    private String selectedMap = "";
    public SettingsPanelBlockEntity(BlockPos pos, BlockState state) { super(MapSelectBlockEntities.SETTINGS_PANEL, pos, state); }
    public int section() { return section; }
    public int category(){return category;}
    public void selectCategory(int category){
        category=Math.floorMod(category,SettingsPanelCategories.count(section));
        if(this.category==category)return;this.category=category;markDirty();
        if(world!=null&&!world.isClient)world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);
    }
    public int offset() { return offset; }
    public int total() { return total; }
    public NbtList rows() { return rows; }
    public String selectedMap() { return selectedMap; }
    public void selectMap(String name) { if (!selectedMap.equals(name)) { selectedMap = name; markDirty(); } }
    public void display(int section, int offset, int total, NbtList rows) {
        section = Math.floorMod(section, SECTIONS.length); offset = Math.max(0, offset);
        if (this.section == section && this.offset == offset && this.total == total && this.rows.equals(rows)) return;
        this.section = section; this.offset = offset; this.total = total; this.rows = rows.copy();
        markDirty();
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries); nbt.putInt("Category",category);nbt.putInt("Section", section); nbt.putInt("Offset", offset);
        nbt.putInt("Total", total); nbt.put("Rows", rows.copy());
        nbt.putString("SelectedMap", selectedMap);
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries); section = Math.floorMod(nbt.getInt("Section"), SECTIONS.length);
        category=Math.floorMod(nbt.getInt("Category"),SettingsPanelCategories.count(section));
        offset = Math.clamp(nbt.getInt("Offset"), 0, 10000); total = Math.clamp(nbt.getInt("Total"), 0, 10000);
        rows = nbt.getList("Rows", NbtElement.COMPOUND_TYPE);
        selectedMap = nbt.getString("SelectedMap");
    }
    @Override public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
}
