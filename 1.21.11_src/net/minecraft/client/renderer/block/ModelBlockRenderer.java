package net.minecraft.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.render.FabricBlockModelRenderer;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.renderer.v1.render.FabricBlockModelRenderer} injected by mod fabric-renderer-api-v1</p>
 */
@Environment(EnvType.CLIENT)
public class ModelBlockRenderer implements FabricBlockModelRenderer {
	private static final Direction[] DIRECTIONS = Direction.values();
	private final BlockColors blockColors;
	private static final int CACHE_SIZE = 100;
	static final ThreadLocal<ModelBlockRenderer.Cache> CACHE = ThreadLocal.withInitial(ModelBlockRenderer.Cache::new);

	public ModelBlockRenderer(BlockColors blockColors) {
		this.blockColors = blockColors;
	}

	public void tesselateBlock(
		BlockAndTintGetter blockAndTintGetter,
		List<BlockModelPart> list,
		BlockState blockState,
		BlockPos blockPos,
		PoseStack poseStack,
		VertexConsumer vertexConsumer,
		boolean bl,
		int i
	) {
		if (!list.isEmpty()) {
			boolean bl2 = Minecraft.useAmbientOcclusion() && blockState.getLightEmission() == 0 && list.getFirst().useAmbientOcclusion();
			poseStack.translate(blockState.getOffset(blockPos));

			try {
				if (bl2) {
					this.tesselateWithAO(blockAndTintGetter, list, blockState, blockPos, poseStack, vertexConsumer, bl, i);
				} else {
					this.tesselateWithoutAO(blockAndTintGetter, list, blockState, blockPos, poseStack, vertexConsumer, bl, i);
				}
			} catch (Throwable throwable) {
				CrashReport crashReport = CrashReport.forThrowable(throwable, "Tesselating block model");
				CrashReportCategory crashReportCategory = crashReport.addCategory("Block model being tesselated");
				CrashReportCategory.populateBlockDetails(crashReportCategory, blockAndTintGetter, blockPos, blockState);
				crashReportCategory.setDetail("Using AO", bl2);
				throw new ReportedException(crashReport);
			}
		}
	}

	private static boolean shouldRenderFace(BlockAndTintGetter blockAndTintGetter, BlockState blockState, boolean bl, Direction direction, BlockPos blockPos) {
		if (!bl) {
			return true;
		}

		BlockState blockState2 = blockAndTintGetter.getBlockState(blockPos);
		return Block.shouldRenderFace(blockState, blockState2, direction);
	}

	public void tesselateWithAO(
		BlockAndTintGetter blockAndTintGetter,
		List<BlockModelPart> list,
		BlockState blockState,
		BlockPos blockPos,
		PoseStack poseStack,
		VertexConsumer vertexConsumer,
		boolean bl,
		int i
	) {
		ModelBlockRenderer.AmbientOcclusionRenderStorage ambientOcclusionRenderStorage = new ModelBlockRenderer.AmbientOcclusionRenderStorage();
		int j = 0;
		int k = 0;

		for (BlockModelPart blockModelPart : list) {
			for (Direction direction : DIRECTIONS) {
				int l = 1 << direction.ordinal();
				boolean bl2 = (j & l) == 1;
				boolean bl3 = (k & l) == 1;
				if (!bl2 || bl3) {
					List<BakedQuad> list2 = blockModelPart.getQuads(direction);
					if (!list2.isEmpty()) {
						if (!bl2) {
							bl3 = shouldRenderFace(blockAndTintGetter, blockState, bl, direction, ambientOcclusionRenderStorage.scratchPos.setWithOffset(blockPos, direction));
							j |= l;
							if (bl3) {
								k |= l;
							}
						}

						if (bl3) {
							this.renderModelFaceAO(blockAndTintGetter, blockState, blockPos, poseStack, vertexConsumer, list2, ambientOcclusionRenderStorage, i);
						}
					}
				}
			}

			List<BakedQuad> list3 = blockModelPart.getQuads(null);
			if (!list3.isEmpty()) {
				this.renderModelFaceAO(blockAndTintGetter, blockState, blockPos, poseStack, vertexConsumer, list3, ambientOcclusionRenderStorage, i);
			}
		}
	}

