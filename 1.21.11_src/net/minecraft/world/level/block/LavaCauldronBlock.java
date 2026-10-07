package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LavaCauldronBlock extends AbstractCauldronBlock {
	public static final MapCodec<LavaCauldronBlock> CODEC = simpleCodec(LavaCauldronBlock::new);
	private static final VoxelShape SHAPE_INSIDE = Block.column(12.0, 4.0, 15.0);
	private static final VoxelShape FILLED_SHAPE = Shapes.or(AbstractCauldronBlock.SHAPE, SHAPE_INSIDE);

	@Override
	public MapCodec<LavaCauldronBlock> codec() {
		return CODEC;
	}

	public LavaCauldronBlock(BlockBehaviour.Properties properties) {
		super(properties, CauldronInteraction.LAVA);
	}

	@Override
	protected double getContentHeight(BlockState blockState) {
		return 0.9375;
	}

	@Override
	public boolean isFull(BlockState blockState) {
		return true;
	}

	@Override
	protected VoxelShape getEntityInsideCollisionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Entity entity) {
		return FILLED_SHAPE;
	}

	@Override
	protected void entityInside(
		BlockState blockState, Level level, BlockPos blockPos, Entity entity, InsideBlockEffectApplier insideBlockEffectApplier, boolean bl
	) {
		insideBlockEffectApplier.apply(InsideBlockEffectType.CLEAR_FREEZE);
		insideBlockEffectApplier.apply(InsideBlockEffectType.LAVA_IGNITE);
		insideBlockEffectApplier.runAfter(InsideBlockEffectType.LAVA_IGNITE, Entity::lavaHurt);
	}

	@Override
	protected int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos blockPos, Direction direction) {
		return 3;
	}
}
