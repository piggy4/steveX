package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SectionOcclusionGraph {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Direction[] DIRECTIONS = Direction.values();
	private static final int MINIMUM_ADVANCED_CULLING_DISTANCE = 60;
	private static final int MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE = SectionPos.blockToSectionCoord(60);
	private static final double CEILED_SECTION_DIAGONAL = Math.ceil(Math.sqrt(3.0) * 16.0);
	private boolean needsFullUpdate = true;
	private @Nullable Future<?> fullUpdateTask;
	private @Nullable ViewArea viewArea;
	private final AtomicReference<SectionOcclusionGraph.@Nullable GraphState> currentGraph = new AtomicReference<>();
	private final AtomicReference<SectionOcclusionGraph.@Nullable GraphEvents> nextGraphEvents = new AtomicReference<>();
	private final AtomicBoolean needsFrustumUpdate = new AtomicBoolean(false);

	public void waitAndReset(@Nullable ViewArea viewArea) {
		if (this.fullUpdateTask != null) {
			try {
				this.fullUpdateTask.get();
				this.fullUpdateTask = null;
			} catch (Exception exception) {
				LOGGER.warn("Full update failed", exception);
			}
		}

		this.viewArea = viewArea;
		if (viewArea != null) {
			this.currentGraph.set(new SectionOcclusionGraph.GraphState(viewArea));
			this.invalidate();
		} else {
			this.currentGraph.set(null);
		}
	}

	public void invalidate() {
		this.needsFullUpdate = true;
	}

	public void addSectionsInFrustum(Frustum frustum, List<SectionRenderDispatcher.RenderSection> list, List<SectionRenderDispatcher.RenderSection> list2) {
		this.currentGraph.get().storage().sectionTree.visitNodes((node, bl, i, bl2) -> {
			SectionRenderDispatcher.RenderSection renderSection = node.getSection();
			if (renderSection != null) {
				list.add(renderSection);
				if (bl2) {
					list2.add(renderSection);
				}
			}
		}, frustum, 32);
	}

	public boolean consumeFrustumUpdate() {
		return this.needsFrustumUpdate.compareAndSet(true, false);
	}

	public void onChunkReadyToRender(ChunkPos chunkPos) {
		SectionOcclusionGraph.GraphEvents graphEvents = this.nextGraphEvents.get();
		if (graphEvents != null) {
			this.addNeighbors(graphEvents, chunkPos);
		}

		SectionOcclusionGraph.GraphEvents graphEvents2 = this.currentGraph.get().events;
		if (graphEvents2 != graphEvents) {
			this.addNeighbors(graphEvents2, chunkPos);
		}
	}

	public void schedulePropagationFrom(SectionRenderDispatcher.RenderSection renderSection) {
		SectionOcclusionGraph.GraphEvents graphEvents = this.nextGraphEvents.get();
		if (graphEvents != null) {
			graphEvents.sectionsToPropagateFrom.add(renderSection);
		}

		SectionOcclusionGraph.GraphEvents graphEvents2 = this.currentGraph.get().events;
		if (graphEvents2 != graphEvents) {
			graphEvents2.sectionsToPropagateFrom.add(renderSection);
		}
	}

	public void update(boolean bl, Camera camera, Frustum frustum, List<SectionRenderDispatcher.RenderSection> list, LongOpenHashSet longOpenHashSet) {
		Vec3 vec3 = camera.position();
		if (this.needsFullUpdate && (this.fullUpdateTask == null || this.fullUpdateTask.isDone())) {
			this.scheduleFullUpdate(bl, camera, vec3, longOpenHashSet);
		}

		this.runPartialUpdate(bl, frustum, list, vec3, longOpenHashSet);
	}

	private void scheduleFullUpdate(boolean bl, Camera camera, Vec3 vec3, LongOpenHashSet longOpenHashSet) {
		this.needsFullUpdate = false;
		LongOpenHashSet longOpenHashSet2 = longOpenHashSet.clone();
		this.fullUpdateTask = CompletableFuture.runAsync(() -> {
			SectionOcclusionGraph.GraphState graphState = new SectionOcclusionGraph.GraphState(this.viewArea);
			this.nextGraphEvents.set(graphState.events);
			Queue<SectionOcclusionGraph.Node> queue = Queues.newArrayDeque();
			this.initializeQueueForFullUpdate(camera, queue);
			queue.forEach(node -> graphState.storage.sectionToNodeMap.put(node.section, node));
			this.runUpdates(graphState.storage, vec3, queue, bl, renderSection -> {}, longOpenHashSet2);
			this.currentGraph.set(graphState);
			this.nextGraphEvents.set(null);
			this.needsFrustumUpdate.set(true);
		}, Util.backgroundExecutor());
	}

	private void runPartialUpdate(boolean bl, Frustum frustum, List<SectionRenderDispatcher.RenderSection> list, Vec3 vec3, LongOpenHashSet longOpenHashSet) {
		SectionOcclusionGraph.GraphState graphState = this.currentGraph.get();
		this.queueSectionsWithNewNeighbors(graphState);
		if (!graphState.events.sectionsToPropagateFrom.isEmpty()) {
			Queue<SectionOcclusionGraph.Node> queue = Queues.newArrayDeque();

			while (!graphState.events.sectionsToPropagateFrom.isEmpty()) {
				SectionRenderDispatcher.RenderSection renderSection = graphState.events.sectionsToPropagateFrom.poll();
				SectionOcclusionGraph.Node node = graphState.storage.sectionToNodeMap.get(renderSection);
				if (node != null && node.section == renderSection) {
					queue.add(node);
				}
			}

			Frustum frustum2 = LevelRenderer.offsetFrustum(frustum);
			Consumer<SectionRenderDispatcher.RenderSection> consumer = renderSection -> {
				if (frustum2.isVisible(renderSection.getBoundingBox())) {
					this.needsFrustumUpdate.set(true);
				}
			};
			this.runUpdates(graphState.storage, vec3, queue, bl, consumer, longOpenHashSet);
		}
	}

	private void queueSectionsWithNewNeighbors(SectionOcclusionGraph.GraphState graphState) {
		LongIterator longIterator = graphState.events.chunksWhichReceivedNeighbors.iterator();

		while (longIterator.hasNext()) {
			long l = longIterator.nextLong();
			List<SectionRenderDispatcher.RenderSection> list = graphState.storage.chunksWaitingForNeighbors.get(l);
			if (list != null && list.get(0).hasAllNeighbors()) {
				graphState.events.sectionsToPropagateFrom.addAll(list);
				graphState.storage.chunksWaitingForNeighbors.remove(l);
			}
		}

		graphState.events.chunksWhichReceivedNeighbors.clear();
	}

	private void addNeighbors(SectionOcclusionGraph.GraphEvents graphEvents, ChunkPos chunkPos) {
		graphEvents.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(chunkPos.x - 1, chunkPos.z));
		graphEvents.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(chunkPos.x, chunkPos.z - 1));
		graphEvents.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(chunkPos.x + 1, chunkPos.z));
		graphEvents.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(chunkPos.x, chunkPos.z + 1));
		graphEvents.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(chunkPos.x - 1, chunkPos.z - 1));
		graphEvents.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(chunkPos.x - 1, chunkPos.z + 1));
		graphEvents.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(chunkPos.x + 1, chunkPos.z - 1));
		graphEvents.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(chunkPos.x + 1, chunkPos.z + 1));
	}

	private void initializeQueueForFullUpdate(Camera camera, Queue<SectionOcclusionGraph.Node> queue) {
		BlockPos blockPos = camera.blockPosition();
		long l = SectionPos.asLong(blockPos);
		int i = SectionPos.y(l);
		SectionRenderDispatcher.RenderSection renderSection = this.viewArea.getRenderSection(l);
		if (renderSection == null) {
			LevelHeightAccessor levelHeightAccessor = this.viewArea.getLevelHeightAccessor();
			boolean bl = i < levelHeightAccessor.getMinSectionY();
			int j = bl ? levelHeightAccessor.getMinSectionY() : levelHeightAccessor.getMaxSectionY();
			int k = this.viewArea.getViewDistance();
			List<SectionOcclusionGraph.Node> list = Lists.newArrayList();
			int m = SectionPos.x(l);
			int n = SectionPos.z(l);

			for (int o = -k; o <= k; o++) {
				for (int p = -k; p <= k; p++) {
					SectionRenderDispatcher.RenderSection renderSection2 = this.viewArea.getRenderSection(SectionPos.asLong(o + m, j, p + n));
					if (renderSection2 != null && this.isInViewDistance(l, renderSection2.getSectionNode())) {
						Direction direction = bl ? Direction.UP : Direction.DOWN;
						SectionOcclusionGraph.Node node = new SectionOcclusionGraph.Node(renderSection2, direction, 0);
						node.setDirections(node.directions, direction);
						if (o > 0) {
							node.setDirections(node.directions, Direction.EAST);
						} else if (o < 0) {
							node.setDirections(node.directions, Direction.WEST);
						}

						if (p > 0) {
							node.setDirections(node.directions, Direction.SOUTH);
						} else if (p < 0) {
							node.setDirections(node.directions, Direction.NORTH);
						}

						list.add(node);
					}
				}
			}

			list.sort(Comparator.comparingDouble(nodex -> blockPos.distSqr(SectionPos.of(nodex.section.getSectionNode()).center())));
			queue.addAll(list);
		} else {
			queue.add(new SectionOcclusionGraph.Node(renderSection, null, 0));
		}
	}

	private void runUpdates(
		SectionOcclusionGraph.GraphStorage graphStorage,
		Vec3 vec3,
		Queue<SectionOcclusionGraph.Node> queue,
		boolean bl,
		Consumer<SectionRenderDispatcher.RenderSection> consumer,
		LongOpenHashSet longOpenHashSet
	) {
		SectionPos sectionPos = SectionPos.of(vec3);
		long l = sectionPos.asLong();
		BlockPos blockPos = sectionPos.center();

		while (!queue.isEmpty()) {
			SectionOcclusionGraph.Node node = queue.poll();
			SectionRenderDispatcher.RenderSection renderSection = node.section;
			if (!longOpenHashSet.contains(node.section.getSectionNode())) {
				if (graphStorage.sectionTree.add(node.section)) {
					consumer.accept(node.section);
				}
			} else {
				node.section.sectionMesh.compareAndSet(CompiledSectionMesh.UNCOMPILED, CompiledSectionMesh.EMPTY);
			}

			long m = renderSection.getSectionNode();
			boolean bl2 = Math.abs(SectionPos.x(m) - sectionPos.x()) > MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE
				|| Math.abs(SectionPos.y(m) - sectionPos.y()) > MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE
				|| Math.abs(SectionPos.z(m) - sectionPos.z()) > MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE;

			for (Direction direction : DIRECTIONS) {
				SectionRenderDispatcher.RenderSection renderSection2 = this.getRelativeFrom(l, renderSection, direction);
				if (renderSection2 != null && (!bl || !node.hasDirection(direction.getOpposite()))) {
					if (bl && node.hasSourceDirections()) {
						SectionMesh sectionMesh = renderSection.getSectionMesh();
						boolean bl3 = false;

						for (int i = 0; i < DIRECTIONS.length; i++) {
							if (node.hasSourceDirection(i) && sectionMesh.facesCanSeeEachother(DIRECTIONS[i].getOpposite(), direction)) {
								bl3 = true;
								break;
							}
						}

						if (!bl3) {
							continue;
						}
					}

					if (bl && bl2) {
						int j = SectionPos.sectionToBlockCoord(SectionPos.x(m));
						int k = SectionPos.sectionToBlockCoord(SectionPos.y(m));
						int i = SectionPos.sectionToBlockCoord(SectionPos.z(m));
						boolean bl4 = direction.getAxis() == Direction.Axis.X ? blockPos.getX() > j : blockPos.getX() < j;
						boolean bl5 = direction.getAxis() == Direction.Axis.Y ? blockPos.getY() > k : blockPos.getY() < k;
						boolean bl6 = direction.getAxis() == Direction.Axis.Z ? blockPos.getZ() > i : blockPos.getZ() < i;
						Vector3d vector3d = new Vector3d(j + (bl4 ? 16 : 0), k + (bl5 ? 16 : 0), i + (bl6 ? 16 : 0));
						Vector3d vector3d2 = new Vector3d(vec3.x, vec3.y, vec3.z).sub(vector3d).normalize().mul(CEILED_SECTION_DIAGONAL);
						boolean bl7 = true;

						while (vector3d.distanceSquared(vec3.x, vec3.y, vec3.z) > 3600.0) {
							vector3d.add(vector3d2);
							LevelHeightAccessor levelHeightAccessor = this.viewArea.getLevelHeightAccessor();
							if (vector3d.y > levelHeightAccessor.getMaxY() || vector3d.y < levelHeightAccessor.getMinY()) {
								break;
							}

							SectionRenderDispatcher.RenderSection renderSection3 = this.viewArea.getRenderSectionAt(BlockPos.containing(vector3d.x, vector3d.y, vector3d.z));
							if (renderSection3 == null || graphStorage.sectionToNodeMap.get(renderSection3) == null) {
								bl7 = false;
								break;
							}
						}

						if (!bl7) {
							continue;
						}
					}

					SectionOcclusionGraph.Node node2 = graphStorage.sectionToNodeMap.get(renderSection2);
					if (node2 != null) {
						node2.addSourceDirection(direction);
					} else {
						SectionOcclusionGraph.Node node3 = new SectionOcclusionGraph.Node(renderSection2, direction, node.step + 1);
						node3.setDirections(node.directions, direction);
						if (renderSection2.hasAllNeighbors()) {
							queue.add(node3);
							graphStorage.sectionToNodeMap.put(renderSection2, node3);
						} else if (this.isInViewDistance(l, renderSection2.getSectionNode())) {
							graphStorage.sectionToNodeMap.put(renderSection2, node3);
							long n = SectionPos.sectionToChunk(renderSection2.getSectionNode());
							graphStorage.chunksWaitingForNeighbors.computeIfAbsent(n, lx -> new ArrayList<>()).add(renderSection2);
						}
					}
				}
			}
		}
	}

	private boolean isInViewDistance(long l, long m) {
		return ChunkTrackingView.isInViewDistance(SectionPos.x(l), SectionPos.z(l), this.viewArea.getViewDistance(), SectionPos.x(m), SectionPos.z(m));
	}

	private SectionRenderDispatcher.@Nullable RenderSection getRelativeFrom(long l, SectionRenderDispatcher.RenderSection renderSection, Direction direction) {
		long m = renderSection.getNeighborSectionNode(direction);
		if (!this.isInViewDistance(l, m)) {
			return null;
		} else {
			return Mth.abs(SectionPos.y(l) - SectionPos.y(m)) > this.viewArea.getViewDistance() ? null : this.viewArea.getRenderSection(m);
		}
	}

	@VisibleForDebug
	public SectionOcclusionGraph.@Nullable Node getNode(SectionRenderDispatcher.RenderSection renderSection) {
		return this.currentGraph.get().storage.sectionToNodeMap.get(renderSection);
	}

	public Octree getOctree() {
		return this.currentGraph.get().storage.sectionTree;
	}

	@Environment(EnvType.CLIENT)
	record GraphEvents(LongSet chunksWhichReceivedNeighbors, BlockingQueue<SectionRenderDispatcher.RenderSection> sectionsToPropagateFrom) {

		GraphEvents() {
			this(new LongOpenHashSet(), new LinkedBlockingQueue<>());
		}
	}

	@Environment(EnvType.CLIENT)
	record GraphState(SectionOcclusionGraph.GraphStorage storage, SectionOcclusionGraph.GraphEvents events) {

		GraphState(ViewArea viewArea) {
			this(new SectionOcclusionGraph.GraphStorage(viewArea), new SectionOcclusionGraph.GraphEvents());
		}
	}

	@Environment(EnvType.CLIENT)
	static class GraphStorage {
		public final SectionOcclusionGraph.SectionToNodeMap sectionToNodeMap;
		public final Octree sectionTree;
		public final Long2ObjectMap<List<SectionRenderDispatcher.RenderSection>> chunksWaitingForNeighbors;

		public GraphStorage(ViewArea viewArea) {
			this.sectionToNodeMap = new SectionOcclusionGraph.SectionToNodeMap(viewArea.sections.length);
			this.sectionTree = new Octree(viewArea.getCameraSectionPos(), viewArea.getViewDistance(), viewArea.sectionGridSizeY, viewArea.level.getMinY());
			this.chunksWaitingForNeighbors = new Long2ObjectOpenHashMap<>();
		}
	}

	@Environment(EnvType.CLIENT)
	@VisibleForDebug
	public static class Node {
		@VisibleForDebug
		protected final SectionRenderDispatcher.RenderSection section;
		private byte sourceDirections;
		byte directions;
		@VisibleForDebug
		public final int step;

		Node(SectionRenderDispatcher.RenderSection renderSection, @Nullable Direction direction, int i) {
			this.section = renderSection;
			if (direction != null) {
				this.addSourceDirection(direction);
			}

			this.step = i;
		}

		void setDirections(byte b, Direction direction) {
			this.directions = (byte)(this.directions | b | 1 << direction.ordinal());
		}

		boolean hasDirection(Direction direction) {
			return (this.directions & 1 << direction.ordinal()) > 0;
		}

		void addSourceDirection(Direction direction) {
			this.sourceDirections = (byte)(this.sourceDirections | this.sourceDirections | 1 << direction.ordinal());
		}

		@VisibleForDebug
		public boolean hasSourceDirection(int i) {
			return (this.sourceDirections & 1 << i) > 0;
		}

		boolean hasSourceDirections() {
			return this.sourceDirections != 0;
		}

		@Override
		public int hashCode() {
			return Long.hashCode(this.section.getSectionNode());
		}

		@Override
		public boolean equals(Object object) {
			return !(object instanceof SectionOcclusionGraph.Node node) ? false : this.section.getSectionNode() == node.section.getSectionNode();
		}
	}

	@Environment(EnvType.CLIENT)
	static class SectionToNodeMap {
		private final SectionOcclusionGraph.Node[] nodes;

		SectionToNodeMap(int i) {
			this.nodes = new SectionOcclusionGraph.Node[i];
		}

		public void put(SectionRenderDispatcher.RenderSection renderSection, SectionOcclusionGraph.Node node) {
			this.nodes[renderSection.index] = node;
		}

		public SectionOcclusionGraph.@Nullable Node get(SectionRenderDispatcher.RenderSection renderSection) {
			int i = renderSection.index;
			return i >= 0 && i < this.nodes.length ? this.nodes[i] : null;
		}
	}
}