	public void tesselateWithoutAO(
		BlockAndTintGetter blockAndTintGetter,
		List<BlockModelPart> list,
		BlockState blockState,
		BlockPos blockPos,
		PoseStack poseStack,
		VertexConsumer vertexConsumer,
		boolean bl,
		int i
	) {
		ModelBlockRenderer.CommonRenderStorage commonRenderStorage = new ModelBlockRenderer.CommonRenderStorage();
		int j = 0;
		int k = 0;

		for (BlockModelPart blockModelPart : list) {
			for (Direction direction : DIRECTIONS) {
				int l = 1 << direction.ordinal();
				boolean bl2 = (j & l) == 1;
				boolean bl3 = (k & l) == 1;
				if (!bl2 || bl3) {
					List<BakedQuad> list2 = blockModelPart.getQuads(direction);
					if (!list2.isEmpty()) {
						BlockPos blockPos2 = commonRenderStorage.scratchPos.setWithOffset(blockPos, direction);
						if (!bl2) {
							bl3 = shouldRenderFace(blockAndTintGetter, blockState, bl, direction, blockPos2);
							j |= l;
							if (bl3) {
								k |= l;
							}
						}

						if (bl3) {
							int m = commonRenderStorage.cache.getLightColor(blockState, blockAndTintGetter, blockPos2);
							this.renderModelFaceFlat(blockAndTintGetter, blockState, blockPos, m, i, false, poseStack, vertexConsumer, list2, commonRenderStorage);
						}
					}
				}
			}

			List<BakedQuad> list3 = blockModelPart.getQuads(null);
			if (!list3.isEmpty()) {
				this.renderModelFaceFlat(blockAndTintGetter, blockState, blockPos, -1, i, true, poseStack, vertexConsumer, list3, commonRenderStorage);
			}
		}
	}

	private void renderModelFaceAO(
		BlockAndTintGetter blockAndTintGetter,
		BlockState blockState,
		BlockPos blockPos,
		PoseStack poseStack,
		VertexConsumer vertexConsumer,
		List<BakedQuad> list,
		ModelBlockRenderer.AmbientOcclusionRenderStorage ambientOcclusionRenderStorage,
		int i
	) {
		for (BakedQuad bakedQuad : list) {
			calculateShape(blockAndTintGetter, blockState, blockPos, bakedQuad, ambientOcclusionRenderStorage);
			ambientOcclusionRenderStorage.calculate(blockAndTintGetter, blockState, blockPos, bakedQuad.direction(), bakedQuad.shade());
			this.putQuadData(blockAndTintGetter, blockState, blockPos, vertexConsumer, poseStack.last(), bakedQuad, ambientOcclusionRenderStorage, i);
		}
	}

	private void putQuadData(
		BlockAndTintGetter blockAndTintGetter,
		BlockState blockState,
		BlockPos blockPos,
		VertexConsumer vertexConsumer,
		PoseStack.Pose pose,
		BakedQuad bakedQuad,
		ModelBlockRenderer.CommonRenderStorage commonRenderStorage,
		int i
	) {
		int j = bakedQuad.tintIndex();
		float f;
		float g;
		float h;
		if (j != -1) {
			int k;
			if (commonRenderStorage.tintCacheIndex == j) {
				k = commonRenderStorage.tintCacheValue;
			} else {
				k = this.blockColors.getColor(blockState, blockAndTintGetter, blockPos, j);
				commonRenderStorage.tintCacheIndex = j;
				commonRenderStorage.tintCacheValue = k;
			}

			f = ARGB.redFloat(k);
			g = ARGB.greenFloat(k);
			h = ARGB.blueFloat(k);
		} else {
			f = 1.0F;
			g = 1.0F;
			h = 1.0F;
		}

		vertexConsumer.putBulkData(pose, bakedQuad, commonRenderStorage.brightness, f, g, h, 1.0F, commonRenderStorage.lightmap, i);
	}

