package net.minecraft.client.renderer.block;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(EnvType.CLIENT)
public class LiquidBlockRenderer {
	private static final float MAX_FLUID_HEIGHT = 0.8888889F;
	private final TextureAtlasSprite lavaStill;
	private final TextureAtlasSprite lavaFlowing;
	private final TextureAtlasSprite waterStill;
	private final TextureAtlasSprite waterFlowing;
	private final TextureAtlasSprite waterOverlay;

	public LiquidBlockRenderer(MaterialSet materialSet) {
		this.lavaStill = materialSet.get(ModelBakery.LAVA_STILL);
		this.lavaFlowing = materialSet.get(ModelBakery.LAVA_FLOW);
		this.waterStill = materialSet.get(ModelBakery.WATER_STILL);
		this.waterFlowing = materialSet.get(ModelBakery.WATER_FLOW);
		this.waterOverlay = materialSet.get(ModelBakery.WATER_OVERLAY);
	}

	private static boolean isNeighborSameFluid(FluidState fluidState, FluidState fluidState2) {
		return fluidState2.getType().isSame(fluidState.getType());
	}

	private static boolean isFaceOccludedByState(Direction direction, float f, BlockState blockState) {
		VoxelShape voxelShape = blockState.getFaceOcclusionShape(direction.getOpposite());
		if (voxelShape == Shapes.empty()) {
			return false;
		} else if (voxelShape == Shapes.block()) {
			boolean bl = f == 1.0F;
			return direction != Direction.UP || bl;
		} else {
			VoxelShape voxelShape2 = Shapes.box(0.0, 0.0, 0.0, 1.0, f, 1.0);
			return Shapes.blockOccludes(voxelShape2, voxelShape, direction);
		}
	}

	private static boolean isFaceOccludedByNeighbor(Direction direction, float f, BlockState blockState) {
		return isFaceOccludedByState(direction, f, blockState);
	}

	private static boolean isFaceOccludedBySelf(BlockState blockState, Direction direction) {
		return isFaceOccludedByState(direction.getOpposite(), 1.0F, blockState);
	}

	public static boolean shouldRenderFace(FluidState fluidState, BlockState blockState, Direction direction, FluidState fluidState2) {
		return !isFaceOccludedBySelf(blockState, direction) && !isNeighborSameFluid(fluidState, fluidState2);
	}

