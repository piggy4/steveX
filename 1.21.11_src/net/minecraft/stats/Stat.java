package net.minecraft.stats;

import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jspecify.annotations.Nullable;

public class Stat<T> extends ObjectiveCriteria {
	public static final StreamCodec<RegistryFriendlyByteBuf, Stat<?>> STREAM_CODEC = ByteBufCodecs.registry(Registries.STAT_TYPE)
		.dispatch(Stat::getType, StatType::streamCodec);
	private final StatFormatter formatter;
	private final T value;
	private final StatType<T> type;

	protected Stat(StatType<T> statType, T object, StatFormatter statFormatter) {
		super(buildName(statType, object));
		this.type = statType;
		this.formatter = statFormatter;
		this.value = object;
	}

	public static <T> String buildName(StatType<T> statType, T object) {
		return locationToKey(BuiltInRegistries.STAT_TYPE.getKey(statType)) + ":" + locationToKey(statType.getRegistry().getKey(object));
	}

	private static String locationToKey(@Nullable Identifier identifier) {
		return identifier.toString().replace(':', '.');
	}

	public StatType<T> getType() {
		return this.type;
	}

	public T getValue() {
		return this.value;
	}

	public String format(int i) {
		return this.formatter.format(i);
	}

	@Override
	public boolean equals(Object object) {
		return this == object || object instanceof Stat && Objects.equals(this.getName(), ((Stat)object).getName());
	}

	@Override
	public int hashCode() {
		return this.getName().hashCode();
	}

	@Override
	public String toString() {
		return "Stat{name=" + this.getName() + ", formatter=" + this.formatter + "}";
	}
}