	private static void calculateShape(
		BlockAndTintGetter blockAndTintGetter,
		BlockState blockState,
		BlockPos blockPos,
		BakedQuad bakedQuad,
		ModelBlockRenderer.CommonRenderStorage commonRenderStorage
	) {
		float f = 32.0F;
		float g = 32.0F;
		float h = 32.0F;
		float i = -32.0F;
		float j = -32.0F;
		float k = -32.0F;

		for (int l = 0; l < 4; l++) {
			Vector3fc vector3fc = bakedQuad.position(l);
			float m = vector3fc.x();
			float n = vector3fc.y();
			float o = vector3fc.z();
			f = Math.min(f, m);
			g = Math.min(g, n);
			h = Math.min(h, o);
			i = Math.max(i, m);
			j = Math.max(j, n);
			k = Math.max(k, o);
		}

		if (commonRenderStorage instanceof ModelBlockRenderer.AmbientOcclusionRenderStorage ambientOcclusionRenderStorage) {
			ambientOcclusionRenderStorage.faceShape[ModelBlockRenderer.SizeInfo.WEST.index] = f;
			ambientOcclusionRenderStorage.faceShape[ModelBlockRenderer.SizeInfo.EAST.index] = i;
			ambientOcclusionRenderStorage.faceShape[ModelBlockRenderer.SizeInfo.DOWN.index] = g;
			ambientOcclusionRenderStorage.faceShape[ModelBlockRenderer.SizeInfo.UP.index] = j;
			ambientOcclusionRenderStorage.faceShape[ModelBlockRenderer.SizeInfo.NORTH.index] = h;
			ambientOcclusionRenderStorage.faceShape[ModelBlockRenderer.SizeInfo.SOUTH.index] = k;
			ambientOcclusionRenderStorage.faceShape[ModelBlockRenderer.SizeInfo.FLIP_WEST.index] = 1.0F - f;
			ambientOcclusionRenderStorage.faceShape[ModelBlockRenderer.SizeInfo.FLIP_EAST.index] = 1.0F - i;
			ambientOcclusionRenderStorage.faceShape[ModelBlockRenderer.SizeInfo.FLIP_DOWN.index] = 1.0F - g;
			ambientOcclusionRenderStorage.faceShape[ModelBlockRenderer.SizeInfo.FLIP_UP.index] = 1.0F - j;
			ambientOcclusionRenderStorage.faceShape[ModelBlockRenderer.SizeInfo.FLIP_NORTH.index] = 1.0F - h;
			ambientOcclusionRenderStorage.faceShape[ModelBlockRenderer.SizeInfo.FLIP_SOUTH.index] = 1.0F - k;
		}

		float p = 1.0E-4F;
		float q = 0.9999F;

		commonRenderStorage.facePartial = switch (bakedQuad.direction()) {
			case DOWN, UP -> f >= 1.0E-4F || h >= 1.0E-4F || i <= 0.9999F || k <= 0.9999F;
			case NORTH, SOUTH -> f >= 1.0E-4F || g >= 1.0E-4F || i <= 0.9999F || j <= 0.9999F;
			case WEST, EAST -> g >= 1.0E-4F || h >= 1.0E-4F || j <= 0.9999F || k <= 0.9999F;
		};

		commonRenderStorage.faceCubic = switch (bakedQuad.direction()) {
			case DOWN -> g == j && (g < 1.0E-4F || blockState.isCollisionShapeFullBlock(blockAndTintGetter, blockPos));
			case UP -> g == j && (j > 0.9999F || blockState.isCollisionShapeFullBlock(blockAndTintGetter, blockPos));
			case NORTH -> h == k && (h < 1.0E-4F || blockState.isCollisionShapeFullBlock(blockAndTintGetter, blockPos));
			case SOUTH -> h == k && (k > 0.9999F || blockState.isCollisionShapeFullBlock(blockAndTintGetter, blockPos));
			case WEST -> f == i && (f < 1.0E-4F || blockState.isCollisionShapeFullBlock(blockAndTintGetter, blockPos));
			case EAST -> f == i && (i > 0.9999F || blockState.isCollisionShapeFullBlock(blockAndTintGetter, blockPos));
		};
	}

