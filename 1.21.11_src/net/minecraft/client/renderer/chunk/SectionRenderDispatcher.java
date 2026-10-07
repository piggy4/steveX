package net.minecraft.client.renderer.chunk;

import com.google.common.collect.Queues;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.TracingExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.SectionBufferBuilderPool;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.Zone;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class SectionRenderDispatcher {
	private final CompileTaskDynamicQueue compileQueue = new CompileTaskDynamicQueue();
	private final Queue<Runnable> toUpload = Queues.newConcurrentLinkedQueue();
	final Executor mainThreadUploadExecutor = this.toUpload::add;
	final Queue<SectionMesh> toClose = Queues.newConcurrentLinkedQueue();
	final SectionBufferBuilderPack fixedBuffers;
	private final SectionBufferBuilderPool bufferPool;
	volatile boolean closed;
	private final ConsecutiveExecutor consecutiveExecutor;
	private final TracingExecutor executor;
	ClientLevel level;
	final LevelRenderer renderer;
	Vec3 cameraPosition = Vec3.ZERO;
	final SectionCompiler sectionCompiler;

	public SectionRenderDispatcher(
		ClientLevel clientLevel,
		LevelRenderer levelRenderer,
		TracingExecutor tracingExecutor,
		RenderBuffers renderBuffers,
		BlockRenderDispatcher blockRenderDispatcher,
		BlockEntityRenderDispatcher blockEntityRenderDispatcher
	) {
		this.level = clientLevel;
		this.renderer = levelRenderer;
		this.fixedBuffers = renderBuffers.fixedBufferPack();
		this.bufferPool = renderBuffers.sectionBufferPool();
		this.executor = tracingExecutor;
		this.consecutiveExecutor = new ConsecutiveExecutor(tracingExecutor, "Section Renderer");
		this.consecutiveExecutor.schedule(this::runTask);
		this.sectionCompiler = new SectionCompiler(blockRenderDispatcher, blockEntityRenderDispatcher);
	}

	public void setLevel(ClientLevel clientLevel) {
		this.level = clientLevel;
	}

	private void runTask() {
		if (!this.closed && !this.bufferPool.isEmpty()) {
			SectionRenderDispatcher.RenderSection.CompileTask compileTask = this.compileQueue.poll(this.cameraPosition);
			if (compileTask != null) {
				SectionBufferBuilderPack sectionBufferBuilderPack = Objects.requireNonNull(this.bufferPool.acquire());
				CompletableFuture.<CompletableFuture<SectionRenderDispatcher.SectionTaskResult>>supplyAsync(
						() -> compileTask.doTask(sectionBufferBuilderPack), this.executor.forName(compileTask.name())
					)
					.thenCompose(completableFuture -> (CompletionStage<SectionRenderDispatcher.SectionTaskResult>)completableFuture)
					.whenComplete((sectionTaskResult, throwable) -> {
						if (throwable != null) {
							Minecraft.getInstance().delayCrash(CrashReport.forThrowable(throwable, "Batching sections"));
						} else {
							compileTask.isCompleted.set(true);
							this.consecutiveExecutor.schedule(() -> {
								if (sectionTaskResult == SectionRenderDispatcher.SectionTaskResult.SUCCESSFUL) {
									sectionBufferBuilderPack.clearAll();
								} else {
									sectionBufferBuilderPack.discardAll();
								}

								this.bufferPool.release(sectionBufferBuilderPack);
								this.runTask();
							});
						}
					});
			}
		}
	}

	public void setCameraPosition(Vec3 vec3) {
		this.cameraPosition = vec3;
	}

	public void uploadAllPendingUploads() {
		Runnable runnable;
		while ((runnable = this.toUpload.poll()) != null) {
			runnable.run();
		}

		SectionMesh sectionMesh;
		while ((sectionMesh = this.toClose.poll()) != null) {
			sectionMesh.close();
		}
	}

	public void rebuildSectionSync(SectionRenderDispatcher.RenderSection renderSection, RenderRegionCache renderRegionCache) {
		renderSection.compileSync(renderRegionCache);
	}

	public void schedule(SectionRenderDispatcher.RenderSection.CompileTask compileTask) {
		if (!this.closed) {
			this.consecutiveExecutor.schedule(() -> {
				if (!this.closed) {
					this.compileQueue.add(compileTask);
					this.runTask();
				}
			});
		}
	}

	public void clearCompileQueue() {
		this.compileQueue.clear();
	}

	public boolean isQueueEmpty() {
		return this.compileQueue.size() == 0 && this.toUpload.isEmpty();
	}

	public void dispose() {
		this.closed = true;
		this.clearCompileQueue();
		this.uploadAllPendingUploads();
	}

	@VisibleForDebug
	public String getStats() {
		return String.format(Locale.ROOT, "pC: %03d, pU: %02d, aB: %02d", this.compileQueue.size(), this.toUpload.size(), this.bufferPool.getFreeBufferCount());
	}

	@VisibleForDebug
	public int getCompileQueueSize() {
		return this.compileQueue.size();
	}

	@VisibleForDebug
	public int getToUpload() {
		return this.toUpload.size();
	}

	@VisibleForDebug
	public int getFreeBufferCount() {
		return this.bufferPool.getFreeBufferCount();
	}

	@Environment(EnvType.CLIENT)
	public class RenderSection {
		public static final int SIZE = 16;
		public final int index;
		public final AtomicReference<SectionMesh> sectionMesh = new AtomicReference<>(CompiledSectionMesh.UNCOMPILED);
		private SectionRenderDispatcher.RenderSection.@Nullable RebuildTask lastRebuildTask;
		private SectionRenderDispatcher.RenderSection.@Nullable ResortTransparencyTask lastResortTransparencyTask;
		private AABB bb;
		private boolean dirty = true;
		volatile long sectionNode = SectionPos.asLong(-1, -1, -1);
		final BlockPos.MutableBlockPos renderOrigin = new BlockPos.MutableBlockPos(-1, -1, -1);
		private boolean playerChanged;
		private long uploadedTime;
		private long fadeDuration;
		private boolean wasPreviouslyEmpty;

		public RenderSection(final int i, final long l) {
			this.index = i;
			this.setSectionNode(l);
		}

		public float getVisibility(long l) {
			long m = l - this.uploadedTime;
			return m >= this.fadeDuration ? 1.0F : (float)m / (float)this.fadeDuration;
		}

		public void setFadeDuration(long l) {
			this.fadeDuration = l;
		}

		public void setWasPreviouslyEmpty(boolean bl) {
			this.wasPreviouslyEmpty = bl;
		}

		public boolean wasPreviouslyEmpty() {
			return this.wasPreviouslyEmpty;
		}

		private boolean doesChunkExistAt(long l) {
			ChunkAccess chunkAccess = SectionRenderDispatcher.this.level.getChunk(SectionPos.x(l), SectionPos.z(l), ChunkStatus.FULL, false);
			return chunkAccess != null && SectionRenderDispatcher.this.level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(l));
		}

		public boolean hasAllNeighbors() {
			return this.doesChunkExistAt(SectionPos.offset(this.sectionNode, Direction.WEST))
				&& this.doesChunkExistAt(SectionPos.offset(this.sectionNode, Direction.NORTH))
				&& this.doesChunkExistAt(SectionPos.offset(this.sectionNode, Direction.EAST))
				&& this.doesChunkExistAt(SectionPos.offset(this.sectionNode, Direction.SOUTH))
				&& this.doesChunkExistAt(SectionPos.offset(this.sectionNode, -1, 0, -1))
				&& this.doesChunkExistAt(SectionPos.offset(this.sectionNode, -1, 0, 1))
				&& this.doesChunkExistAt(SectionPos.offset(this.sectionNode, 1, 0, -1))
				&& this.doesChunkExistAt(SectionPos.offset(this.sectionNode, 1, 0, 1));
		}

		public AABB getBoundingBox() {
			return this.bb;
		}

		public CompletableFuture<Void> upload(Map<ChunkSectionLayer, MeshData> map, CompiledSectionMesh compiledSectionMesh) {
			if (SectionRenderDispatcher.this.closed) {
				map.values().forEach(MeshData::close);
				return CompletableFuture.completedFuture(null);
			} else {
				return CompletableFuture.runAsync(() -> map.forEach((chunkSectionLayer, meshData) -> {
					try (Zone zone = Profiler.get().zone("Upload Section Layer")) {
						compiledSectionMesh.uploadMeshLayer(chunkSectionLayer, meshData, this.sectionNode);
						meshData.close();
					}

					if (this.uploadedTime == 0L) {
						this.uploadedTime = Util.getMillis();
					}
				}), SectionRenderDispatcher.this.mainThreadUploadExecutor);
			}
		}

		public CompletableFuture<Void> uploadSectionIndexBuffer(
			CompiledSectionMesh compiledSectionMesh, ByteBufferBuilder.Result result, ChunkSectionLayer chunkSectionLayer
		) {
			if (SectionRenderDispatcher.this.closed) {
				result.close();
				return CompletableFuture.completedFuture(null);
			} else {
				return CompletableFuture.runAsync(() -> {
					try (Zone zone = Profiler.get().zone("Upload Section Indices")) {
						compiledSectionMesh.uploadLayerIndexBuffer(chunkSectionLayer, result, this.sectionNode);
						result.close();
					}
				}, SectionRenderDispatcher.this.mainThreadUploadExecutor);
			}
		}

		public void setSectionNode(long l) {
			this.reset();
			this.sectionNode = l;
			int i = SectionPos.sectionToBlockCoord(SectionPos.x(l));
			int j = SectionPos.sectionToBlockCoord(SectionPos.y(l));
			int k = SectionPos.sectionToBlockCoord(SectionPos.z(l));
			this.renderOrigin.set(i, j, k);
			this.bb = new AABB(i, j, k, i + 16, j + 16, k + 16);
		}

		public SectionMesh getSectionMesh() {
			return this.sectionMesh.get();
		}

		public void reset() {
			this.cancelTasks();
			this.sectionMesh.getAndSet(CompiledSectionMesh.UNCOMPILED).close();
			this.dirty = true;
			this.uploadedTime = 0L;
			this.wasPreviouslyEmpty = false;
		}

		public BlockPos getRenderOrigin() {
			return this.renderOrigin;
		}

		public long getSectionNode() {
			return this.sectionNode;
		}

		public void setDirty(boolean bl) {
			boolean bl2 = this.dirty;
			this.dirty = true;
			this.playerChanged = bl | (bl2 && this.playerChanged);
		}

		public void setNotDirty() {
			this.dirty = false;
			this.playerChanged = false;
		}

		public boolean isDirty() {
			return this.dirty;
		}

		public boolean isDirtyFromPlayer() {
			return this.dirty && this.playerChanged;
		}

		public long getNeighborSectionNode(Direction direction) {
			return SectionPos.offset(this.sectionNode, direction);
		}

		public void resortTransparency(SectionRenderDispatcher sectionRenderDispatcher) {
			if (this.getSectionMesh() instanceof CompiledSectionMesh compiledSectionMesh) {
				this.lastResortTransparencyTask = new SectionRenderDispatcher.RenderSection.ResortTransparencyTask(compiledSectionMesh);
				sectionRenderDispatcher.schedule(this.lastResortTransparencyTask);
			}
		}

		public boolean hasTranslucentGeometry() {
			return this.getSectionMesh().hasTranslucentGeometry();
		}

		public boolean transparencyResortingScheduled() {
			return this.lastResortTransparencyTask != null && !this.lastResortTransparencyTask.isCompleted.get();
		}

		protected void cancelTasks() {
			if (this.lastRebuildTask != null) {
				this.lastRebuildTask.cancel();
				this.lastRebuildTask = null;
			}

			if (this.lastResortTransparencyTask != null) {
				this.lastResortTransparencyTask.cancel();
				this.lastResortTransparencyTask = null;
			}
		}

		public SectionRenderDispatcher.RenderSection.CompileTask createCompileTask(RenderRegionCache renderRegionCache) {
			this.cancelTasks();
			RenderSectionRegion renderSectionRegion = renderRegionCache.createRegion(SectionRenderDispatcher.this.level, this.sectionNode);
			boolean bl = this.sectionMesh.get() != CompiledSectionMesh.UNCOMPILED;
			this.lastRebuildTask = new SectionRenderDispatcher.RenderSection.RebuildTask(renderSectionRegion, bl);
			return this.lastRebuildTask;
		}

		public void rebuildSectionAsync(RenderRegionCache renderRegionCache) {
			SectionRenderDispatcher.RenderSection.CompileTask compileTask = this.createCompileTask(renderRegionCache);
			SectionRenderDispatcher.this.schedule(compileTask);
		}

		public void compileSync(RenderRegionCache renderRegionCache) {
			SectionRenderDispatcher.RenderSection.CompileTask compileTask = this.createCompileTask(renderRegionCache);
			compileTask.doTask(SectionRenderDispatcher.this.fixedBuffers);
		}

		void setSectionMesh(SectionMesh sectionMesh) {
			SectionMesh sectionMesh2 = this.sectionMesh.getAndSet(sectionMesh);
			SectionRenderDispatcher.this.toClose.add(sectionMesh2);
			SectionRenderDispatcher.this.renderer.addRecentlyCompiledSection(this);
		}

		VertexSorting createVertexSorting(SectionPos sectionPos) {
			Vec3 vec3 = SectionRenderDispatcher.this.cameraPosition;
			return VertexSorting.byDistance((float)(vec3.x - sectionPos.minBlockX()), (float)(vec3.y - sectionPos.minBlockY()), (float)(vec3.z - sectionPos.minBlockZ()));
		}

		@Environment(EnvType.CLIENT)
		public abstract class CompileTask {
			protected final AtomicBoolean isCancelled = new AtomicBoolean(false);
			protected final AtomicBoolean isCompleted = new AtomicBoolean(false);
			protected final boolean isRecompile;

			public CompileTask(final boolean bl) {
				this.isRecompile = bl;
			}

			public abstract CompletableFuture<SectionRenderDispatcher.SectionTaskResult> doTask(SectionBufferBuilderPack sectionBufferBuilderPack);

			public abstract void cancel();

			protected abstract String name();

			public boolean isRecompile() {
				return this.isRecompile;
			}

			public BlockPos getRenderOrigin() {
				return RenderSection.this.renderOrigin;
			}
		}

		@Environment(EnvType.CLIENT)
		class RebuildTask extends SectionRenderDispatcher.RenderSection.CompileTask {
			protected final RenderSectionRegion region;

			public RebuildTask(final RenderSectionRegion renderSectionRegion, final boolean bl) {
				super(bl);
				this.region = renderSectionRegion;
			}

			@Override
			protected String name() {
				return "rend_chk_rebuild";
			}

			@Override
			public CompletableFuture<SectionRenderDispatcher.SectionTaskResult> doTask(SectionBufferBuilderPack sectionBufferBuilderPack) {
				if (this.isCancelled.get()) {
					return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
				}

				long l = RenderSection.this.sectionNode;
				SectionPos sectionPos = SectionPos.of(l);
				if (this.isCancelled.get()) {
					return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
				}

				SectionCompiler.Results results;
				try (Zone zone = Profiler.get().zone("Compile Section")) {
					results = SectionRenderDispatcher.this.sectionCompiler
						.compile(sectionPos, this.region, RenderSection.this.createVertexSorting(sectionPos), sectionBufferBuilderPack);
				}

				TranslucencyPointOfView translucencyPointOfView = TranslucencyPointOfView.of(SectionRenderDispatcher.this.cameraPosition, l);
				if (this.isCancelled.get()) {
					results.release();
					return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
				} else {
					CompiledSectionMesh compiledSectionMesh = new CompiledSectionMesh(translucencyPointOfView, results);
					CompletableFuture<Void> completableFuture = RenderSection.this.upload(results.renderedLayers, compiledSectionMesh);
					return completableFuture.handle((void_, throwable) -> {
						if (throwable != null && !(throwable instanceof CancellationException) && !(throwable instanceof InterruptedException)) {
							Minecraft.getInstance().delayCrash(CrashReport.forThrowable(throwable, "Rendering section"));
						}

						if (!this.isCancelled.get() && !SectionRenderDispatcher.this.closed) {
							RenderSection.this.setSectionMesh(compiledSectionMesh);
							return SectionRenderDispatcher.SectionTaskResult.SUCCESSFUL;
						} else {
							SectionRenderDispatcher.this.toClose.add(compiledSectionMesh);
							return SectionRenderDispatcher.SectionTaskResult.CANCELLED;
						}
					});
				}
			}

			@Override
			public void cancel() {
				if (this.isCancelled.compareAndSet(false, true)) {
					RenderSection.this.setDirty(false);
				}
			}
		}

		@Environment(EnvType.CLIENT)
		class ResortTransparencyTask extends SectionRenderDispatcher.RenderSection.CompileTask {
			private final CompiledSectionMesh compiledSectionMesh;

			public ResortTransparencyTask(final CompiledSectionMesh compiledSectionMesh) {
				super(true);
				this.compiledSectionMesh = compiledSectionMesh;
			}

			@Override
			protected String name() {
				return "rend_chk_sort";
			}

			@Override
			public CompletableFuture<SectionRenderDispatcher.SectionTaskResult> doTask(SectionBufferBuilderPack sectionBufferBuilderPack) {
				if (this.isCancelled.get()) {
					return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
				}

				MeshData.SortState sortState = this.compiledSectionMesh.getTransparencyState();
				if (sortState != null && !this.compiledSectionMesh.isEmpty(ChunkSectionLayer.TRANSLUCENT)) {
					long l = RenderSection.this.sectionNode;
					VertexSorting vertexSorting = RenderSection.this.createVertexSorting(SectionPos.of(l));
					TranslucencyPointOfView translucencyPointOfView = TranslucencyPointOfView.of(SectionRenderDispatcher.this.cameraPosition, l);
					if (!this.compiledSectionMesh.isDifferentPointOfView(translucencyPointOfView) && !translucencyPointOfView.isAxisAligned()) {
						return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
					} else {
						ByteBufferBuilder.Result result = sortState.buildSortedIndexBuffer(sectionBufferBuilderPack.buffer(ChunkSectionLayer.TRANSLUCENT), vertexSorting);
						if (result == null) {
							return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
						} else if (this.isCancelled.get()) {
							result.close();
							return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
						} else {
							CompletableFuture<Void> completableFuture = RenderSection.this.uploadSectionIndexBuffer(this.compiledSectionMesh, result, ChunkSectionLayer.TRANSLUCENT);
							return completableFuture.handle((void_, throwable) -> {
								if (throwable != null && !(throwable instanceof CancellationException) && !(throwable instanceof InterruptedException)) {
									Minecraft.getInstance().delayCrash(CrashReport.forThrowable(throwable, "Rendering section"));
								}

								if (this.isCancelled.get()) {
									return SectionRenderDispatcher.SectionTaskResult.CANCELLED;
								}

								this.compiledSectionMesh.setTranslucencyPointOfView(translucencyPointOfView);
								return SectionRenderDispatcher.SectionTaskResult.SUCCESSFUL;
							});
						}
					}
				} else {
					return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
				}
			}

			@Override
			public void cancel() {
				this.isCancelled.set(true);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	enum SectionTaskResult {
		SUCCESSFUL,
		CANCELLED;
	}
}
