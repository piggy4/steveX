package net.minecraft.server.level;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class PlayerSpawnFinder {
	private static final EntityDimensions PLAYER_DIMENSIONS = EntityType.PLAYER.getDimensions();
	private static final int ABSOLUTE_MAX_ATTEMPTS = 1024;
	private final ServerLevel level;
	private final BlockPos spawnSuggestion;
	private final int radius;
	private final int candidateCount;
	private final int coprime;
	private final int offset;
	private int nextCandidateIndex;
	private final CompletableFuture<Vec3> finishedFuture = new CompletableFuture<>();

	private PlayerSpawnFinder(ServerLevel serverLevel, BlockPos blockPos, int i) {
		this.level = serverLevel;
		this.spawnSuggestion = blockPos;
		this.radius = i;
		long l = i * 2L + 1L;
		this.candidateCount = (int)Math.min(1024L, l * l);
		this.coprime = getCoprime(this.candidateCount);
		this.offset = RandomSource.create().nextInt(this.candidateCount);
	}

	public static CompletableFuture<Vec3> findSpawn(ServerLevel serverLevel, BlockPos blockPos) {
		if (serverLevel.dimensionType().hasSkyLight() && serverLevel.getServer().getWorldData().getGameType() != GameType.ADVENTURE) {
			int i = Math.max(0, serverLevel.getGameRules().get(GameRules.RESPAWN_RADIUS));
			int j = Mth.floor(serverLevel.getWorldBorder().getDistanceToBorder(blockPos.getX(), blockPos.getZ()));
			if (j < i) {
				i = j;
			}

			if (j <= 1) {
				i = 1;
			}

			PlayerSpawnFinder playerSpawnFinder = new PlayerSpawnFinder(serverLevel, blockPos, i);
			playerSpawnFinder.scheduleNext();
			return playerSpawnFinder.finishedFuture;
		} else {
			return CompletableFuture.completedFuture(fixupSpawnHeight(serverLevel, blockPos));
		}
	}

	private void scheduleNext() {
		int i = this.nextCandidateIndex++;
		if (i < this.candidateCount) {
			int j = (this.offset + this.coprime * i) % this.candidateCount;
			int k = j % (this.radius * 2 + 1);
			int l = j / (this.radius * 2 + 1);
			int m = this.spawnSuggestion.getX() + k - this.radius;
			int n = this.spawnSuggestion.getZ() + l - this.radius;
			this.scheduleCandidate(m, n, i, () -> {
				BlockPos blockPos = getOverworldRespawnPos(this.level, m, n);
				return blockPos != null && noCollisionNoLiquid(this.level, blockPos) ? Optional.of(Vec3.atBottomCenterOf(blockPos)) : Optional.empty();
			});
		} else {
			this.scheduleCandidate(this.spawnSuggestion.getX(), this.spawnSuggestion.getZ(), i, () -> Optional.of(fixupSpawnHeight(this.level, this.spawnSuggestion)));
		}
	}

	private static Vec3 fixupSpawnHeight(CollisionGetter collisionGetter, BlockPos blockPos) {
		BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable();

		while (!noCollisionNoLiquid(collisionGetter, mutableBlockPos) && mutableBlockPos.getY() < collisionGetter.getMaxY()) {
			mutableBlockPos.move(Direction.UP);
		}

		mutableBlockPos.move(Direction.DOWN);

		while (noCollisionNoLiquid(collisionGetter, mutableBlockPos) && mutableBlockPos.getY() > collisionGetter.getMinY()) {
			mutableBlockPos.move(Direction.DOWN);
		}

		mutableBlockPos.move(Direction.UP);
		return Vec3.atBottomCenterOf(mutableBlockPos);
	}

	private static boolean noCollisionNoLiquid(CollisionGetter collisionGetter, BlockPos blockPos) {
		return collisionGetter.noCollision(null, PLAYER_DIMENSIONS.makeBoundingBox(blockPos.getBottomCenter()), true);
	}

	private static int getCoprime(int i) {
		return i <= 16 ? i - 1 : 17;
	}

	private void scheduleCandidate(int i, int j, int k, Supplier<Optional<Vec3>> supplier) {
		if (!this.finishedFuture.isDone()) {
			int l = SectionPos.blockToSectionCoord(i);
			int m = SectionPos.blockToSectionCoord(j);
			this.level.getChunkSource().addTicketAndLoadWithRadius(TicketType.SPAWN_SEARCH, new ChunkPos(l, m), 0).whenCompleteAsync((object, throwable) -> {
				if (throwable == null) {
					try {
						Optional<Vec3> optional = supplier.get();
						if (optional.isPresent()) {
							this.finishedFuture.complete(optional.get());
						} else {
							this.scheduleNext();
						}
					} catch (Throwable throwable2) {
						throwable = throwable2;
					}
				}

				if (throwable != null) {
					CrashReport crashReport = CrashReport.forThrowable(throwable, "Searching for spawn");
					CrashReportCategory crashReportCategory = crashReport.addCategory("Spawn Lookup");
					crashReportCategory.setDetail("Origin", this.spawnSuggestion::toString);
					crashReportCategory.setDetail("Radius", () -> Integer.toString(this.radius));
					crashReportCategory.setDetail("Candidate", () -> "[" + i + "," + j + "]");
					crashReportCategory.setDetail("Progress", () -> k + " out of " + this.candidateCount);
					this.finishedFuture.completeExceptionally(new ReportedException(crashReport));
				}
			}, this.level.getServer());
		}
	}

	protected static @Nullable BlockPos getOverworldRespawnPos(ServerLevel serverLevel, int i, int j) {
		boolean bl = serverLevel.dimensionType().hasCeiling();
		LevelChunk levelChunk = serverLevel.getChunk(SectionPos.blockToSectionCoord(i), SectionPos.blockToSectionCoord(j));
		int k = bl ? serverLevel.getChunkSource().getGenerator().getSpawnHeight(serverLevel) : levelChunk.getHeight(Heightmap.Types.MOTION_BLOCKING, i & 15, j & 15);
		if (k < serverLevel.getMinY()) {
			return null;
		}

		int l = levelChunk.getHeight(Heightmap.Types.WORLD_SURFACE, i & 15, j & 15);
		if (l <= k && l > levelChunk.getHeight(Heightmap.Types.OCEAN_FLOOR, i & 15, j & 15)) {
			return null;
		}

		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

		for (int m = k + 1; m >= serverLevel.getMinY(); m--) {
			mutableBlockPos.set(i, m, j);
			BlockState blockState = serverLevel.getBlockState(mutableBlockPos);
			if (!blockState.getFluidState().isEmpty()) {
				break;
			}

			if (Block.isFaceFull(blockState.getCollisionShape(serverLevel, mutableBlockPos), Direction.UP)) {
				return mutableBlockPos.above().immutable();
			}
		}

		return null;
	}

	public static @Nullable BlockPos getSpawnPosInChunk(ServerLevel serverLevel, ChunkPos chunkPos) {
		if (SharedConstants.debugVoidTerrain(chunkPos)) {
			return null;
		}

		for (int i = chunkPos.getMinBlockX(); i <= chunkPos.getMaxBlockX(); i++) {
			for (int j = chunkPos.getMinBlockZ(); j <= chunkPos.getMaxBlockZ(); j++) {
				BlockPos blockPos = getOverworldRespawnPos(serverLevel, i, j);
				if (blockPos != null) {
					return blockPos;
				}
			}
		}

		return null;
	}
}
