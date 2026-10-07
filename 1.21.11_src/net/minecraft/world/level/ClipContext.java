package net.minecraft.world.level;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ClipContext {
	private final Vec3 from;
	private final Vec3 to;
	private final ClipContext.Block block;
	private final ClipContext.Fluid fluid;
	private final CollisionContext collisionContext;

	public ClipContext(Vec3 vec3, Vec3 vec32, ClipContext.Block block, ClipContext.Fluid fluid, Entity entity) {
		this(vec3, vec32, block, fluid, CollisionContext.of(entity));
	}

	public ClipContext(Vec3 vec3, Vec3 vec32, ClipContext.Block block, ClipContext.Fluid fluid, CollisionContext collisionContext) {
		this.from = vec3;
		this.to = vec32;
		this.block = block;
		this.fluid = fluid;
		this.collisionContext = collisionContext;
	}

	public Vec3 getTo() {
		return this.to;
	}

	public Vec3 getFrom() {
		return this.from;
	}

	public VoxelShape getBlockShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
		return this.block.get(blockState, blockGetter, blockPos, this.collisionContext);
	}

	public VoxelShape getFluidShape(FluidState fluidState, BlockGetter blockGetter, BlockPos blockPos) {
		return this.fluid.canPick(fluidState) ? fluidState.getShape(blockGetter, blockPos) : Shapes.empty();
	}

	public enum Block implements ClipContext.ShapeGetter {
		COLLIDER(BlockBehaviour.BlockStateBase::getCollisionShape),
		OUTLINE(BlockBehaviour.BlockStateBase::getShape),
		VISUAL(BlockBehaviour.BlockStateBase::getVisualShape),
		FALLDAMAGE_RESETTING(
			(blockState, blockGetter, blockPos, collisionContext) -> {
				if (blockState.is(BlockTags.FALL_DAMAGE_RESETTING)) {
					return Shapes.block();
				}

				if (collisionContext instanceof EntityCollisionContext entityCollisionContext
					&& entityCollisionContext.getEntity() != null
					&& entityCollisionContext.getEntity().getType() == EntityType.PLAYER) {
					if (blockState.is(Blocks.END_GATEWAY) || blockState.is(Blocks.END_PORTAL)) {
						return Shapes.block();
					}

					if (blockGetter instanceof ServerLevel serverLevel
						&& blockState.is(Blocks.NETHER_PORTAL)
						&& serverLevel.getGameRules().get(GameRules.PLAYERS_NETHER_PORTAL_DEFAULT_DELAY) == 0) {
						return Shapes.block();
					}
				}

				return Shapes.empty();
			}
		);

		private final ClipContext.ShapeGetter shapeGetter;

		Block(final ClipContext.ShapeGetter shapeGetter) {
			this.shapeGetter = shapeGetter;
		}

		@Override
		public VoxelShape get(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
			return this.shapeGetter.get(blockState, blockGetter, blockPos, collisionContext);
		}
	}

	public enum Fluid {
		NONE(fluidState -> false),
		SOURCE_ONLY(FluidState::isSource),
		ANY(fluidState -> !fluidState.isEmpty()),
		WATER(fluidState -> fluidState.is(FluidTags.WATER));

		private final Predicate<FluidState> canPick;

		Fluid(final Predicate<FluidState> predicate) {
			this.canPick = predicate;
		}

		public boolean canPick(FluidState fluidState) {
			return this.canPick.test(fluidState);
		}
	}

	public interface ShapeGetter {
		VoxelShape get(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext);
	}
}
