package net.minecraft.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

public record BiomeSpecialEffects(
	int waterColor,
	Optional<Integer> foliageColorOverride,
	Optional<Integer> dryFoliageColorOverride,
	Optional<Integer> grassColorOverride,
	BiomeSpecialEffects.GrassColorModifier grassColorModifier
) {
	public static final Codec<BiomeSpecialEffects> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				ExtraCodecs.STRING_RGB_COLOR.fieldOf("water_color").forGetter(BiomeSpecialEffects::waterColor),
				ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("foliage_color").forGetter(BiomeSpecialEffects::foliageColorOverride),
				ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("dry_foliage_color").forGetter(BiomeSpecialEffects::dryFoliageColorOverride),
				ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("grass_color").forGetter(BiomeSpecialEffects::grassColorOverride),
				BiomeSpecialEffects.GrassColorModifier.CODEC
					.optionalFieldOf("grass_color_modifier", BiomeSpecialEffects.GrassColorModifier.NONE)
					.forGetter(BiomeSpecialEffects::grassColorModifier)
			)
			.apply(instance, BiomeSpecialEffects::new)
	);

	public static class Builder {
		private OptionalInt waterColor = OptionalInt.empty();
		private Optional<Integer> foliageColorOverride = Optional.empty();
		private Optional<Integer> dryFoliageColorOverride = Optional.empty();
		private Optional<Integer> grassColorOverride = Optional.empty();
		private BiomeSpecialEffects.GrassColorModifier grassColorModifier = BiomeSpecialEffects.GrassColorModifier.NONE;

		public BiomeSpecialEffects.Builder waterColor(int i) {
			this.waterColor = OptionalInt.of(i);
			return this;
		}

		public BiomeSpecialEffects.Builder foliageColorOverride(int i) {
			this.foliageColorOverride = Optional.of(i);
			return this;
		}

		public BiomeSpecialEffects.Builder dryFoliageColorOverride(int i) {
			this.dryFoliageColorOverride = Optional.of(i);
			return this;
		}

		public BiomeSpecialEffects.Builder grassColorOverride(int i) {
			this.grassColorOverride = Optional.of(i);
			return this;
		}

		public BiomeSpecialEffects.Builder grassColorModifier(BiomeSpecialEffects.GrassColorModifier grassColorModifier) {
			this.grassColorModifier = grassColorModifier;
			return this;
		}

		public BiomeSpecialEffects build() {
			return new BiomeSpecialEffects(
				this.waterColor.orElseThrow(() -> new IllegalStateException("Missing 'water' color.")),
				this.foliageColorOverride,
				this.dryFoliageColorOverride,
				this.grassColorOverride,
				this.grassColorModifier
			);
		}
	}

	public enum GrassColorModifier implements StringRepresentable {
		NONE("none") {
			@Override
			public int modifyColor(double d, double e, int i) {
				return i;
			}
		},
		DARK_FOREST("dark_forest") {
			@Override
			public int modifyColor(double d, double e, int i) {
				return (i & 16711422) + 2634762 >> 1;
			}
		},
		SWAMP("swamp") {
			@Override
			public int modifyColor(double d, double e, int i) {
				double f = Biome.BIOME_INFO_NOISE.getValue(d * 0.0225, e * 0.0225, false);
				return f < -0.1 ? 5011004 : 6975545;
			}
		};

		private final String name;
		public static final Codec<BiomeSpecialEffects.GrassColorModifier> CODEC = StringRepresentable.fromEnum(BiomeSpecialEffects.GrassColorModifier::values);

		public abstract int modifyColor(double d, double e, int i);

		GrassColorModifier(final String string2) {
			this.name = string2;
		}

		public String getName() {
			return this.name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}
}
