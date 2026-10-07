package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CopperGolemStatueBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class WeatheringCopperGolemStatueBlock extends CopperGolemStatueBlock implements WeatheringCopper {
	public static final MapCodec<WeatheringCopperGolemStatueBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(ChangeOverTimeBlock::getAge), propertiesCodec())
			.apply(instance, WeatheringCopperGolemStatueBlock::new)
	);

	@Override
	public MapCodec<WeatheringCopperGolemStatueBlock> codec() {
		return CODEC;
	}

	public WeatheringCopperGolemStatueBlock(WeatheringCopper.WeatherState weatherState, BlockBehaviour.Properties properties) {
		super(weatherState, properties);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState blockState) {
		return WeatheringCopper.getNext(blockState.getBlock()).isPresent();
	}

	@Override
	protected void randomTick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
		this.changeOverTime(blockState, serverLevel, blockPos, randomSource);
	}

	public WeatheringCopper.WeatherState getAge() {
		return this.getWeatheringState();
	}

	@Override
	protected InteractionResult useItemOn(
		ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult
	) {
		if (level.getBlockEntity(blockPos) instanceof CopperGolemStatueBlockEntity copperGolemStatueBlockEntity) {
			if (!itemStack.is(ItemTags.AXES)) {
				if (itemStack.is(Items.HONEYCOMB)) {
					return InteractionResult.PASS;
				}

				this.updatePose(level, blockState, blockPos, player);
				return InteractionResult.SUCCESS;
			}

			if (this.getAge().equals(WeatheringCopper.WeatherState.UNAFFECTED)) {
				CopperGolem copperGolem = copperGolemStatueBlockEntity.removeStatue(blockState);
				itemStack.hurtAndBreak(1, player, interactionHand.asEquipmentSlot());
				if (copperGolem != null) {
					level.addFreshEntity(copperGolem);
					level.removeBlock(blockPos, false);
					return InteractionResult.SUCCESS;
				}
			}
		}

		return InteractionResult.PASS;
	}
}
