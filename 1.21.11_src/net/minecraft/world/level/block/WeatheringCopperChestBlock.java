package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public class WeatheringCopperChestBlock extends CopperChestBlock implements WeatheringCopper {
	public static final MapCodec<WeatheringCopperChestBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(CopperChestBlock::getState),
				BuiltInRegistries.SOUND_EVENT.byNameCodec().fieldOf("open_sound").forGetter(ChestBlock::getOpenChestSound),
				BuiltInRegistries.SOUND_EVENT.byNameCodec().fieldOf("close_sound").forGetter(ChestBlock::getCloseChestSound),
				propertiesCodec()
			)
			.apply(instance, WeatheringCopperChestBlock::new)
	);

	@Override
	public MapCodec<WeatheringCopperChestBlock> codec() {
		return CODEC;
	}

	public WeatheringCopperChestBlock(
		WeatheringCopper.WeatherState weatherState, SoundEvent soundEvent, SoundEvent soundEvent2, BlockBehaviour.Properties properties
	) {
		super(weatherState, soundEvent, soundEvent2, properties);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState blockState) {
		return WeatheringCopper.getNext(blockState.getBlock()).isPresent();
	}

	@Override
	protected void randomTick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
		if (!blockState.getValue(ChestBlock.TYPE).equals(ChestType.RIGHT)
			&& serverLevel.getBlockEntity(blockPos) instanceof ChestBlockEntity chestBlockEntity
			&& chestBlockEntity.getEntitiesWithContainerOpen().isEmpty()) {
			this.changeOverTime(blockState, serverLevel, blockPos, randomSource);
		}
	}

	public WeatheringCopper.WeatherState getAge() {
		return this.getState();
	}

	@Override
	public boolean isWaxed() {
		return false;
	}
}
