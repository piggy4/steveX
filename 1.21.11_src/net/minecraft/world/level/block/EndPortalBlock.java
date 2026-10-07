package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class EndPortalBlock extends BaseEntityBlock implements Portal {
	public static final MapCodec<EndPortalBlock> CODEC = simpleCodec(EndPortalBlock::new);
	private static final VoxelShape SHAPE = Block.column(16.0, 6.0, 12.0);

	@Override
	public MapCodec<EndPortalBlock> codec() {
		return CODEC;
	}

	public EndPortalBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
		return new TheEndPortalBlockEntity(blockPos, blockState);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getEntityInsideCollisionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Entity entity) {
		return blockState.getShape(blockGetter, blockPos);
	}

	@Override
	protected void entityInside(
		BlockState blockState, Level level, BlockPos blockPos, Entity entity, InsideBlockEffectApplier insideBlockEffectApplier, boolean bl
	) {
		if (entity.canUsePortal(false)) {
			if (!level.isClientSide() && level.dimension() == Level.END && entity instanceof ServerPlayer serverPlayer && !serverPlayer.seenCredits) {
				serverPlayer.showEndCredits();
			} else {
				entity.setAsInsidePortal(this, blockPos);
			}
		}
	}

	@Override
	public @Nullable TeleportTransition getPortalDestination(ServerLevel serverLevel, Entity entity, BlockPos blockPos) {
		LevelData.RespawnData respawnData = serverLevel.getRespawnData();
		ResourceKey<Level> resourceKey = serverLevel.dimension();
		boolean bl = resourceKey == Level.END;
		ResourceKey<Level> resourceKey2 = bl ? respawnData.dimension() : Level.END;
		BlockPos blockPos2 = bl ? respawnData.pos() : ServerLevel.END_SPAWN_POINT;
		ServerLevel serverLevel2 = serverLevel.getServer().getLevel(resourceKey2);
		if (serverLevel2 == null) {
			return null;
		}

		Vec3 vec3 = blockPos2.getBottomCenter();
		float f;
		float g;
		Set<Relative> set;
		if (!bl) {
			EndPlatformFeature.createEndPlatform(serverLevel2, BlockPos.containing(vec3).below(), true);
			f = Direction.WEST.toYRot();
			g = 0.0F;
			set = Relative.union(Relative.DELTA, Set.of(Relative.X_ROT));
			if (entity instanceof ServerPlayer) {
				vec3 = vec3.subtract(0.0, 1.0, 0.0);
			}
		} else {
			f = respawnData.yaw();
			g = respawnData.pitch();
			set = Relative.union(Relative.DELTA, Relative.ROTATION);
			if (entity instanceof ServerPlayer serverPlayer) {
				return serverPlayer.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
			}

			vec3 = entity.adjustSpawnLocation(serverLevel2, blockPos2).getBottomCenter();
		}

		return new TeleportTransition(serverLevel2, vec3, Vec3.ZERO, f, g, set, TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET));
	}

	@Override
	public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
		double d = blockPos.getX() + randomSource.nextDouble();
		double e = blockPos.getY() + 0.8;
		double f = blockPos.getZ() + randomSource.nextDouble();
		level.addParticle(ParticleTypes.SMOKE, d, e, f, 0.0, 0.0, 0.0);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader levelReader, BlockPos blockPos, BlockState blockState, boolean bl) {
		return ItemStack.EMPTY;
	}

	@Override
	protected boolean canBeReplaced(BlockState blockState, Fluid fluid) {
		return false;
	}

	@Override
	protected RenderShape getRenderShape(BlockState blockState) {
		return RenderShape.INVISIBLE;
	}
}
