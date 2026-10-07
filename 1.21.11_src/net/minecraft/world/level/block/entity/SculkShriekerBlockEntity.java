package net.minecraft.world.level.block.entity;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.SpawnUtil;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SculkShriekerBlockEntity extends BlockEntity implements GameEventListener.Provider<VibrationSystem.Listener>, VibrationSystem {
	private static final int WARNING_SOUND_RADIUS = 10;
	private static final int WARDEN_SPAWN_ATTEMPTS = 20;
	private static final int WARDEN_SPAWN_RANGE_XZ = 5;
	private static final int WARDEN_SPAWN_RANGE_Y = 6;
	private static final int DARKNESS_RADIUS = 40;
	private static final int SHRIEKING_TICKS = 90;
	private static final Int2ObjectMap<SoundEvent> SOUND_BY_LEVEL = Util.make(new Int2ObjectOpenHashMap<>(), int2ObjectOpenHashMap -> {
		int2ObjectOpenHashMap.put(1, SoundEvents.WARDEN_NEARBY_CLOSE);
		int2ObjectOpenHashMap.put(2, SoundEvents.WARDEN_NEARBY_CLOSER);
		int2ObjectOpenHashMap.put(3, SoundEvents.WARDEN_NEARBY_CLOSEST);
		int2ObjectOpenHashMap.put(4, SoundEvents.WARDEN_LISTENING_ANGRY);
	});
	private static final int DEFAULT_WARNING_LEVEL = 0;
	private int warningLevel = 0;
	private final VibrationSystem.User vibrationUser = new SculkShriekerBlockEntity.VibrationUser();
	private VibrationSystem.Data vibrationData = new VibrationSystem.Data();
	private final VibrationSystem.Listener vibrationListener = new VibrationSystem.Listener(this);

	public SculkShriekerBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.SCULK_SHRIEKER, blockPos, blockState);
	}

	@Override
	public VibrationSystem.Data getVibrationData() {
		return this.vibrationData;
	}

	@Override
	public VibrationSystem.User getVibrationUser() {
		return this.vibrationUser;
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.warningLevel = valueInput.getIntOr("warning_level", 0);
		this.vibrationData = valueInput.read("listener", VibrationSystem.Data.CODEC).orElseGet(VibrationSystem.Data::new);
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		valueOutput.putInt("warning_level", this.warningLevel);
		valueOutput.store("listener", VibrationSystem.Data.CODEC, this.vibrationData);
	}

	public static @Nullable ServerPlayer tryGetPlayer(@Nullable Entity entity) {
		if (entity instanceof ServerPlayer serverPlayer) {
			return serverPlayer;
		} else if (entity != null && entity.getControllingPassenger() instanceof ServerPlayer serverPlayer) {
			return serverPlayer;
		} else if (entity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer serverPlayer2) {
			return serverPlayer2;
		} else {
			return entity instanceof ItemEntity itemEntity && itemEntity.getOwner() instanceof ServerPlayer serverPlayer2 ? serverPlayer2 : null;
		}
	}

	public void tryShriek(ServerLevel serverLevel, @Nullable ServerPlayer serverPlayer) {
		if (serverPlayer != null) {
			BlockState blockState = this.getBlockState();
			if (!blockState.getValue(SculkShriekerBlock.SHRIEKING)) {
				this.warningLevel = 0;
				if (!this.canRespond(serverLevel) || this.tryToWarn(serverLevel, serverPlayer)) {
					this.shriek(serverLevel, serverPlayer);
				}
			}
		}
	}

	private boolean tryToWarn(ServerLevel serverLevel, ServerPlayer serverPlayer) {
		OptionalInt optionalInt = WardenSpawnTracker.tryWarn(serverLevel, this.getBlockPos(), serverPlayer);
		optionalInt.ifPresent(i -> this.warningLevel = i);
		return optionalInt.isPresent();
	}

	private void shriek(ServerLevel serverLevel, @Nullable Entity entity) {
		BlockPos blockPos = this.getBlockPos();
		BlockState blockState = this.getBlockState();
		serverLevel.setBlock(blockPos, blockState.setValue(SculkShriekerBlock.SHRIEKING, true), 2);
		serverLevel.scheduleTick(blockPos, blockState.getBlock(), 90);
		serverLevel.levelEvent(3007, blockPos, 0);
		serverLevel.gameEvent(GameEvent.SHRIEK, blockPos, GameEvent.Context.of(entity));
	}

	private boolean canRespond(ServerLevel serverLevel) {
		return this.getBlockState().getValue(SculkShriekerBlock.CAN_SUMMON)
			&& serverLevel.getDifficulty() != Difficulty.PEACEFUL
			&& serverLevel.getGameRules().get(GameRules.SPAWN_WARDENS);
	}

	@Override
	public void preRemoveSideEffects(BlockPos blockPos, BlockState blockState) {
		if (blockState.getValue(SculkShriekerBlock.SHRIEKING) && this.level instanceof ServerLevel serverLevel) {
			this.tryRespond(serverLevel);
		}
	}

	public void tryRespond(ServerLevel serverLevel) {
		if (this.canRespond(serverLevel) && this.warningLevel > 0) {
			if (!this.trySummonWarden(serverLevel)) {
				this.playWardenReplySound(serverLevel);
			}

			Warden.applyDarknessAround(serverLevel, Vec3.atCenterOf(this.getBlockPos()), null, 40);
		}
	}

	private void playWardenReplySound(Level level) {
		SoundEvent soundEvent = SOUND_BY_LEVEL.get(this.warningLevel);
		if (soundEvent != null) {
			BlockPos blockPos = this.getBlockPos();
			int i = blockPos.getX() + Mth.randomBetweenInclusive(level.random, -10, 10);
			int j = blockPos.getY() + Mth.randomBetweenInclusive(level.random, -10, 10);
			int k = blockPos.getZ() + Mth.randomBetweenInclusive(level.random, -10, 10);
			level.playSound(null, i, j, k, soundEvent, SoundSource.HOSTILE, 5.0F, 1.0F);
		}
	}

	private boolean trySummonWarden(ServerLevel serverLevel) {
		return this.warningLevel < 4
			? false
			: SpawnUtil.trySpawnMob(
					EntityType.WARDEN, EntitySpawnReason.TRIGGERED, serverLevel, this.getBlockPos(), 20, 5, 6, SpawnUtil.Strategy.ON_TOP_OF_COLLIDER, false
				)
				.isPresent();
	}

	public VibrationSystem.Listener getListener() {
		return this.vibrationListener;
	}

	class VibrationUser implements VibrationSystem.User {
		private static final int LISTENER_RADIUS = 8;
		private final PositionSource positionSource = new BlockPositionSource(SculkShriekerBlockEntity.this.worldPosition);

		public VibrationUser() {
		}

		@Override
		public int getListenerRadius() {
			return 8;
		}

		@Override
		public PositionSource getPositionSource() {
			return this.positionSource;
		}

		@Override
		public TagKey<GameEvent> getListenableEvents() {
			return GameEventTags.SHRIEKER_CAN_LISTEN;
		}

		@Override
		public boolean canReceiveVibration(ServerLevel serverLevel, BlockPos blockPos, Holder<GameEvent> holder, GameEvent.Context context) {
			return !SculkShriekerBlockEntity.this.getBlockState().getValue(SculkShriekerBlock.SHRIEKING)
				&& SculkShriekerBlockEntity.tryGetPlayer(context.sourceEntity()) != null;
		}

		@Override
		public void onReceiveVibration(
			ServerLevel serverLevel, BlockPos blockPos, Holder<GameEvent> holder, @Nullable Entity entity, @Nullable Entity entity2, float f
		) {
			SculkShriekerBlockEntity.this.tryShriek(serverLevel, SculkShriekerBlockEntity.tryGetPlayer(entity2 != null ? entity2 : entity));
		}

		@Override
		public void onDataChanged() {
			SculkShriekerBlockEntity.this.setChanged();
		}

		@Override
		public boolean requiresAdjacentChunksToBeTicking() {
			return true;
		}
	}
}
