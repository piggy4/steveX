package net.minecraft.world.level.block.entity;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.entity.trialspawner.PlayerDetector;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class TrialSpawnerBlockEntity extends BlockEntity implements Spawner, TrialSpawner.StateAccessor {
	private final TrialSpawner trialSpawner = this.createDefaultSpawner();

	public TrialSpawnerBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.TRIAL_SPAWNER, blockPos, blockState);
	}

	private TrialSpawner createDefaultSpawner() {
		PlayerDetector playerDetector = SharedConstants.DEBUG_TRIAL_SPAWNER_DETECTS_SHEEP_AS_PLAYERS ? PlayerDetector.SHEEP : PlayerDetector.NO_CREATIVE_PLAYERS;
		PlayerDetector.EntitySelector entitySelector = PlayerDetector.EntitySelector.SELECT_FROM_LEVEL;
		return new TrialSpawner(TrialSpawner.FullConfig.DEFAULT, this, playerDetector, entitySelector);
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.trialSpawner.load(valueInput);
		if (this.level != null) {
			this.markUpdated();
		}
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		this.trialSpawner.store(valueOutput);
	}

	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return this.trialSpawner.getStateData().getUpdateTag(this.getBlockState().getValue(TrialSpawnerBlock.STATE));
	}

	@Override
	public void setEntityId(EntityType<?> entityType, RandomSource randomSource) {
		if (this.level == null) {
			Util.logAndPauseIfInIde("Expected non-null level");
		} else {
			this.trialSpawner.overrideEntityToSpawn(entityType, this.level);
			this.setChanged();
		}
	}

	public TrialSpawner getTrialSpawner() {
		return this.trialSpawner;
	}

	@Override
	public TrialSpawnerState getState() {
		return !this.getBlockState().hasProperty(BlockStateProperties.TRIAL_SPAWNER_STATE)
			? TrialSpawnerState.INACTIVE
			: this.getBlockState().getValue(BlockStateProperties.TRIAL_SPAWNER_STATE);
	}

	@Override
	public void setState(Level level, TrialSpawnerState trialSpawnerState) {
		this.setChanged();
		level.setBlockAndUpdate(this.worldPosition, this.getBlockState().setValue(BlockStateProperties.TRIAL_SPAWNER_STATE, trialSpawnerState));
	}

	@Override
	public void markUpdated() {
		this.setChanged();
		if (this.level != null) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
		}
	}
}
