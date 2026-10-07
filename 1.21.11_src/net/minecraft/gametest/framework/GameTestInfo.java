package net.minecraft.gametest.framework;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2LongMap.Entry;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class GameTestInfo {
	private final Holder.Reference<GameTestInstance> test;
	private @Nullable BlockPos testBlockPos;
	private final ServerLevel level;
	private final Collection<GameTestListener> listeners = Lists.newArrayList();
	private final int timeoutTicks;
	private final Collection<GameTestSequence> sequences = Lists.newCopyOnWriteArrayList();
	private final Object2LongMap<Runnable> runAtTickTimeMap = new Object2LongOpenHashMap<>();
	private boolean placedStructure;
	private boolean chunksLoaded;
	private int tickCount;
	private boolean started;
	private final RetryOptions retryOptions;
	private final Stopwatch timer = Stopwatch.createUnstarted();
	private boolean done;
	private final Rotation extraRotation;
	private @Nullable GameTestException error;
	private @Nullable TestInstanceBlockEntity testInstanceBlockEntity;

	public GameTestInfo(Holder.Reference<GameTestInstance> reference, Rotation rotation, ServerLevel serverLevel, RetryOptions retryOptions) {
		this.test = reference;
		this.level = serverLevel;
		this.retryOptions = retryOptions;
		this.timeoutTicks = reference.value().maxTicks();
		this.extraRotation = rotation;
	}

	public void setTestBlockPos(@Nullable BlockPos blockPos) {
		this.testBlockPos = blockPos;
	}

	public GameTestInfo startExecution(int i) {
		this.tickCount = -(this.test.value().setupTicks() + i + 1);
		return this;
	}

	public void placeStructure() {
		if (!this.placedStructure) {
			TestInstanceBlockEntity testInstanceBlockEntity = this.getTestInstanceBlockEntity();
			if (!testInstanceBlockEntity.placeStructure()) {
				this.fail(Component.translatable("test.error.structure.failure", testInstanceBlockEntity.getTestName().getString()));
			}

			this.placedStructure = true;
			testInstanceBlockEntity.encaseStructure();
			BoundingBox boundingBox = testInstanceBlockEntity.getStructureBoundingBox();
			this.level.getBlockTicks().clearArea(boundingBox);
			this.level.clearBlockEvents(boundingBox);
			this.listeners.forEach(gameTestListener -> gameTestListener.testStructureLoaded(this));
		}
	}

	public void tick(GameTestRunner gameTestRunner) {
		if (!this.isDone()) {
			if (!this.placedStructure) {
				this.fail(Component.translatable("test.error.ticking_without_structure"));
			}

			if (this.testInstanceBlockEntity == null) {
				this.fail(Component.translatable("test.error.missing_block_entity"));
			}

			if (this.error != null) {
				this.finish();
			}

			if (this.chunksLoaded
				|| this.testInstanceBlockEntity.getStructureBoundingBox().intersectingChunks().allMatch(this.level::areEntitiesActuallyLoadedAndTicking)) {
				this.chunksLoaded = true;
				this.tickInternal();
				if (this.isDone()) {
					if (this.error != null) {
						this.listeners.forEach(gameTestListener -> gameTestListener.testFailed(this, gameTestRunner));
					} else {
						this.listeners.forEach(gameTestListener -> gameTestListener.testPassed(this, gameTestRunner));
					}
				}
			}
		}
	}

	private void tickInternal() {
		this.tickCount++;
		if (this.tickCount >= 0) {
			if (!this.started) {
				this.startTest();
			}

			ObjectIterator<Entry<Runnable>> objectIterator = this.runAtTickTimeMap.object2LongEntrySet().iterator();

			while (objectIterator.hasNext()) {
				Entry<Runnable> entry = objectIterator.next();
				if (entry.getLongValue() <= this.tickCount) {
					try {
						entry.getKey().run();
					} catch (GameTestException gameTestException) {
						this.fail(gameTestException);
					} catch (Exception exception) {
						this.fail(new UnknownGameTestException(exception));
					}

					objectIterator.remove();
				}
			}

			if (this.tickCount > this.timeoutTicks) {
				if (this.sequences.isEmpty()) {
					this.fail(new GameTestTimeoutException(Component.translatable("test.error.timeout.no_result", this.test.value().maxTicks())));
				} else {
					this.sequences.forEach(gameTestSequence -> gameTestSequence.tickAndFailIfNotComplete(this.tickCount));
					if (this.error == null) {
						this.fail(new GameTestTimeoutException(Component.translatable("test.error.timeout.no_sequences_finished", this.test.value().maxTicks())));
					}
				}
			} else {
				this.sequences.forEach(gameTestSequence -> gameTestSequence.tickAndContinue(this.tickCount));
			}
		}
	}

	private void startTest() {
		if (!this.started) {
			this.started = true;
			this.timer.start();
			this.getTestInstanceBlockEntity().setRunning();

			try {
				this.test.value().run(new GameTestHelper(this));
			} catch (GameTestException gameTestException) {
				this.fail(gameTestException);
			} catch (Exception exception) {
				this.fail(new UnknownGameTestException(exception));
			}
		}
	}

	public void setRunAtTickTime(long l, Runnable runnable) {
		this.runAtTickTimeMap.put(runnable, l);
	}

	public Identifier id() {
		return this.test.key().identifier();
	}

	public @Nullable BlockPos getTestBlockPos() {
		return this.testBlockPos;
	}

	public BlockPos getTestOrigin() {
		return this.testInstanceBlockEntity.getStartCorner();
	}

	public AABB getStructureBounds() {
		TestInstanceBlockEntity testInstanceBlockEntity = this.getTestInstanceBlockEntity();
		return testInstanceBlockEntity.getStructureBounds();
	}

	public TestInstanceBlockEntity getTestInstanceBlockEntity() {
		if (this.testInstanceBlockEntity == null) {
			if (this.testBlockPos == null) {
				throw new IllegalStateException("This GameTestInfo has no position");
			}

			if (this.level.getBlockEntity(this.testBlockPos) instanceof TestInstanceBlockEntity testInstanceBlockEntity) {
				this.testInstanceBlockEntity = testInstanceBlockEntity;
			}

			if (this.testInstanceBlockEntity == null) {
				throw new IllegalStateException("Could not find a test instance block entity at the given coordinate " + this.testBlockPos);
			}
		}

		return this.testInstanceBlockEntity;
	}

	public ServerLevel getLevel() {
		return this.level;
	}

	public boolean hasSucceeded() {
		return this.done && this.error == null;
	}

	public boolean hasFailed() {
		return this.error != null;
	}

	public boolean hasStarted() {
		return this.started;
	}

	public boolean isDone() {
		return this.done;
	}

	public long getRunTime() {
		return this.timer.elapsed(TimeUnit.MILLISECONDS);
	}

	private void finish() {
		if (!this.done) {
			this.done = true;
			if (this.timer.isRunning()) {
				this.timer.stop();
			}
		}
	}

	public void succeed() {
		if (this.error == null) {
			this.finish();
			AABB aABB = this.getStructureBounds();
			List<Entity> list = this.getLevel().getEntitiesOfClass(Entity.class, aABB.inflate(1.0), entity -> !(entity instanceof Player));
			list.forEach(entity -> entity.remove(Entity.RemovalReason.DISCARDED));
		}
	}

	public void fail(Component component) {
		this.fail(new GameTestAssertException(component, this.tickCount));
	}

	public void fail(GameTestException gameTestException) {
		this.error = gameTestException;
	}

	public @Nullable GameTestException getError() {
		return this.error;
	}

	@Override
	public String toString() {
		return this.id().toString();
	}

	public void addListener(GameTestListener gameTestListener) {
		this.listeners.add(gameTestListener);
	}

	public @Nullable GameTestInfo prepareTestStructure() {
		TestInstanceBlockEntity testInstanceBlockEntity = this.createTestInstanceBlock(Objects.requireNonNull(this.testBlockPos), this.extraRotation, this.level);
		if (testInstanceBlockEntity != null) {
			this.testInstanceBlockEntity = testInstanceBlockEntity;
			this.placeStructure();
			return this;
		} else {
			return null;
		}
	}

	private @Nullable TestInstanceBlockEntity createTestInstanceBlock(BlockPos blockPos, Rotation rotation, ServerLevel serverLevel) {
		serverLevel.setBlockAndUpdate(blockPos, Blocks.TEST_INSTANCE_BLOCK.defaultBlockState());
		if (serverLevel.getBlockEntity(blockPos) instanceof TestInstanceBlockEntity testInstanceBlockEntity) {
			ResourceKey<GameTestInstance> resourceKey = this.getTestHolder().key();
			Vec3i vec3i = TestInstanceBlockEntity.getStructureSize(serverLevel, resourceKey).orElse(new Vec3i(1, 1, 1));
			testInstanceBlockEntity.set(
				new TestInstanceBlockEntity.Data(Optional.of(resourceKey), vec3i, rotation, false, TestInstanceBlockEntity.Status.CLEARED, Optional.empty())
			);
			return testInstanceBlockEntity;
		} else {
			return null;
		}
	}

	int getTick() {
		return this.tickCount;
	}

	GameTestSequence createSequence() {
		GameTestSequence gameTestSequence = new GameTestSequence(this);
		this.sequences.add(gameTestSequence);
		return gameTestSequence;
	}

	public boolean isRequired() {
		return this.test.value().required();
	}

	public boolean isOptional() {
		return !this.test.value().required();
	}

	public Identifier getStructure() {
		return this.test.value().structure();
	}

	public Rotation getRotation() {
		return this.test.value().info().rotation().getRotated(this.extraRotation);
	}

	public GameTestInstance getTest() {
		return this.test.value();
	}

	public Holder.Reference<GameTestInstance> getTestHolder() {
		return this.test;
	}

	public int getTimeoutTicks() {
		return this.timeoutTicks;
	}

	public boolean isFlaky() {
		return this.test.value().maxAttempts() > 1;
	}

	public int maxAttempts() {
		return this.test.value().maxAttempts();
	}

	public int requiredSuccesses() {
		return this.test.value().requiredSuccesses();
	}

	public RetryOptions retryOptions() {
		return this.retryOptions;
	}

	public Stream<GameTestListener> getListeners() {
		return this.listeners.stream();
	}

	public GameTestInfo copyReset() {
		GameTestInfo gameTestInfo = new GameTestInfo(this.test, this.extraRotation, this.level, this.retryOptions());
		if (this.testBlockPos != null) {
			gameTestInfo.setTestBlockPos(this.testBlockPos);
		}

		return gameTestInfo;
	}
}
