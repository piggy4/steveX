package net.minecraft.world.attribute;

import com.google.common.collect.Sets;
import java.util.Set;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.modifier.ColorModifier;
import net.minecraft.world.attribute.modifier.FloatModifier;
import net.minecraft.world.attribute.modifier.FloatWithAlpha;
import net.minecraft.world.level.Level;
import net.minecraft.world.timeline.Timelines;

public class WeatherAttributes {
	public static final EnvironmentAttributeMap RAIN = EnvironmentAttributeMap.builder()
		.modify(EnvironmentAttributes.SKY_COLOR, ColorModifier.BLEND_TO_GRAY, new ColorModifier.BlendToGray(0.6F, 0.75F))
		.modify(EnvironmentAttributes.FOG_COLOR, ColorModifier.MULTIPLY_RGB, ARGB.colorFromFloat(1.0F, 0.5F, 0.5F, 0.6F))
		.modify(EnvironmentAttributes.CLOUD_COLOR, ColorModifier.BLEND_TO_GRAY, new ColorModifier.BlendToGray(0.24F, 0.5F))
		.modify(EnvironmentAttributes.SKY_LIGHT_LEVEL, FloatModifier.ALPHA_BLEND, new FloatWithAlpha(4.0F, 0.3125F))
		.modify(EnvironmentAttributes.SKY_LIGHT_COLOR, ColorModifier.ALPHA_BLEND, ARGB.color(0.3125F, Timelines.NIGHT_SKY_LIGHT_COLOR))
		.modify(EnvironmentAttributes.SKY_LIGHT_FACTOR, FloatModifier.ALPHA_BLEND, new FloatWithAlpha(0.24F, 0.3125F))
		.set(EnvironmentAttributes.STAR_BRIGHTNESS, 0.0F)
		.modify(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, ColorModifier.MULTIPLY_ARGB, ARGB.colorFromFloat(1.0F, 0.5F, 0.5F, 0.6F))
		.set(EnvironmentAttributes.BEES_STAY_IN_HIVE, true)
		.build();
	public static final EnvironmentAttributeMap THUNDER = EnvironmentAttributeMap.builder()
		.modify(EnvironmentAttributes.SKY_COLOR, ColorModifier.BLEND_TO_GRAY, new ColorModifier.BlendToGray(0.24F, 0.94F))
		.modify(EnvironmentAttributes.FOG_COLOR, ColorModifier.MULTIPLY_RGB, ARGB.colorFromFloat(1.0F, 0.25F, 0.25F, 0.3F))
		.modify(EnvironmentAttributes.CLOUD_COLOR, ColorModifier.BLEND_TO_GRAY, new ColorModifier.BlendToGray(0.095F, 0.94F))
		.modify(EnvironmentAttributes.SKY_LIGHT_LEVEL, FloatModifier.ALPHA_BLEND, new FloatWithAlpha(4.0F, 0.52734375F))
		.modify(EnvironmentAttributes.SKY_LIGHT_COLOR, ColorModifier.ALPHA_BLEND, ARGB.color(0.52734375F, Timelines.NIGHT_SKY_LIGHT_COLOR))
		.modify(EnvironmentAttributes.SKY_LIGHT_FACTOR, FloatModifier.ALPHA_BLEND, new FloatWithAlpha(0.24F, 0.52734375F))
		.set(EnvironmentAttributes.STAR_BRIGHTNESS, 0.0F)
		.modify(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, ColorModifier.MULTIPLY_ARGB, ARGB.colorFromFloat(1.0F, 0.25F, 0.25F, 0.3F))
		.set(EnvironmentAttributes.BEES_STAY_IN_HIVE, true)
		.build();
	private static final Set<EnvironmentAttribute<?>> WEATHER_ATTRIBUTES = Sets.union(RAIN.keySet(), THUNDER.keySet());

	public static void addBuiltinLayers(EnvironmentAttributeSystem.Builder builder, WeatherAttributes.WeatherAccess weatherAccess) {
		for (EnvironmentAttribute<?> environmentAttribute : WEATHER_ATTRIBUTES) {
			addLayer(builder, weatherAccess, environmentAttribute);
		}
	}

	private static <Value> void addLayer(
		EnvironmentAttributeSystem.Builder builder, WeatherAttributes.WeatherAccess weatherAccess, EnvironmentAttribute<Value> environmentAttribute
	) {
		EnvironmentAttributeMap.Entry<Value, ?> entry = RAIN.get(environmentAttribute);
		EnvironmentAttributeMap.Entry<Value, ?> entry2 = THUNDER.get(environmentAttribute);
		builder.addTimeBasedLayer(environmentAttribute, (object, i) -> {
			float f = weatherAccess.thunderLevel();
			float g = weatherAccess.rainLevel() - f;
			if (entry != null && g > 0.0F) {
				Value object2 = entry.applyModifier(object);
				object = environmentAttribute.type().stateChangeLerp().apply(g, object, object2);
			}

			if (entry2 != null && f > 0.0F) {
				Value object2 = entry2.applyModifier(object);
				object = environmentAttribute.type().stateChangeLerp().apply(f, object, object2);
			}

			return object;
		});
	}

	public interface WeatherAccess {
		static WeatherAttributes.WeatherAccess from(Level level) {
			return new WeatherAttributes.WeatherAccess() {
				@Override
				public float rainLevel() {
					return level.getRainLevel(1.0F);
				}

				@Override
				public float thunderLevel() {
					return level.getThunderLevel(1.0F);
				}
			};
		}

		float rainLevel();

		float thunderLevel();
	}
}