	public void tesselate(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos, VertexConsumer vertexConsumer, BlockState blockState, FluidState fluidState) {
		boolean bl = fluidState.is(FluidTags.LAVA);
		TextureAtlasSprite textureAtlasSprite = bl ? this.lavaStill : this.waterStill;
		TextureAtlasSprite textureAtlasSprite2 = bl ? this.lavaFlowing : this.waterFlowing;
		int i = bl ? 16777215 : BiomeColors.getAverageWaterColor(blockAndTintGetter, blockPos);
		float f = (i >> 16 & 0xFF) / 255.0F;
		float g = (i >> 8 & 0xFF) / 255.0F;
		float h = (i & 0xFF) / 255.0F;
		BlockState blockState2 = blockAndTintGetter.getBlockState(blockPos.relative(Direction.DOWN));
		FluidState fluidState2 = blockState2.getFluidState();
		BlockState blockState3 = blockAndTintGetter.getBlockState(blockPos.relative(Direction.UP));
		FluidState fluidState3 = blockState3.getFluidState();
		BlockState blockState4 = blockAndTintGetter.getBlockState(blockPos.relative(Direction.NORTH));
		FluidState fluidState4 = blockState4.getFluidState();
		BlockState blockState5 = blockAndTintGetter.getBlockState(blockPos.relative(Direction.SOUTH));
		FluidState fluidState5 = blockState5.getFluidState();
		BlockState blockState6 = blockAndTintGetter.getBlockState(blockPos.relative(Direction.WEST));
		FluidState fluidState6 = blockState6.getFluidState();
		BlockState blockState7 = blockAndTintGetter.getBlockState(blockPos.relative(Direction.EAST));
		FluidState fluidState7 = blockState7.getFluidState();
		boolean bl2 = !isNeighborSameFluid(fluidState, fluidState3);
		boolean bl3 = shouldRenderFace(fluidState, blockState, Direction.DOWN, fluidState2) && !isFaceOccludedByNeighbor(Direction.DOWN, 0.8888889F, blockState2);
		boolean bl4 = shouldRenderFace(fluidState, blockState, Direction.NORTH, fluidState4);
		boolean bl5 = shouldRenderFace(fluidState, blockState, Direction.SOUTH, fluidState5);
		boolean bl6 = shouldRenderFace(fluidState, blockState, Direction.WEST, fluidState6);
		boolean bl7 = shouldRenderFace(fluidState, blockState, Direction.EAST, fluidState7);
		if (bl2 || bl3 || bl7 || bl6 || bl4 || bl5) {
			float j = blockAndTintGetter.getShade(Direction.DOWN, true);
			float k = blockAndTintGetter.getShade(Direction.UP, true);
			float l = blockAndTintGetter.getShade(Direction.NORTH, true);
			float m = blockAndTintGetter.getShade(Direction.WEST, true);
			Fluid fluid = fluidState.getType();
			float n = this.getHeight(blockAndTintGetter, fluid, blockPos, blockState, fluidState);
			float o;
			float p;
			float q;
			float r;
			if (n >= 1.0F) {
				o = 1.0F;
				p = 1.0F;
				q = 1.0F;
				r = 1.0F;
			} else {
				float s = this.getHeight(blockAndTintGetter, fluid, blockPos.north(), blockState4, fluidState4);
				float t = this.getHeight(blockAndTintGetter, fluid, blockPos.south(), blockState5, fluidState5);
				float u = this.getHeight(blockAndTintGetter, fluid, blockPos.east(), blockState7, fluidState7);
				float v = this.getHeight(blockAndTintGetter, fluid, blockPos.west(), blockState6, fluidState6);
				o = this.calculateAverageHeight(blockAndTintGetter, fluid, n, s, u, blockPos.relative(Direction.NORTH).relative(Direction.EAST));
				p = this.calculateAverageHeight(blockAndTintGetter, fluid, n, s, v, blockPos.relative(Direction.NORTH).relative(Direction.WEST));
				q = this.calculateAverageHeight(blockAndTintGetter, fluid, n, t, u, blockPos.relative(Direction.SOUTH).relative(Direction.EAST));
				r = this.calculateAverageHeight(blockAndTintGetter, fluid, n, t, v, blockPos.relative(Direction.SOUTH).relative(Direction.WEST));
			}

			float s = blockPos.getX() & 15;
			float t = blockPos.getY() & 15;
			float u = blockPos.getZ() & 15;
			float v = 0.001F;
			float w = bl3 ? 0.001F : 0.0F;
			if (bl2 && !isFaceOccludedByNeighbor(Direction.UP, Math.min(Math.min(p, r), Math.min(q, o)), blockState3)) {
				p -= 0.001F;
				r -= 0.001F;
				q -= 0.001F;
				o -= 0.001F;
				Vec3 vec3 = fluidState.getFlow(blockAndTintGetter, blockPos);
				float x;
				float z;
				float ab;
				float ad;
				float y;
				float aa;
				float ac;
				float ae;
				if (vec3.x == 0.0 && vec3.z == 0.0) {
					x = textureAtlasSprite.getU(0.0F);
					y = textureAtlasSprite.getV(0.0F);
					z = x;
					aa = textureAtlasSprite.getV(1.0F);
					ab = textureAtlasSprite.getU(1.0F);
					ac = aa;
					ad = ab;
					ae = y;
				} else {
					float af = (float)Mth.atan2(vec3.z, vec3.x) - (float) (Math.PI / 2);
					float ag = Mth.sin(af) * 0.25F;
					float ah = Mth.cos(af) * 0.25F;
					float ai = 0.5F;
					x = textureAtlasSprite2.getU(0.5F + (-ah - ag));
					y = textureAtlasSprite2.getV(0.5F + (-ah + ag));
					z = textureAtlasSprite2.getU(0.5F + (-ah + ag));
					aa = textureAtlasSprite2.getV(0.5F + (ah + ag));
					ab = textureAtlasSprite2.getU(0.5F + (ah + ag));
					ac = textureAtlasSprite2.getV(0.5F + (ah - ag));
					ad = textureAtlasSprite2.getU(0.5F + (ah - ag));
					ae = textureAtlasSprite2.getV(0.5F + (-ah - ag));
				}

				int aj = this.getLightColor(blockAndTintGetter, blockPos);
				float ag = k * f;
				float ah = k * g;
				float ai = k * h;
				this.vertex(vertexConsumer, s + 0.0F, t + p, u + 0.0F, ag, ah, ai, x, y, aj);
				this.vertex(vertexConsumer, s + 0.0F, t + r, u + 1.0F, ag, ah, ai, z, aa, aj);
				this.vertex(vertexConsumer, s + 1.0F, t + q, u + 1.0F, ag, ah, ai, ab, ac, aj);
				this.vertex(vertexConsumer, s + 1.0F, t + o, u + 0.0F, ag, ah, ai, ad, ae, aj);
				if (fluidState.shouldRenderBackwardUpFace(blockAndTintGetter, blockPos.above())) {
					this.vertex(vertexConsumer, s + 0.0F, t + p, u + 0.0F, ag, ah, ai, x, y, aj);
					this.vertex(vertexConsumer, s + 1.0F, t + o, u + 0.0F, ag, ah, ai, ad, ae, aj);
					this.vertex(vertexConsumer, s + 1.0F, t + q, u + 1.0F, ag, ah, ai, ab, ac, aj);
					this.vertex(vertexConsumer, s + 0.0F, t + r, u + 1.0F, ag, ah, ai, z, aa, aj);
				}
			}

			if (bl3) {
				float x = textureAtlasSprite.getU0();
				float z = textureAtlasSprite.getU1();
				float ab = textureAtlasSprite.getV0();
				float ad = textureAtlasSprite.getV1();
				int ak = this.getLightColor(blockAndTintGetter, blockPos.below());
				float aa = j * f;
				float ac = j * g;
				float ae = j * h;
				this.vertex(vertexConsumer, s, t + w, u + 1.0F, aa, ac, ae, x, ad, ak);
				this.vertex(vertexConsumer, s, t + w, u, aa, ac, ae, x, ab, ak);
				this.vertex(vertexConsumer, s + 1.0F, t + w, u, aa, ac, ae, z, ab, ak);
				this.vertex(vertexConsumer, s + 1.0F, t + w, u + 1.0F, aa, ac, ae, z, ad, ak);
			}

			int al = this.getLightColor(blockAndTintGetter, blockPos);

			for (Direction direction : Direction.Plane.HORIZONTAL) {
				float ad;
				float y;
				float aa;
				float ac;
				float ae;
				float am;
				boolean bl8;
				switch (direction) {
					case NORTH:
						ad = p;
						y = o;
						aa = s;
						ae = s + 1.0F;
						ac = u + 0.001F;
						am = u + 0.001F;
						bl8 = bl4;
						break;
					case SOUTH:
						ad = q;
						y = r;
						aa = s + 1.0F;
						ae = s;
						ac = u + 1.0F - 0.001F;
						am = u + 1.0F - 0.001F;
						bl8 = bl5;
						break;
					case WEST:
						ad = r;
						y = p;
						aa = s + 0.001F;
						ae = s + 0.001F;
						ac = u + 1.0F;
						am = u;
						bl8 = bl6;
						break;
					default:
						ad = o;
						y = q;
						aa = s + 1.0F - 0.001F;
						ae = s + 1.0F - 0.001F;
						ac = u;
						am = u + 1.0F;
						bl8 = bl7;
				}

				if (bl8 && !isFaceOccludedByNeighbor(direction, Math.max(ad, y), blockAndTintGetter.getBlockState(blockPos.relative(direction)))) {
					BlockPos blockPos2 = blockPos.relative(direction);
					TextureAtlasSprite textureAtlasSprite3 = textureAtlasSprite2;
					if (!bl) {
						Block block = blockAndTintGetter.getBlockState(blockPos2).getBlock();
						if (block instanceof HalfTransparentBlock || block instanceof LeavesBlock) {
							textureAtlasSprite3 = this.waterOverlay;
						}
					}

					float ai = textureAtlasSprite3.getU(0.0F);
					float an = textureAtlasSprite3.getU(0.5F);
					float ao = textureAtlasSprite3.getV((1.0F - ad) * 0.5F);
					float ap = textureAtlasSprite3.getV((1.0F - y) * 0.5F);
					float aq = textureAtlasSprite3.getV(0.5F);
					float ar = direction.getAxis() == Direction.Axis.Z ? l : m;
					float as = k * ar * f;
					float at = k * ar * g;
					float au = k * ar * h;
					this.vertex(vertexConsumer, aa, t + ad, ac, as, at, au, ai, ao, al);
					this.vertex(vertexConsumer, ae, t + y, am, as, at, au, an, ap, al);
					this.vertex(vertexConsumer, ae, t + w, am, as, at, au, an, aq, al);
					this.vertex(vertexConsumer, aa, t + w, ac, as, at, au, ai, aq, al);
					if (textureAtlasSprite3 != this.waterOverlay) {
						this.vertex(vertexConsumer, aa, t + w, ac, as, at, au, ai, aq, al);
						this.vertex(vertexConsumer, ae, t + w, am, as, at, au, an, aq, al);
						this.vertex(vertexConsumer, ae, t + y, am, as, at, au, an, ap, al);
						this.vertex(vertexConsumer, aa, t + ad, ac, as, at, au, ai, ao, al);
					}
				}
			}
		}
	}

