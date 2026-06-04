package dev.mapselect.item;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Identifier;

public class FusedLedgeItem extends BlockItem {
	private final Identifier ledgeId;

	public FusedLedgeItem(Block block, Settings settings, Identifier ledgeId) {
		super(block, settings);
		this.ledgeId = ledgeId;
	}

	public Identifier getLedgeId() {
		return ledgeId;
	}
}
