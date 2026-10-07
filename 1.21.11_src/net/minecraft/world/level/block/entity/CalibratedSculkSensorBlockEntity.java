package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CalibratedSculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.jspecify.annotations.Nullable;

public class CalibratedSculkSensorBlockEntity extends SculkSensorBlockEntity {
	public CalibratedSculkSensorBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.CALIBRATED_SCULK_SENSOR, blockPos, blockState);
	}

	@Override
	public VibrationSystem.User createVibrationUser() {
		return new CalibratedSculkSensorBlockEntity.VibrationUser(this.getBlockPos());
	}

	protected class VibrationUser extends SculkSensorBlockEntity.VibrationUser {
		public VibrationUser(final BlockPos blockPos) {
			super(blockPos);
		}

		@Override
		public int getListenerRadius() {
			return 16;
		}

		@Override
		public boolean canReceiveVibration(ServerLevel serverLevel, BlockPos blockPos, Holder<GameEvent> holder, GameEvent.@Nullable Context context) {
			int i = this.getBackSignal(serverLevel, this.blockPos, CalibratedSculkSensorBlockEntity.this.getBlockState());
			return i != 0 && VibrationSystem.getGameEventFrequency(holder) != i ? false : super.canReceiveVibration(serverLevel, blockPos, holder, context);
		}

		private int getBackSignal(Level level, BlockPos blockPos, BlockState blockState) {
			Direction direction = blockState.getValue(CalibratedSculkSensorBlock.FACING).getOpposite();
			return level.getSignal(blockPos.relative(direction), direction);
		}
	}
}
