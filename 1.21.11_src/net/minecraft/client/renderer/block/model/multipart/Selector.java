package net.minecraft.client.renderer.block.model.multipart;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;

@Environment(EnvType.CLIENT)
public record Selector(Optional<Condition> condition, BlockStateModel.Unbaked variant) {
	public static final Codec<Selector> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Condition.CODEC.optionalFieldOf("when").forGetter(Selector::condition), BlockStateModel.Unbaked.CODEC.fieldOf("apply").forGetter(Selector::variant)
			)
			.apply(instance, Selector::new)
	);

	public <O, S extends StateHolder<O, S>> Predicate<S> instantiate(StateDefinition<O, S> stateDefinition) {
		return this.condition.<Predicate<S>>map(condition -> condition.instantiate(stateDefinition)).orElse(stateHolder -> true);
	}
}
