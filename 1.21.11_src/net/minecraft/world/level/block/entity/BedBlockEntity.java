package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

public class BedBlockEntity extends BlockEntity {
	private final DyeColor color;

	public BedBlockEntity(BlockPos blockPos, BlockState blockState) {
		this(blockPos, blockState, ((BedBlock)blockState.getBlock()).getColor());
	}

	public BedBlockEntity(BlockPos blockPos, BlockState blockState, DyeColor dyeColor) {
		super(BlockEntityType.BED, blockPos, blockState);
		this.color = dyeColor;
	}

	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	public DyeColor getColor() {
		return this.color;
	}
}
