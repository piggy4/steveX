package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ComparatorBlockEntity extends BlockEntity {
	private static final int DEFAULT_OUTPUT = 0;
	private int output = 0;

	public ComparatorBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.COMPARATOR, blockPos, blockState);
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		valueOutput.putInt("OutputSignal", this.output);
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.output = valueInput.getIntOr("OutputSignal", 0);
	}

	public int getOutputSignal() {
		return this.output;
	}

	public void setOutputSignal(int i) {
		this.output = i;
	}
}