	private void renderModelFaceFlat(
		BlockAndTintGetter blockAndTintGetter,
		BlockState blockState,
		BlockPos blockPos,
		int i,
		int j,
		boolean bl,
		PoseStack poseStack,
		VertexConsumer vertexConsumer,
		List<BakedQuad> list,
		ModelBlockRenderer.CommonRenderStorage commonRenderStorage
	) {
		for (BakedQuad bakedQuad : list) {
			if (bl) {
				calculateShape(blockAndTintGetter, blockState, blockPos, bakedQuad, commonRenderStorage);
				BlockPos blockPos2 = commonRenderStorage.faceCubic ? commonRenderStorage.scratchPos.setWithOffset(blockPos, bakedQuad.direction()) : blockPos;
				i = commonRenderStorage.cache.getLightColor(blockState, blockAndTintGetter, blockPos2);
			}

			float f = blockAndTintGetter.getShade(bakedQuad.direction(), bakedQuad.shade());
			commonRenderStorage.brightness[0] = f;
			commonRenderStorage.brightness[1] = f;
			commonRenderStorage.brightness[2] = f;
			commonRenderStorage.brightness[3] = f;
			commonRenderStorage.lightmap[0] = i;
			commonRenderStorage.lightmap[1] = i;
			commonRenderStorage.lightmap[2] = i;
			commonRenderStorage.lightmap[3] = i;
			this.putQuadData(blockAndTintGetter, blockState, blockPos, vertexConsumer, poseStack.last(), bakedQuad, commonRenderStorage, j);
		}
	}

	public static void renderModel(PoseStack.Pose pose, VertexConsumer vertexConsumer, BlockStateModel blockStateModel, float f, float g, float h, int i, int j) {
		for (BlockModelPart blockModelPart : blockStateModel.collectParts(RandomSource.create(42L))) {
			for (Direction direction : DIRECTIONS) {
				renderQuadList(pose, vertexConsumer, f, g, h, blockModelPart.getQuads(direction), i, j);
			}

			renderQuadList(pose, vertexConsumer, f, g, h, blockModelPart.getQuads(null), i, j);
		}
	}

	private static void renderQuadList(PoseStack.Pose pose, VertexConsumer vertexConsumer, float f, float g, float h, List<BakedQuad> list, int i, int j) {
		for (BakedQuad bakedQuad : list) {
			float k;
			float l;
			float m;
			if (bakedQuad.isTinted()) {
				k = Mth.clamp(f, 0.0F, 1.0F);
				l = Mth.clamp(g, 0.0F, 1.0F);
				m = Mth.clamp(h, 0.0F, 1.0F);
			} else {
				k = 1.0F;
				l = 1.0F;
				m = 1.0F;
			}

			vertexConsumer.putBulkData(pose, bakedQuad, k, l, m, 1.0F, i, j);
		}
	}

	public static void enableCaching() {
		CACHE.get().enable();
	}

	public static void clearCache() {
		CACHE.get().disable();
	}

