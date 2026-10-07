package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.jspecify.annotations.Nullable;

public class Beardifier implements DensityFunctions.BeardifierOrMarker {
	public static final int BEARD_KERNEL_RADIUS = 12;
	private static final int BEARD_KERNEL_SIZE = 24;
	private static final float[] BEARD_KERNEL = Util.make(new float[13824], fs -> {
		for (int i = 0; i < 24; i++) {
			for (int j = 0; j < 24; j++) {
				for (int k = 0; k < 24; k++) {
					fs[i * 24 * 24 + j * 24 + k] = (float)computeBeardContribution(j - 12, k - 12, i - 12);
				}
			}
		}
	});
	public static final Beardifier EMPTY = new Beardifier(List.of(), List.of(), null);
	private final List<Beardifier.Rigid> pieces;
	private final List<JigsawJunction> junctions;
	private final @Nullable BoundingBox affectedBox;

	public static Beardifier forStructuresInChunk(StructureManager structureManager, ChunkPos chunkPos) {
		List<StructureStart> list = structureManager.startsForStructure(chunkPos, structure -> structure.terrainAdaptation() != TerrainAdjustment.NONE);
		if (list.isEmpty()) {
			return EMPTY;
		}

		int i = chunkPos.getMinBlockX();
		int j = chunkPos.getMinBlockZ();
		List<Beardifier.Rigid> list2 = new ArrayList<>();
		List<JigsawJunction> list3 = new ArrayList<>();
		BoundingBox boundingBox = null;

		for (StructureStart structureStart : list) {
			TerrainAdjustment terrainAdjustment = structureStart.getStructure().terrainAdaptation();

			for (StructurePiece structurePiece : structureStart.getPieces()) {
				if (structurePiece.isCloseToChunk(chunkPos, 12)) {
					if (structurePiece instanceof PoolElementStructurePiece poolElementStructurePiece) {
						StructureTemplatePool.Projection projection = poolElementStructurePiece.getElement().getProjection();
						if (projection == StructureTemplatePool.Projection.RIGID) {
							list2.add(new Beardifier.Rigid(poolElementStructurePiece.getBoundingBox(), terrainAdjustment, poolElementStructurePiece.getGroundLevelDelta()));
							boundingBox = includeBoundingBox(boundingBox, structurePiece.getBoundingBox());
						}

						for (JigsawJunction jigsawJunction : poolElementStructurePiece.getJunctions()) {
							int k = jigsawJunction.getSourceX();
							int l = jigsawJunction.getSourceZ();
							if (k > i - 12 && l > j - 12 && k < i + 15 + 12 && l < j + 15 + 12) {
								list3.add(jigsawJunction);
								BoundingBox boundingBox2 = new BoundingBox(new BlockPos(k, jigsawJunction.getSourceGroundY(), l));
								boundingBox = includeBoundingBox(boundingBox, boundingBox2);
							}
						}
					} else {
						list2.add(new Beardifier.Rigid(structurePiece.getBoundingBox(), terrainAdjustment, 0));
						boundingBox = includeBoundingBox(boundingBox, structurePiece.getBoundingBox());
					}
				}
			}
		}

		if (boundingBox == null) {
			return EMPTY;
		}

		BoundingBox boundingBox3 = boundingBox.inflatedBy(24);
		return new Beardifier(List.copyOf(list2), List.copyOf(list3), boundingBox3);
	}

	private static BoundingBox includeBoundingBox(@Nullable BoundingBox boundingBox, BoundingBox boundingBox2) {
		return boundingBox == null ? boundingBox2 : BoundingBox.encapsulating(boundingBox, boundingBox2);
	}

	@VisibleForTesting
	public Beardifier(List<Beardifier.Rigid> list, List<JigsawJunction> list2, @Nullable BoundingBox boundingBox) {
		this.pieces = list;
		this.junctions = list2;
		this.affectedBox = boundingBox;
	}

	@Override
	public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
		if (this.affectedBox == null) {
			Arrays.fill(ds, 0.0);
		} else {
			DensityFunctions.BeardifierOrMarker.super.fillArray(ds, contextProvider);
		}
	}

	@Override
	public double compute(DensityFunction.FunctionContext functionContext) {
		if (this.affectedBox == null) {
			return 0.0;
		}

		int i = functionContext.blockX();
		int j = functionContext.blockY();
		int k = functionContext.blockZ();
		if (!this.affectedBox.isInside(i, j, k)) {
			return 0.0;
		}

		double d = 0.0;

		for (Beardifier.Rigid rigid : this.pieces) {
			BoundingBox boundingBox = rigid.box();
			int l = rigid.groundLevelDelta();
			int m = Math.max(0, Math.max(boundingBox.minX() - i, i - boundingBox.maxX()));
			int n = Math.max(0, Math.max(boundingBox.minZ() - k, k - boundingBox.maxZ()));
			int o = boundingBox.minY() + l;
			int p = j - o;

			int q = switch (rigid.terrainAdjustment()) {
				case NONE -> 0;
				case BURY, BEARD_THIN -> p;
				case BEARD_BOX -> Math.max(0, Math.max(o - j, j - boundingBox.maxY()));
				case ENCAPSULATE -> Math.max(0, Math.max(boundingBox.minY() - j, j - boundingBox.maxY()));
			};

			d += switch (rigid.terrainAdjustment()) {
				case NONE -> 0.0;
				case BURY -> getBuryContribution(m, q / 2.0, n);
				case BEARD_THIN, BEARD_BOX -> getBeardContribution(m, q, n, p) * 0.8;
				case ENCAPSULATE -> getBuryContribution(m / 2.0, q / 2.0, n / 2.0) * 0.8;
			};
		}

		for (JigsawJunction jigsawJunction : this.junctions) {
			int r = i - jigsawJunction.getSourceX();
			int l = j - jigsawJunction.getSourceGroundY();
			int m = k - jigsawJunction.getSourceZ();
			d += getBeardContribution(r, l, m, l) * 0.4;
		}

		return d;
	}

	@Override
	public double minValue() {
		return Double.NEGATIVE_INFINITY;
	}

	@Override
	public double maxValue() {
		return Double.POSITIVE_INFINITY;
	}

	private static double getBuryContribution(double d, double e, double f) {
		double g = Mth.length(d, e, f);
		return Mth.clampedMap(g, 0.0, 6.0, 1.0, 0.0);
	}

	private static double getBeardContribution(int i, int j, int k, int l) {
		int m = i + 12;
		int n = j + 12;
		int o = k + 12;
		if (isInKernelRange(m) && isInKernelRange(n) && isInKernelRange(o)) {
			double d = l + 0.5;
			double e = Mth.lengthSquared(i, d, k);
			double f = -d * Mth.fastInvSqrt(e / 2.0) / 2.0;
			return f * BEARD_KERNEL[o * 24 * 24 + m * 24 + n];
		} else {
			return 0.0;
		}
	}

	private static boolean isInKernelRange(int i) {
		return i >= 0 && i < 24;
	}

	private static double computeBeardContribution(int i, int j, int k) {
		return computeBeardContribution(i, j + 0.5, k);
	}

	private static double computeBeardContribution(int i, double d, int j) {
		double e = Mth.lengthSquared(i, d, j);
		return Math.pow(Math.E, -e / 16.0);
	}

	@VisibleForTesting
	public record Rigid(BoundingBox box, TerrainAdjustment terrainAdjustment, int groundLevelDelta) {
	}
}
