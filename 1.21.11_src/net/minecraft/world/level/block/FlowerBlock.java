package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class FlowerBlock extends VegetationBlock implements SuspiciousEffectHolder {
	protected static final MapCodec<SuspiciousStewEffects> EFFECTS_FIELD = SuspiciousStewEffects.CODEC.fieldOf("suspicious_stew_effects");
	public static final MapCodec<FlowerBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(EFFECTS_FIELD.forGetter(FlowerBlock::getSuspiciousEffects), propertiesCodec()).apply(instance, FlowerBlock::new)
	);
	private static final VoxelShape SHAPE = Block.column(6.0, 0.0, 10.0);
	private final SuspiciousStewEffects suspiciousStewEffects;

	@Override
	public MapCodec<? extends FlowerBlock> codec() {
		return CODEC;
	}

	public FlowerBlock(Holder<MobEffect> holder, float f, BlockBehaviour.Properties properties) {
		this(makeEffectList(holder, f), properties);
	}

	public FlowerBlock(SuspiciousStewEffects suspiciousStewEffects, BlockBehaviour.Properties properties) {
		super(properties);
		this.suspiciousStewEffects = suspiciousStewEffects;
	}

	protected static SuspiciousStewEffects makeEffectList(Holder<MobEffect> holder, float f) {
		return new SuspiciousStewEffects(List.of(new SuspiciousStewEffects.Entry(holder, Mth.floor(f * 20.0F))));
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE.move(blockState.getOffset(blockPos));
	}

	@Override
	public SuspiciousStewEffects getSuspiciousEffects() {
		return this.suspiciousStewEffects;
	}

	public @Nullable MobEffectInstance getBeeInteractionEffect() {
		return null;
	}
}
