package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import org.apache.commons.lang3.Validate;

public class BannerItem extends StandingAndWallBlockItem {
	public BannerItem(Block block, Block block2, Item.Properties properties) {
		super(block, block2, Direction.DOWN, properties);
		Validate.isInstanceOf(AbstractBannerBlock.class, block);
		Validate.isInstanceOf(AbstractBannerBlock.class, block2);
	}

	public DyeColor getColor() {
		return ((AbstractBannerBlock)this.getBlock()).getColor();
	}
}