	private float calculateAverageHeight(BlockAndTintGetter blockAndTintGetter, Fluid fluid, float f, float g, float h, BlockPos blockPos) {
		if (!(h >= 1.0F) && !(g >= 1.0F)) {
			float[] fs = new float[2];
			if (h > 0.0F || g > 0.0F) {
				float i = this.getHeight(blockAndTintGetter, fluid, blockPos);
				if (i >= 1.0F) {
					return 1.0F;
				}

				this.addWeightedHeight(fs, i);
			}

			this.addWeightedHeight(fs, f);
			this.addWeightedHeight(fs, h);
			this.addWeightedHeight(fs, g);
			return fs[0] / fs[1];
		} else {
			return 1.0F;
		}
	}

	private void addWeightedHeight(float[] fs, float f) {
		if (f >= 0.8F) {
			fs[0] += f * 10.0F;
			fs[1] += 10.0F;
		} else if (f >= 0.0F) {
			fs[0] += f;
			fs[1]++;
		}
	}

	private float getHeight(BlockAndTintGetter blockAndTintGetter, Fluid fluid, BlockPos blockPos) {
		BlockState blockState = blockAndTintGetter.getBlockState(blockPos);
		return this.getHeight(blockAndTintGetter, fluid, blockPos, blockState, blockState.getFluidState());
	}

