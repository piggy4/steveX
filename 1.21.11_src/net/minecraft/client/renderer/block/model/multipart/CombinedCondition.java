package net.minecraft.client.renderer.block.model.multipart;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;

@Environment(EnvType.CLIENT)
public record CombinedCondition(CombinedCondition.Operation operation, List<Condition> terms) implements Condition {
	@Override
	public <O, S extends StateHolder<O, S>> Predicate<S> instantiate(StateDefinition<O, S> stateDefinition) {
		return this.operation.apply(Lists.transform(this.terms, condition -> condition.instantiate(stateDefinition)));
	}

	@Environment(EnvType.CLIENT)
	public enum Operation implements StringRepresentable {
		AND("AND") {
			@Override
			public <V> Predicate<V> apply(List<Predicate<V>> list) {
				return Util.allOf(list);
			}
		},
		OR("OR") {
			@Override
			public <V> Predicate<V> apply(List<Predicate<V>> list) {
				return Util.anyOf(list);
			}
		};

		public static final Codec<CombinedCondition.Operation> CODEC = StringRepresentable.fromEnum(CombinedCondition.Operation::values);
		private final String name;

		Operation(final String string2) {
			this.name = string2;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		public abstract <V> Predicate<V> apply(List<Predicate<V>> list);
	}
}
