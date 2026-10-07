package net.minecraft.world.level.pathfinder;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import org.jspecify.annotations.Nullable;

public class PathFinder {
	private static final float FUDGING = 1.5F;
	private final Node[] neighbors = new Node[32];
	private int maxVisitedNodes;
	private final NodeEvaluator nodeEvaluator;
	private final BinaryHeap openSet = new BinaryHeap();
	private BooleanSupplier captureDebug = () -> false;

	public PathFinder(NodeEvaluator nodeEvaluator, int i) {
		this.nodeEvaluator = nodeEvaluator;
		this.maxVisitedNodes = i;
	}

	public void setCaptureDebug(BooleanSupplier booleanSupplier) {
		this.captureDebug = booleanSupplier;
	}

	public void setMaxVisitedNodes(int i) {
		this.maxVisitedNodes = i;
	}

	public @Nullable Path findPath(PathNavigationRegion pathNavigationRegion, Mob mob, Set<BlockPos> set, float f, int i, float g) {
		this.openSet.clear();
		this.nodeEvaluator.prepare(pathNavigationRegion, mob);
		Node node = this.nodeEvaluator.getStart();
		if (node == null) {
			return null;
		}

		Map<Target, BlockPos> map = set.stream()
			.collect(Collectors.toMap(blockPos -> this.nodeEvaluator.getTarget(blockPos.getX(), blockPos.getY(), blockPos.getZ()), Function.identity()));
		Path path = this.findPath(node, map, f, i, g);
		this.nodeEvaluator.done();
		return path;
	}

	private @Nullable Path findPath(Node node, Map<Target, BlockPos> map, float f, int i, float g) {
		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("find_path");
		profilerFiller.markForCharting(MetricCategory.PATH_FINDING);
		Set<Target> set = map.keySet();
		node.g = 0.0F;
		node.h = this.getBestH(node, set);
		node.f = node.h;
		this.openSet.clear();
		this.openSet.insert(node);
		boolean bl = this.captureDebug.getAsBoolean();
		Set<Node> set2 = bl ? new HashSet<>() : Set.of();
		int j = 0;
		Set<Target> set3 = Sets.newHashSetWithExpectedSize(set.size());
		int k = (int)(this.maxVisitedNodes * g);

		while (!this.openSet.isEmpty()) {
			if (++j >= k) {
				break;
			}

			Node node2 = this.openSet.pop();
			node2.closed = true;

			for (Target target : set) {
				if (node2.distanceManhattan(target) <= i) {
					target.setReached();
					set3.add(target);
				}
			}

			if (!set3.isEmpty()) {
				break;
			}

			if (bl) {
				set2.add(node2);
			}

			if (!(node2.distanceTo(node) >= f)) {
				int l = this.nodeEvaluator.getNeighbors(this.neighbors, node2);

				for (int m = 0; m < l; m++) {
					Node node3 = this.neighbors[m];
					float h = this.distance(node2, node3);
					node3.walkedDistance = node2.walkedDistance + h;
					float n = node2.g + h + node3.costMalus;
					if (node3.walkedDistance < f && (!node3.inOpenSet() || n < node3.g)) {
						node3.cameFrom = node2;
						node3.g = n;
						node3.h = this.getBestH(node3, set) * 1.5F;
						if (node3.inOpenSet()) {
							this.openSet.changeCost(node3, node3.g + node3.h);
						} else {
							node3.f = node3.g + node3.h;
							this.openSet.insert(node3);
						}
					}
				}
			}
		}

		Optional<Path> optional = !set3.isEmpty()
			? set3.stream().map(target -> this.reconstructPath(target.getBestNode(), map.get(target), true)).min(Comparator.comparingInt(Path::getNodeCount))
			: set.stream()
				.map(target -> this.reconstructPath(target.getBestNode(), map.get(target), false))
				.min(Comparator.comparingDouble(Path::getDistToTarget).thenComparingInt(Path::getNodeCount));
		profilerFiller.pop();
		if (optional.isEmpty()) {
			return null;
		}

		Path path = optional.get();
		if (bl) {
			path.setDebug(this.openSet.getHeap(), set2.toArray(Node[]::new), set);
		}

		return path;
	}

	protected float distance(Node node, Node node2) {
		return node.distanceTo(node2);
	}

	private float getBestH(Node node, Set<Target> set) {
		float f = Float.MAX_VALUE;

		for (Target target : set) {
			float g = node.distanceTo(target);
			target.updateBest(g, node);
			f = Math.min(g, f);
		}

		return f;
	}

	private Path reconstructPath(Node node, BlockPos blockPos, boolean bl) {
		List<Node> list = Lists.newArrayList();
		Node node2 = node;
		list.add(0, node2);

		while (node2.cameFrom != null) {
			node2 = node2.cameFrom;
			list.add(0, node2);
		}

		return new Path(list, blockPos, bl);
	}
}