	@Environment(EnvType.CLIENT)
	protected enum AdjacencyInfo {
		DOWN(
			new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH},
			0.5F,
			true,
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.SOUTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.SOUTH
			}
		),
		UP(
			new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH},
			1.0F,
			true,
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.SOUTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.SOUTH
			}
		),
		NORTH(
			new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST},
			0.8F,
			true,
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_WEST
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_EAST
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_EAST
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_WEST
			}
		),
		SOUTH(
			new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP},
			0.8F,
			true,
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.WEST
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.WEST
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.EAST
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.EAST
			}
		),
		WEST(
			new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH},
			0.6F,
			true,
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.SOUTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.SOUTH
			}
		),
		EAST(
			new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH},
			0.6F,
			true,
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.SOUTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.SOUTH
			}
		);

		final Direction[] corners;
		final boolean doNonCubicWeight;
		final ModelBlockRenderer.SizeInfo[] vert0Weights;
		final ModelBlockRenderer.SizeInfo[] vert1Weights;
		final ModelBlockRenderer.SizeInfo[] vert2Weights;
		final ModelBlockRenderer.SizeInfo[] vert3Weights;
		private static final ModelBlockRenderer.AdjacencyInfo[] BY_FACING = Util.make(new ModelBlockRenderer.AdjacencyInfo[6], adjacencyInfos -> {
			adjacencyInfos[Direction.DOWN.get3DDataValue()] = DOWN;
			adjacencyInfos[Direction.UP.get3DDataValue()] = UP;
			adjacencyInfos[Direction.NORTH.get3DDataValue()] = NORTH;
			adjacencyInfos[Direction.SOUTH.get3DDataValue()] = SOUTH;
			adjacencyInfos[Direction.WEST.get3DDataValue()] = WEST;
			adjacencyInfos[Direction.EAST.get3DDataValue()] = EAST;
		});

		AdjacencyInfo(
			final Direction[] directions,
			final float f,
			final boolean bl,
			final ModelBlockRenderer.SizeInfo[] sizeInfos,
			final ModelBlockRenderer.SizeInfo[] sizeInfos2,
			final ModelBlockRenderer.SizeInfo[] sizeInfos3,
			final ModelBlockRenderer.SizeInfo[] sizeInfos4
		) {
			this.corners = directions;
			this.doNonCubicWeight = bl;
			this.vert0Weights = sizeInfos;
			this.vert1Weights = sizeInfos2;
			this.vert2Weights = sizeInfos3;
			this.vert3Weights = sizeInfos4;
		}

		public static ModelBlockRenderer.AdjacencyInfo fromFacing(Direction direction) {
			return BY_FACING[direction.get3DDataValue()];
		}
	}

	@Environment(EnvType.CLIENT)
	static class AmbientOcclusionRenderStorage extends ModelBlockRenderer.CommonRenderStorage {
		final float[] faceShape = new float[ModelBlockRenderer.SizeInfo.COUNT];

		public AmbientOcclusionRenderStorage() {
		}

		public void calculate(BlockAndTintGetter blockAndTintGetter, BlockState blockState, BlockPos blockPos, Direction direction, boolean bl) {
			BlockPos blockPos2 = this.faceCubic ? blockPos.relative(direction) : blockPos;
			ModelBlockRenderer.AdjacencyInfo adjacencyInfo = ModelBlockRenderer.AdjacencyInfo.fromFacing(direction);
			BlockPos.MutableBlockPos mutableBlockPos = this.scratchPos;
			mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[0]);
			BlockState blockState2 = blockAndTintGetter.getBlockState(mutableBlockPos);
			int i = this.cache.getLightColor(blockState2, blockAndTintGetter, mutableBlockPos);
			float f = this.cache.getShadeBrightness(blockState2, blockAndTintGetter, mutableBlockPos);
			mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[1]);
			BlockState blockState3 = blockAndTintGetter.getBlockState(mutableBlockPos);
			int j = this.cache.getLightColor(blockState3, blockAndTintGetter, mutableBlockPos);
			float g = this.cache.getShadeBrightness(blockState3, blockAndTintGetter, mutableBlockPos);
			mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[2]);
			BlockState blockState4 = blockAndTintGetter.getBlockState(mutableBlockPos);
			int k = this.cache.getLightColor(blockState4, blockAndTintGetter, mutableBlockPos);
			float h = this.cache.getShadeBrightness(blockState4, blockAndTintGetter, mutableBlockPos);
			mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[3]);
			BlockState blockState5 = blockAndTintGetter.getBlockState(mutableBlockPos);
			int l = this.cache.getLightColor(blockState5, blockAndTintGetter, mutableBlockPos);
			float m = this.cache.getShadeBrightness(blockState5, blockAndTintGetter, mutableBlockPos);
			BlockState blockState6 = blockAndTintGetter.getBlockState(mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[0]).move(direction));
			boolean bl2 = !blockState6.isViewBlocking(blockAndTintGetter, mutableBlockPos) || blockState6.getLightBlock() == 0;
			BlockState blockState7 = blockAndTintGetter.getBlockState(mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[1]).move(direction));
			boolean bl3 = !blockState7.isViewBlocking(blockAndTintGetter, mutableBlockPos) || blockState7.getLightBlock() == 0;
			BlockState blockState8 = blockAndTintGetter.getBlockState(mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[2]).move(direction));
			boolean bl4 = !blockState8.isViewBlocking(blockAndTintGetter, mutableBlockPos) || blockState8.getLightBlock() == 0;
			BlockState blockState9 = blockAndTintGetter.getBlockState(mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[3]).move(direction));
			boolean bl5 = !blockState9.isViewBlocking(blockAndTintGetter, mutableBlockPos) || blockState9.getLightBlock() == 0;
			float n;
			int o;
			if (!bl4 && !bl2) {
				n = f;
				o = i;
			} else {
				mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[0]).move(adjacencyInfo.corners[2]);
				BlockState blockState10 = blockAndTintGetter.getBlockState(mutableBlockPos);
				n = this.cache.getShadeBrightness(blockState10, blockAndTintGetter, mutableBlockPos);
				o = this.cache.getLightColor(blockState10, blockAndTintGetter, mutableBlockPos);
			}

			float p;
			int q;
			if (!bl5 && !bl2) {
				p = f;
				q = i;
			} else {
				mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[0]).move(adjacencyInfo.corners[3]);
				BlockState blockState10 = blockAndTintGetter.getBlockState(mutableBlockPos);
				p = this.cache.getShadeBrightness(blockState10, blockAndTintGetter, mutableBlockPos);
				q = this.cache.getLightColor(blockState10, blockAndTintGetter, mutableBlockPos);
			}

			float r;
			int s;
			if (!bl4 && !bl3) {
				r = f;
				s = i;
			} else {
				mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[1]).move(adjacencyInfo.corners[2]);
				BlockState blockState10 = blockAndTintGetter.getBlockState(mutableBlockPos);
				r = this.cache.getShadeBrightness(blockState10, blockAndTintGetter, mutableBlockPos);
				s = this.cache.getLightColor(blockState10, blockAndTintGetter, mutableBlockPos);
			}

			float t;
			int u;
			if (!bl5 && !bl3) {
				t = f;
				u = i;
			} else {
				mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[1]).move(adjacencyInfo.corners[3]);
				BlockState blockState10 = blockAndTintGetter.getBlockState(mutableBlockPos);
				t = this.cache.getShadeBrightness(blockState10, blockAndTintGetter, mutableBlockPos);
				u = this.cache.getLightColor(blockState10, blockAndTintGetter, mutableBlockPos);
			}

			int v = this.cache.getLightColor(blockState, blockAndTintGetter, blockPos);
			mutableBlockPos.setWithOffset(blockPos, direction);
			BlockState blockState11 = blockAndTintGetter.getBlockState(mutableBlockPos);
			if (this.faceCubic || !blockState11.isSolidRender()) {
				v = this.cache.getLightColor(blockState11, blockAndTintGetter, mutableBlockPos);
			}

			float w = this.faceCubic
				? this.cache.getShadeBrightness(blockAndTintGetter.getBlockState(blockPos2), blockAndTintGetter, blockPos2)
				: this.cache.getShadeBrightness(blockAndTintGetter.getBlockState(blockPos), blockAndTintGetter, blockPos);
			ModelBlockRenderer.AmbientVertexRemap ambientVertexRemap = ModelBlockRenderer.AmbientVertexRemap.fromFacing(direction);
			if (this.facePartial && adjacencyInfo.doNonCubicWeight) {
				float x = (m + f + p + w) * 0.25F;
				float y = (h + f + n + w) * 0.25F;
				float z = (h + g + r + w) * 0.25F;
				float aa = (m + g + t + w) * 0.25F;
				float ab = this.faceShape[adjacencyInfo.vert0Weights[0].index] * this.faceShape[adjacencyInfo.vert0Weights[1].index];
				float ac = this.faceShape[adjacencyInfo.vert0Weights[2].index] * this.faceShape[adjacencyInfo.vert0Weights[3].index];
				float ad = this.faceShape[adjacencyInfo.vert0Weights[4].index] * this.faceShape[adjacencyInfo.vert0Weights[5].index];
				float ae = this.faceShape[adjacencyInfo.vert0Weights[6].index] * this.faceShape[adjacencyInfo.vert0Weights[7].index];
				float af = this.faceShape[adjacencyInfo.vert1Weights[0].index] * this.faceShape[adjacencyInfo.vert1Weights[1].index];
				float ag = this.faceShape[adjacencyInfo.vert1Weights[2].index] * this.faceShape[adjacencyInfo.vert1Weights[3].index];
				float ah = this.faceShape[adjacencyInfo.vert1Weights[4].index] * this.faceShape[adjacencyInfo.vert1Weights[5].index];
				float ai = this.faceShape[adjacencyInfo.vert1Weights[6].index] * this.faceShape[adjacencyInfo.vert1Weights[7].index];
				float aj = this.faceShape[adjacencyInfo.vert2Weights[0].index] * this.faceShape[adjacencyInfo.vert2Weights[1].index];
				float ak = this.faceShape[adjacencyInfo.vert2Weights[2].index] * this.faceShape[adjacencyInfo.vert2Weights[3].index];
				float al = this.faceShape[adjacencyInfo.vert2Weights[4].index] * this.faceShape[adjacencyInfo.vert2Weights[5].index];
				float am = this.faceShape[adjacencyInfo.vert2Weights[6].index] * this.faceShape[adjacencyInfo.vert2Weights[7].index];
				float an = this.faceShape[adjacencyInfo.vert3Weights[0].index] * this.faceShape[adjacencyInfo.vert3Weights[1].index];
				float ao = this.faceShape[adjacencyInfo.vert3Weights[2].index] * this.faceShape[adjacencyInfo.vert3Weights[3].index];
				float ap = this.faceShape[adjacencyInfo.vert3Weights[4].index] * this.faceShape[adjacencyInfo.vert3Weights[5].index];
				float aq = this.faceShape[adjacencyInfo.vert3Weights[6].index] * this.faceShape[adjacencyInfo.vert3Weights[7].index];
				this.brightness[ambientVertexRemap.vert0] = Math.clamp(x * ab + y * ac + z * ad + aa * ae, 0.0F, 1.0F);
				this.brightness[ambientVertexRemap.vert1] = Math.clamp(x * af + y * ag + z * ah + aa * ai, 0.0F, 1.0F);
				this.brightness[ambientVertexRemap.vert2] = Math.clamp(x * aj + y * ak + z * al + aa * am, 0.0F, 1.0F);
				this.brightness[ambientVertexRemap.vert3] = Math.clamp(x * an + y * ao + z * ap + aa * aq, 0.0F, 1.0F);
				int ar = blend(l, i, q, v);
				int as = blend(k, i, o, v);
				int at = blend(k, j, s, v);
				int au = blend(l, j, u, v);
				this.lightmap[ambientVertexRemap.vert0] = blend(ar, as, at, au, ab, ac, ad, ae);
				this.lightmap[ambientVertexRemap.vert1] = blend(ar, as, at, au, af, ag, ah, ai);
				this.lightmap[ambientVertexRemap.vert2] = blend(ar, as, at, au, aj, ak, al, am);
				this.lightmap[ambientVertexRemap.vert3] = blend(ar, as, at, au, an, ao, ap, aq);
			} else {
				float x = (m + f + p + w) * 0.25F;
				float y = (h + f + n + w) * 0.25F;
				float z = (h + g + r + w) * 0.25F;
				float aa = (m + g + t + w) * 0.25F;
				this.lightmap[ambientVertexRemap.vert0] = blend(l, i, q, v);
				this.lightmap[ambientVertexRemap.vert1] = blend(k, i, o, v);
				this.lightmap[ambientVertexRemap.vert2] = blend(k, j, s, v);
				this.lightmap[ambientVertexRemap.vert3] = blend(l, j, u, v);
				this.brightness[ambientVertexRemap.vert0] = x;
				this.brightness[ambientVertexRemap.vert1] = y;
				this.brightness[ambientVertexRemap.vert2] = z;
				this.brightness[ambientVertexRemap.vert3] = aa;
			}

			float x = blockAndTintGetter.getShade(direction, bl);

			for (int av = 0; av < this.brightness.length; av++) {
				this.brightness[av] = this.brightness[av] * x;
			}
		}

		private static int blend(int i, int j, int k, int l) {
			if (i == 0) {
				i = l;
			}

			if (j == 0) {
				j = l;
			}

			if (k == 0) {
				k = l;
			}

			return i + j + k + l >> 2 & 16711935;
		}

		private static int blend(int i, int j, int k, int l, float f, float g, float h, float m) {
			int n = (int)((i >> 16 & 0xFF) * f + (j >> 16 & 0xFF) * g + (k >> 16 & 0xFF) * h + (l >> 16 & 0xFF) * m) & 0xFF;
			int o = (int)((i & 0xFF) * f + (j & 0xFF) * g + (k & 0xFF) * h + (l & 0xFF) * m) & 0xFF;
			return n << 16 | o;
		}
	}

	@Environment(EnvType.CLIENT)
	enum AmbientVertexRemap {
		DOWN(0, 1, 2, 3),
		UP(2, 3, 0, 1),
		NORTH(3, 0, 1, 2),
		SOUTH(0, 1, 2, 3),
		WEST(3, 0, 1, 2),
		EAST(1, 2, 3, 0);

		final int vert0;
		final int vert1;
		final int vert2;
		final int vert3;
		private static final ModelBlockRenderer.AmbientVertexRemap[] BY_FACING = Util.make(new ModelBlockRenderer.AmbientVertexRemap[6], ambientVertexRemaps -> {
			ambientVertexRemaps[Direction.DOWN.get3DDataValue()] = DOWN;
			ambientVertexRemaps[Direction.UP.get3DDataValue()] = UP;
			ambientVertexRemaps[Direction.NORTH.get3DDataValue()] = NORTH;
			ambientVertexRemaps[Direction.SOUTH.get3DDataValue()] = SOUTH;
			ambientVertexRemaps[Direction.WEST.get3DDataValue()] = WEST;
			ambientVertexRemaps[Direction.EAST.get3DDataValue()] = EAST;
		});

		AmbientVertexRemap(final int j, final int k, final int l, final int m) {
			this.vert0 = j;
			this.vert1 = k;
			this.vert2 = l;
			this.vert3 = m;
		}

		public static ModelBlockRenderer.AmbientVertexRemap fromFacing(Direction direction) {
			return BY_FACING[direction.get3DDataValue()];
		}
	}

	@Environment(EnvType.CLIENT)
	static class Cache {
		private boolean enabled;
		private final Long2IntLinkedOpenHashMap colorCache = Util.make(() -> {
			Long2IntLinkedOpenHashMap long2IntLinkedOpenHashMap = new Long2IntLinkedOpenHashMap(100, 0.25F) {
				@Override
				protected void rehash(int i) {
				}
			};
			long2IntLinkedOpenHashMap.defaultReturnValue(Integer.MAX_VALUE);
			return long2IntLinkedOpenHashMap;
		});
		private final Long2FloatLinkedOpenHashMap brightnessCache = Util.make(() -> {
			Long2FloatLinkedOpenHashMap long2FloatLinkedOpenHashMap = new Long2FloatLinkedOpenHashMap(100, 0.25F) {
				@Override
				protected void rehash(int i) {
				}
			};
			long2FloatLinkedOpenHashMap.defaultReturnValue(Float.NaN);
			return long2FloatLinkedOpenHashMap;
		});
		private final LevelRenderer.BrightnessGetter cachedBrightnessGetter = (blockAndTintGetter, blockPos) -> {
			long l = blockPos.asLong();
			int i = this.colorCache.get(l);
			if (i != Integer.MAX_VALUE) {
				return i;
			}

			int j = LevelRenderer.BrightnessGetter.DEFAULT.packedBrightness(blockAndTintGetter, blockPos);
			if (this.colorCache.size() == 100) {
				this.colorCache.removeFirstInt();
			}

			this.colorCache.put(l, j);
			return j;
		};

		private Cache() {
		}

		public void enable() {
			this.enabled = true;
		}

		public void disable() {
			this.enabled = false;
			this.colorCache.clear();
			this.brightnessCache.clear();
		}

		public int getLightColor(BlockState blockState, BlockAndTintGetter blockAndTintGetter, BlockPos blockPos) {
			return LevelRenderer.getLightColor(
				this.enabled ? this.cachedBrightnessGetter : LevelRenderer.BrightnessGetter.DEFAULT, blockAndTintGetter, blockState, blockPos
			);
		}

		public float getShadeBrightness(BlockState blockState, BlockAndTintGetter blockAndTintGetter, BlockPos blockPos) {
			long l = blockPos.asLong();
			if (this.enabled) {
				float f = this.brightnessCache.get(l);
				if (!Float.isNaN(f)) {
					return f;
				}
			}

			float f = blockState.getShadeBrightness(blockAndTintGetter, blockPos);
			if (this.enabled) {
				if (this.brightnessCache.size() == 100) {
					this.brightnessCache.removeFirstFloat();
				}

				this.brightnessCache.put(l, f);
			}

			return f;
		}
	}

	@Environment(EnvType.CLIENT)
	static class CommonRenderStorage {
		public final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();
		public boolean faceCubic;
		public boolean facePartial;
		public final float[] brightness = new float[4];
		public final int[] lightmap = new int[4];
		public int tintCacheIndex = -1;
		public int tintCacheValue;
		public final ModelBlockRenderer.Cache cache = ModelBlockRenderer.CACHE.get();
	}

	@Environment(EnvType.CLIENT)
	protected enum SizeInfo {
		DOWN(0),
		UP(1),
		NORTH(2),
		SOUTH(3),
		WEST(4),
		EAST(5),
		FLIP_DOWN(6),
		FLIP_UP(7),
		FLIP_NORTH(8),
		FLIP_SOUTH(9),
		FLIP_WEST(10),
		FLIP_EAST(11);

		public static final int COUNT = values().length;
		final int index;

		SizeInfo(final int j) {
			this.index = j;
		}
	}
}
