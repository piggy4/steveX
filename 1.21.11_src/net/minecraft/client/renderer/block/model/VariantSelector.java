package net.minecraft.client.renderer.block.model;

import com.google.common.base.Splitter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class VariantSelector {
	private static final Splitter COMMA_SPLITTER = Splitter.on(',');
	private static final Splitter EQUAL_SPLITTER = Splitter.on('=').limit(2);

	public static <O, S extends StateHolder<O, S>> Predicate<StateHolder<O, S>> predicate(StateDefinition<O, S> stateDefinition, String string) {
		Map<Property<?>, Comparable<?>> map = new HashMap<>();

		for (String string2 : COMMA_SPLITTER.split(string)) {
			Iterator<String> iterator = EQUAL_SPLITTER.split(string2).iterator();
			if (iterator.hasNext()) {
				String string3 = iterator.next();
				Property<?> property = stateDefinition.getProperty(string3);
				if (property != null && iterator.hasNext()) {
					String string4 = iterator.next();
					Comparable<?> comparable = getValueHelper((Property<Comparable<?>>)property, string4);
					if (comparable == null) {
						throw new RuntimeException("Unknown value: '" + string4 + "' for blockstate property: '" + string3 + "' " + property.getPossibleValues());
					}

					map.put(property, comparable);
				} else if (!string3.isEmpty()) {
					throw new RuntimeException("Unknown blockstate property: '" + string3 + "'");
				}
			}
		}

		return stateHolder -> {
			for (Entry<Property<?>, Comparable<?>> entry : map.entrySet()) {
				if (!Objects.equals(stateHolder.getValue(entry.getKey()), entry.getValue())) {
					return false;
				}
			}

			return true;
		};
	}

	private static <T extends Comparable<T>> @Nullable T getValueHelper(Property<T> property, String string) {
		return property.getValue(string).orElse(null);
	}
}
