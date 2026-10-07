package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LayeredCauldronBlock extends AbstractCauldronBlock {
	public static final MapCodec<LayeredCauldronBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Biome.Precipitation.CODEC.fieldOf("precipitation").forGetter(layeredCauldronBlock -> layeredCauldronBlock.precipitationType),
				CauldronInteraction.CODEC.fieldOf("interactions").forGetter(layeredCauldronBlock -> layeredCauldronBlock.interactions),
				propertiesCodec()
			)
			.apply(instance, LayeredCauldronBlock::new)
	);
	public static final int MIN_FILL_LEVEL = 1;
	public static final int MAX_FILL_LEVEL = 3;
	public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_CAULDRON;
	private static final int BASE_CONTENT_HEIGHT = 6;
	private static final double HEIGHT_PER_LEVEL = 3.0;
	private static final VoxelShape[] FILLED_SHAPES = Util.make(
		() -> Block.boxes(2, i -> Shapes.or(AbstractCauldronBlock.SHAPE, Block.column(12.0, 4.0, getPixelContentHeight(i + 1))))
	);
	private final Biome.Precipitation precipitationType;

	@Override
	public MapCodec<LayeredCauldronBlock> codec() {
		return CODEC;
	}

	public LayeredCauldronBlock(Biome.Precipitation precipitation, CauldronInteraction.InteractionMap interactionMap, BlockBehaviour.Properties properties) {
		super(properties, interactionMap);
		this.precipitationType = precipitation;
		this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 1));
	}

	@Override
	public boolean isFull(BlockState blockState) {
		return blockState.getValue(LEVEL) == 3;
	}

	@Override
	protected boolean canReceiveStalactiteDrip(Fluid fluid) {
		return fluid == Fluids.WATER && this.precipitationType == Biome.Precipitation.RAIN;
	}

	@Override
	protected double getContentHeight(BlockState blockState) {
		return getPixelContentHeight(blockState.getValue(LEVEL)) / 16.0;
	}

	private static double getPixelContentHeight(int i) {
		return 6.0 + i * 3.0;
	}

	@Override
	protected VoxelShape getEntityInsideCollisionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Entity entity) {
		return FILLED_SHAPES[blockState.getValue(LEVEL) - 1];
	}

	@Override
	protected void entityInside(
		BlockState blockState, Level level, BlockPos blockPos, Entity entity, InsideBlockEffectApplier insideBlockEffectApplier, boolean bl
	) {
		if (level instanceof ServerLevel serverLevel) {
			BlockPos blockPos2 = blockPos.immutable();
			insideBlockEffectApplier.runBefore(InsideBlockEffectType.EXTINGUISH, entityx -> {
				if (entityx.isOnFire() && entityx.mayInteract(serverLevel, blockPos2)) {
					this.handleEntityOnFireInside(blockState, level, blockPos2);
				}
			});
		}

		insideBlockEffectApplier.apply(InsideBlockEffectType.EXTINGUISH);
	}

	private void handleEntityOnFireInside(BlockState blockState, Level level, BlockPos blockPos) {
		if (this.precipitationType == Biome.Precipitation.SNOW) {
			lowerFillLevel(Blocks.WATER_CAULDRON.defaultBlockState().setValue(LEVEL, blockState.getValue(LEVEL)), level, blockPos);
		} else {
			lowerFillLevel(blockState, level, blockPos);
		}
	}

	public static void lowerFillLevel(BlockState blockState, Level level, BlockPos blockPos) {
		int i = blockState.getValue(LEVEL) - 1;
		BlockState blockState2 = i == 0 ? Blocks.CAULDRON.defaultBlockState() : blockState.setValue(LEVEL, i);
		level.setBlockAndUpdate(blockPos, blockState2);
		level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(blockState2));
	}

	@Override
	public void handlePrecipitation(BlockState blockState, Level level, BlockPos blockPos, Biome.Precipitation precipitation) {
		if (CauldronBlock.shouldHandlePrecipitation(level, precipitation) && blockState.getValue(LEVEL) != 3 && precipitation == this.precipitationType) {
			BlockState blockState2 = blockState.cycle(LEVEL);
			level.setBlockAndUpdate(blockPos, blockState2);
			level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(blockState2));
		}
	}

	@Override
	protected int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos blockPos, Direction direction) {
		return blockState.getValue(LEVEL);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LEVEL);
	}

	@Override
	protected void receiveStalactiteDrip(BlockState blockState, Level level, BlockPos blockPos, Fluid fluid) {
		if (!this.isFull(blockState)) {
			BlockState blockState2 = blockState.setValue(LEVEL, blockState.getValue(LEVEL) + 1);
			level.setBlockAndUpdate(blockPos, blockState2);
			level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(blockState2));
			level.levelEvent(1047, blockPos, 0);
		}
	}
}