	private float getHeight(BlockAndTintGetter blockAndTintGetter, Fluid fluid, BlockPos blockPos, BlockState blockState, FluidState fluidState) {
		if (fluid.isSame(fluidState.getType())) {
			BlockState blockState2 = blockAndTintGetter.getBlockState(blockPos.above());
			return fluid.isSame(blockState2.getFluidState().getType()) ? 1.0F : fluidState.getOwnHeight();
		} else {
			return !blockState.isSolid() ? 0.0F : -1.0F;
		}
	}

	private void vertex(VertexConsumer vertexConsumer, float f, float g, float h, float i, float j, float k, float l, float m, int n) {
		vertexConsumer.addVertex(f, g, h).setColor(i, j, k, 1.0F).setUv(l, m).setLight(n).setNormal(0.0F, 1.0F, 0.0F);
	}

	private int getLightColor(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos) {
		int i = LevelRenderer.getLightColor(blockAndTintGetter, blockPos);
		int j = LevelRenderer.getLightColor(blockAndTintGetter, blockPos.above());
		int k = i & 0xFF;
		int l = j & 0xFF;
		int m = i >> 16 & 0xFF;
		int n = j >> 16 & 0xFF;
		return (k > l ? k : l) | (m > n ? m : n) << 16;
	}
}
