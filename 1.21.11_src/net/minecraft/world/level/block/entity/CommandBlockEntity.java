package net.minecraft.world.level.block.entity;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class CommandBlockEntity extends BlockEntity {
	private static final boolean DEFAULT_POWERED = false;
	private static final boolean DEFAULT_CONDITION_MET = false;
	private static final boolean DEFAULT_AUTOMATIC = false;
	private boolean powered = false;
	private boolean auto = false;
	private boolean conditionMet = false;
	private final BaseCommandBlock commandBlock = new BaseCommandBlock() {
		@Override
		public void setCommand(String string) {
			super.setCommand(string);
			CommandBlockEntity.this.setChanged();
		}

		@Override
		public void onUpdated(ServerLevel serverLevel) {
			BlockState blockState = serverLevel.getBlockState(CommandBlockEntity.this.worldPosition);
			serverLevel.sendBlockUpdated(CommandBlockEntity.this.worldPosition, blockState, blockState, 3);
		}

		@Override
		public CommandSourceStack createCommandSourceStack(ServerLevel serverLevel, CommandSource commandSource) {
			Direction direction = CommandBlockEntity.this.getBlockState().getValue(CommandBlock.FACING);
			return new CommandSourceStack(
				commandSource,
				Vec3.atCenterOf(CommandBlockEntity.this.worldPosition),
				new Vec2(0.0F, direction.toYRot()),
				serverLevel,
				LevelBasedPermissionSet.GAMEMASTER,
				this.getName().getString(),
				this.getName(),
				serverLevel.getServer(),
				null
			);
		}

		@Override
		public boolean isValid() {
			return !CommandBlockEntity.this.isRemoved();
		}
	};

	public CommandBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.COMMAND_BLOCK, blockPos, blockState);
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		this.commandBlock.save(valueOutput);
		valueOutput.putBoolean("powered", this.isPowered());
		valueOutput.putBoolean("conditionMet", this.wasConditionMet());
		valueOutput.putBoolean("auto", this.isAutomatic());
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.commandBlock.load(valueInput);
		this.powered = valueInput.getBooleanOr("powered", false);
		this.conditionMet = valueInput.getBooleanOr("conditionMet", false);
		this.setAutomatic(valueInput.getBooleanOr("auto", false));
	}

	public BaseCommandBlock getCommandBlock() {
		return this.commandBlock;
	}

	public void setPowered(boolean bl) {
		this.powered = bl;
	}

	public boolean isPowered() {
		return this.powered;
	}

	public boolean isAutomatic() {
		return this.auto;
	}

	public void setAutomatic(boolean bl) {
		boolean bl2 = this.auto;
		this.auto = bl;
		if (!bl2 && bl && !this.powered && this.level != null && this.getMode() != CommandBlockEntity.Mode.SEQUENCE) {
			this.scheduleTick();
		}
	}

	public void onModeSwitch() {
		CommandBlockEntity.Mode mode = this.getMode();
		if (mode == CommandBlockEntity.Mode.AUTO && (this.powered || this.auto) && this.level != null) {
			this.scheduleTick();
		}
	}

	private void scheduleTick() {
		Block block = this.getBlockState().getBlock();
		if (block instanceof CommandBlock) {
			this.markConditionMet();
			this.level.scheduleTick(this.worldPosition, block, 1);
		}
	}

	public boolean wasConditionMet() {
		return this.conditionMet;
	}

	public boolean markConditionMet() {
		this.conditionMet = true;
		if (this.isConditional()) {
			BlockPos blockPos = this.worldPosition.relative(this.level.getBlockState(this.worldPosition).getValue(CommandBlock.FACING).getOpposite());
			if (this.level.getBlockState(blockPos).getBlock() instanceof CommandBlock) {
				BlockEntity blockEntity = this.level.getBlockEntity(blockPos);
				this.conditionMet = blockEntity instanceof CommandBlockEntity && ((CommandBlockEntity)blockEntity).getCommandBlock().getSuccessCount() > 0;
			} else {
				this.conditionMet = false;
			}
		}

		return this.conditionMet;
	}

	public CommandBlockEntity.Mode getMode() {
		BlockState blockState = this.getBlockState();
		if (blockState.is(Blocks.COMMAND_BLOCK)) {
			return CommandBlockEntity.Mode.REDSTONE;
		} else if (blockState.is(Blocks.REPEATING_COMMAND_BLOCK)) {
			return CommandBlockEntity.Mode.AUTO;
		} else {
			return blockState.is(Blocks.CHAIN_COMMAND_BLOCK) ? CommandBlockEntity.Mode.SEQUENCE : CommandBlockEntity.Mode.REDSTONE;
		}
	}

	public boolean isConditional() {
		BlockState blockState = this.level.getBlockState(this.getBlockPos());
		return blockState.getBlock() instanceof CommandBlock ? blockState.getValue(CommandBlock.CONDITIONAL) : false;
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		super.applyImplicitComponents(dataComponentGetter);
		this.commandBlock.setCustomName(dataComponentGetter.get(DataComponents.CUSTOM_NAME));
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		builder.set(DataComponents.CUSTOM_NAME, this.commandBlock.getCustomName());
	}

	@Override
	public void removeComponentsFromTag(ValueOutput valueOutput) {
		super.removeComponentsFromTag(valueOutput);
		valueOutput.discard("CustomName");
		valueOutput.discard("conditionMet");
		valueOutput.discard("powered");
	}

	public enum Mode {
		SEQUENCE,
		AUTO,
		REDSTONE;
	}
}
