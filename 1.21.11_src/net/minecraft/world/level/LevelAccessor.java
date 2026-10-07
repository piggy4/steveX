package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import org.jspecify.annotations.Nullable;

public interface LevelAccessor extends CommonLevelAccessor, LevelReader, ScheduledTickAccess {
	long nextSubTickCount();

	@Override
	default <T> ScheduledTick<T> createTick(BlockPos blockPos, T object, int i, TickPriority tickPriority) {
		return new ScheduledTick<>(object, blockPos, this.getGameTime() + i, tickPriority, this.nextSubTickCount());
	}

	@Override
	default <T> ScheduledTick<T> createTick(BlockPos blockPos, T object, int i) {
		return new ScheduledTick<>(object, blockPos, this.getGameTime() + i, this.nextSubTickCount());
	}

	LevelData getLevelData();

	default long getGameTime() {
		return this.getLevelData().getGameTime();
	}

	@Nullable MinecraftServer getServer();

	default Difficulty getDifficulty() {
		return this.getLevelData().getDifficulty();
	}

	ChunkSource getChunkSource();

	@Override
	default boolean hasChunk(int i, int j) {
		return this.getChunkSource().hasChunk(i, j);
	}

	RandomSource getRandom();

	default void updateNeighborsAt(BlockPos blockPos, Block block) {
	}

	default void neighborShapeChanged(Direction direction, BlockPos blockPos, BlockPos blockPos2, BlockState blockState, @Block.UpdateFlags int i, int j) {
		NeighborUpdater.executeShapeUpdate(this, direction, blockPos, blockPos2, blockState, i, j - 1);
	}

	default void playSound(@Nullable Entity entity, BlockPos blockPos, SoundEvent soundEvent, SoundSource soundSource) {
		this.playSound(entity, blockPos, soundEvent, soundSource, 1.0F, 1.0F);
	}

	void playSound(@Nullable Entity entity, BlockPos blockPos, SoundEvent soundEvent, SoundSource soundSource, float f, float g);

	void addParticle(ParticleOptions particleOptions, double d, double e, double f, double g, double h, double i);

	void levelEvent(@Nullable Entity entity, int i, BlockPos blockPos, int j);

	default void levelEvent(int i, BlockPos blockPos, int j) {
		this.levelEvent(null, i, blockPos, j);
	}

	void gameEvent(Holder<GameEvent> holder, Vec3 vec3, GameEvent.Context context);

	default void gameEvent(@Nullable Entity entity, Holder<GameEvent> holder, Vec3 vec3) {
		this.gameEvent(holder, vec3, new GameEvent.Context(entity, null));
	}

	default void gameEvent(@Nullable Entity entity, Holder<GameEvent> holder, BlockPos blockPos) {
		this.gameEvent(holder, blockPos, new GameEvent.Context(entity, null));
	}

	default void gameEvent(Holder<GameEvent> holder, BlockPos blockPos, GameEvent.Context context) {
		this.gameEvent(holder, Vec3.atCenterOf(blockPos), context);
	}

	default void gameEvent(ResourceKey<GameEvent> resourceKey, BlockPos blockPos, GameEvent.Context context) {
		this.gameEvent(this.registryAccess().lookupOrThrow(Registries.GAME_EVENT).getOrThrow(resourceKey), blockPos, context);
	}
}
