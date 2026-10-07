package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.TestBlockMode;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class TestBlockEntity extends BlockEntity {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String DEFAULT_MESSAGE = "";
	private static final boolean DEFAULT_POWERED = false;
	private TestBlockMode mode;
	private String message = "";
	private boolean powered = false;
	private boolean triggered;

	public TestBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.TEST_BLOCK, blockPos, blockState);
		this.mode = blockState.getValue(TestBlock.MODE);
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		valueOutput.store("mode", TestBlockMode.CODEC, this.mode);
		valueOutput.putString("message", this.message);
		valueOutput.putBoolean("powered", this.powered);
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		this.mode = valueInput.read("mode", TestBlockMode.CODEC).orElse(TestBlockMode.FAIL);
		this.message = valueInput.getStringOr("message", "");
		this.powered = valueInput.getBooleanOr("powered", false);
	}

	private void updateBlockState() {
		if (this.level != null) {
			BlockPos blockPos = this.getBlockPos();
			BlockState blockState = this.level.getBlockState(blockPos);
			if (blockState.is(Blocks.TEST_BLOCK)) {
				this.level.setBlock(blockPos, blockState.setValue(TestBlock.MODE, this.mode), 2);
			}
		}
	}

	public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return this.saveCustomOnly(provider);
	}

	public boolean isPowered() {
		return this.powered;
	}

	public void setPowered(boolean bl) {
		this.powered = bl;
	}

	public TestBlockMode getMode() {
		return this.mode;
	}

	public void setMode(TestBlockMode testBlockMode) {
		this.mode = testBlockMode;
		this.updateBlockState();
	}

	private Block getBlockType() {
		return this.getBlockState().getBlock();
	}

	public void reset() {
		this.triggered = false;
		if (this.mode == TestBlockMode.START && this.level != null) {
			this.setPowered(false);
			this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockType());
		}
	}

	public void trigger() {
		if (this.mode == TestBlockMode.START && this.level != null) {
			this.setPowered(true);
			BlockPos blockPos = this.getBlockPos();
			this.level.updateNeighborsAt(blockPos, this.getBlockType());
			this.level.getBlockTicks().willTickThisTick(blockPos, this.getBlockType());
			this.log();
		} else {
			if (this.mode == TestBlockMode.LOG) {
				this.log();
			}

			this.triggered = true;
		}
	}

	public void log() {
		if (!this.message.isBlank()) {
			LOGGER.info("Test {} (at {}): {}", this.mode.getSerializedName(), this.getBlockPos(), this.message);
		}
	}

	public boolean hasTriggered() {
		return this.triggered;
	}

	public String getMessage() {
		return this.message;
	}

	public void setMessage(String string) {
		this.message = string;
	}
}
