package net.minecraft.world.level.biome;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import net.minecraft.world.level.DryFoliageColor;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

public final class Biome {
	public static final Codec<Biome> DIRECT_CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Biome.ClimateSettings.CODEC.forGetter(biome -> biome.climateSettings),
				EnvironmentAttributeMap.CODEC_ONLY_POSITIONAL.optionalFieldOf("attributes", EnvironmentAttributeMap.EMPTY).forGetter(biome -> biome.attributes),
				BiomeSpecialEffects.CODEC.fieldOf("effects").forGetter(biome -> biome.specialEffects),
				BiomeGenerationSettings.CODEC.forGetter(biome -> biome.generationSettings),
				MobSpawnSettings.CODEC.forGetter(biome -> biome.mobSettings)
			)
			.apply(instance, Biome::new)
	);
	public static final Codec<Biome> NETWORK_CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Biome.ClimateSettings.CODEC.forGetter(biome -> biome.climateSettings),
				EnvironmentAttributeMap.NETWORK_CODEC.optionalFieldOf("attributes", EnvironmentAttributeMap.EMPTY).forGetter(biome -> biome.attributes),
				BiomeSpecialEffects.CODEC.fieldOf("effects").forGetter(biome -> biome.specialEffects)
			)
			.apply(
				instance,
				(climateSettings, environmentAttributeMap, biomeSpecialEffects) -> new Biome(
					climateSettings, environmentAttributeMap, biomeSpecialEffects, BiomeGenerationSettings.EMPTY, MobSpawnSettings.EMPTY
				)
			)
	);
	public static final Codec<Holder<Biome>> CODEC = RegistryFileCodec.create(Registries.BIOME, DIRECT_CODEC);
	public static final Codec<HolderSet<Biome>> LIST_CODEC = RegistryCodecs.homogeneousList(Registries.BIOME, DIRECT_CODEC);
	private static final PerlinSimplexNoise TEMPERATURE_NOISE = new PerlinSimplexNoise(new WorldgenRandom(new LegacyRandomSource(1234L)), ImmutableList.of(0));
	static final PerlinSimplexNoise FROZEN_TEMPERATURE_NOISE = new PerlinSimplexNoise(
		new WorldgenRandom(new LegacyRandomSource(3456L)), ImmutableList.of(-2, -1, 0)
	);
	@Deprecated(forRemoval = true)
	public static final PerlinSimplexNoise BIOME_INFO_NOISE = new PerlinSimplexNoise(new WorldgenRandom(new LegacyRandomSource(2345L)), ImmutableList.of(0));
	private static final int TEMPERATURE_CACHE_SIZE = 1024;
	private final Biome.ClimateSettings climateSettings;
	private final BiomeGenerationSettings generationSettings;
	private final MobSpawnSettings mobSettings;
	private final EnvironmentAttributeMap attributes;
	private final BiomeSpecialEffects specialEffects;
	private final ThreadLocal<Long2FloatLinkedOpenHashMap> temperatureCache = ThreadLocal.withInitial(() -> {
		Long2FloatLinkedOpenHashMap long2FloatLinkedOpenHashMap = new Long2FloatLinkedOpenHashMap(1024, 0.25F) {
			@Override
			protected void rehash(int i) {
			}
		};
		long2FloatLinkedOpenHashMap.defaultReturnValue(Float.NaN);
		return long2FloatLinkedOpenHashMap;
	});

	Biome(
		Biome.ClimateSettings climateSettings,
		EnvironmentAttributeMap environmentAttributeMap,
		BiomeSpecialEffects biomeSpecialEffects,
		BiomeGenerationSettings biomeGenerationSettings,
		MobSpawnSettings mobSpawnSettings
	) {
		this.climateSettings = climateSettings;
		this.generationSettings = biomeGenerationSettings;
		this.mobSettings = mobSpawnSettings;
		this.attributes = environmentAttributeMap;
		this.specialEffects = biomeSpecialEffects;
	}

	public MobSpawnSettings getMobSettings() {
		return this.mobSettings;
	}

	public boolean hasPrecipitation() {
		return this.climateSettings.hasPrecipitation();
	}

	public Biome.Precipitation getPrecipitationAt(BlockPos blockPos, int i) {
		if (!this.hasPrecipitation()) {
			return Biome.Precipitation.NONE;
		} else {
			return this.coldEnoughToSnow(blockPos, i) ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
		}
	}

	private float getHeightAdjustedTemperature(BlockPos blockPos, int i) {
		float f = this.climateSettings.temperatureModifier.modifyTemperature(blockPos, this.getBaseTemperature());
		int j = i + 17;
		if (blockPos.getY() > j) {
			float g = (float)(TEMPERATURE_NOISE.getValue(blockPos.getX() / 8.0F, blockPos.getZ() / 8.0F, false) * 8.0);
			return f - (g + blockPos.getY() - j) * 0.05F / 40.0F;
		} else {
			return f;
		}
	}

	@Deprecated
	private float getTemperature(BlockPos blockPos, int i) {
		long l = blockPos.asLong();
		Long2FloatLinkedOpenHashMap long2FloatLinkedOpenHashMap = this.temperatureCache.get();
		float f = long2FloatLinkedOpenHashMap.get(l);
		if (!Float.isNaN(f)) {
			return f;
		}

		float g = this.getHeightAdjustedTemperature(blockPos, i);
		if (long2FloatLinkedOpenHashMap.size() == 1024) {
			long2FloatLinkedOpenHashMap.removeFirstFloat();
		}

		long2FloatLinkedOpenHashMap.put(l, g);
		return g;
	}

	public boolean shouldFreeze(LevelReader levelReader, BlockPos blockPos) {
		return this.shouldFreeze(levelReader, blockPos, true);
	}

	public boolean shouldFreeze(LevelReader levelReader, BlockPos blockPos, boolean bl) {
		if (this.warmEnoughToRain(blockPos, levelReader.getSeaLevel())) {
			return false;
		}

		if (levelReader.isInsideBuildHeight(blockPos.getY()) && levelReader.getBrightness(LightLayer.BLOCK, blockPos) < 10) {
			BlockState blockState = levelReader.getBlockState(blockPos);
			FluidState fluidState = levelReader.getFluidState(blockPos);
			if (fluidState.getType() == Fluids.WATER && blockState.getBlock() instanceof LiquidBlock) {
				if (!bl) {
					return true;
				}

				boolean bl2 = levelReader.isWaterAt(blockPos.west())
					&& levelReader.isWaterAt(blockPos.east())
					&& levelReader.isWaterAt(blockPos.north())
					&& levelReader.isWaterAt(blockPos.south());
				if (!bl2) {
					return true;
				}
			}
		}

		return false;
	}

	public boolean coldEnoughToSnow(BlockPos blockPos, int i) {
		return !this.warmEnoughToRain(blockPos, i);
	}

	public boolean warmEnoughToRain(BlockPos blockPos, int i) {
		return this.getTemperature(blockPos, i) >= 0.15F;
	}

	public boolean shouldMeltFrozenOceanIcebergSlightly(BlockPos blockPos, int i) {
		return this.getTemperature(blockPos, i) > 0.1F;
	}

	public boolean shouldSnow(LevelReader levelReader, BlockPos blockPos) {
		if (this.getPrecipitationAt(blockPos, levelReader.getSeaLevel()) != Biome.Precipitation.SNOW) {
			return false;
		}

		if (levelReader.isInsideBuildHeight(blockPos.getY()) && levelReader.getBrightness(LightLayer.BLOCK, blockPos) < 10) {
			BlockState blockState = levelReader.getBlockState(blockPos);
			if ((blockState.isAir() || blockState.is(Blocks.SNOW)) && Blocks.SNOW.defaultBlockState().canSurvive(levelReader, blockPos)) {
				return true;
			}
		}

		return false;
	}

	public BiomeGenerationSettings getGenerationSettings() {
		return this.generationSettings;
	}

	public int getGrassColor(double d, double e) {
		int i = this.getBaseGrassColor();
		return this.specialEffects.grassColorModifier().modifyColor(d, e, i);
	}

	private int getBaseGrassColor() {
		Optional<Integer> optional = this.specialEffects.grassColorOverride();
		return optional.isPresent() ? optional.get() : this.getGrassColorFromTexture();
	}

	private int getGrassColorFromTexture() {
		double d = Mth.clamp(this.climateSettings.temperature, 0.0F, 1.0F);
		double e = Mth.clamp(this.climateSettings.downfall, 0.0F, 1.0F);
		return GrassColor.get(d, e);
	}

	public int getFoliageColor() {
		return this.specialEffects.foliageColorOverride().orElseGet(this::getFoliageColorFromTexture);
	}

	private int getFoliageColorFromTexture() {
		double d = Mth.clamp(this.climateSettings.temperature, 0.0F, 1.0F);
		double e = Mth.clamp(this.climateSettings.downfall, 0.0F, 1.0F);
		return FoliageColor.get(d, e);
	}

	public int getDryFoliageColor() {
		return this.specialEffects.dryFoliageColorOverride().orElseGet(this::getDryFoliageColorFromTexture);
	}

	private int getDryFoliageColorFromTexture() {
		double d = Mth.clamp(this.climateSettings.temperature, 0.0F, 1.0F);
		double e = Mth.clamp(this.climateSettings.downfall, 0.0F, 1.0F);
		return DryFoliageColor.get(d, e);
	}

	public float getBaseTemperature() {
		return this.climateSettings.temperature;
	}

	public EnvironmentAttributeMap getAttributes() {
		return this.attributes;
	}

	public BiomeSpecialEffects getSpecialEffects() {
		return this.specialEffects;
	}

	public int getWaterColor() {
		return this.specialEffects.waterColor();
	}

	public static class BiomeBuilder {
		private boolean hasPrecipitation = true;
		private @Nullable Float temperature;
		private Biome.TemperatureModifier temperatureModifier = Biome.TemperatureModifier.NONE;
		private @Nullable Float downfall;
		private final EnvironmentAttributeMap.Builder attributes = EnvironmentAttributeMap.builder();
		private @Nullable BiomeSpecialEffects specialEffects;
		private @Nullable MobSpawnSettings mobSpawnSettings;
		private @Nullable BiomeGenerationSettings generationSettings;

		public Biome.BiomeBuilder hasPrecipitation(boolean bl) {
			this.hasPrecipitation = bl;
			return this;
		}

		public Biome.BiomeBuilder temperature(float f) {
			this.temperature = f;
			return this;
		}

		public Biome.BiomeBuilder downfall(float f) {
			this.downfall = f;
			return this;
		}

		public Biome.BiomeBuilder putAttributes(EnvironmentAttributeMap environmentAttributeMap) {
			this.attributes.putAll(environmentAttributeMap);
			return this;
		}

		public Biome.BiomeBuilder putAttributes(EnvironmentAttributeMap.Builder builder) {
			return this.putAttributes(builder.build());
		}

		public <Value> Biome.BiomeBuilder setAttribute(EnvironmentAttribute<Value> environmentAttribute, Value object) {
			this.attributes.set(environmentAttribute, object);
			return this;
		}

		public <Value, Parameter> Biome.BiomeBuilder modifyAttribute(
			EnvironmentAttribute<Value> environmentAttribute, AttributeModifier<Value, Parameter> attributeModifier, Parameter object
		) {
			this.attributes.modify(environmentAttribute, attributeModifier, object);
			return this;
		}

		public Biome.BiomeBuilder specialEffects(BiomeSpecialEffects biomeSpecialEffects) {
			this.specialEffects = biomeSpecialEffects;
			return this;
		}

		public Biome.BiomeBuilder mobSpawnSettings(MobSpawnSettings mobSpawnSettings) {
			this.mobSpawnSettings = mobSpawnSettings;
			return this;
		}

		public Biome.BiomeBuilder generationSettings(BiomeGenerationSettings biomeGenerationSettings) {
			this.generationSettings = biomeGenerationSettings;
			return this;
		}

		public Biome.BiomeBuilder temperatureAdjustment(Biome.TemperatureModifier temperatureModifier) {
			this.temperatureModifier = temperatureModifier;
			return this;
		}

		public Biome build() {
			if (this.temperature != null && this.downfall != null && this.specialEffects != null && this.mobSpawnSettings != null && this.generationSettings != null) {
				return new Biome(
					new Biome.ClimateSettings(this.hasPrecipitation, this.temperature, this.temperatureModifier, this.downfall),
					this.attributes.build(),
					this.specialEffects,
					this.generationSettings,
					this.mobSpawnSettings
				);
			} else {
				throw new IllegalStateException("You are missing parameters to build a proper biome\n" + this);
			}
		}

		@Override
		public String toString() {
			return "BiomeBuilder{\nhasPrecipitation="
				+ this.hasPrecipitation
				+ ",\ntemperature="
				+ this.temperature
				+ ",\ntemperatureModifier="
				+ this.temperatureModifier
				+ ",\ndownfall="
				+ this.downfall
				+ ",\nspecialEffects="
				+ this.specialEffects
				+ ",\nmobSpawnSettings="
				+ this.mobSpawnSettings
				+ ",\ngenerationSettings="
				+ this.generationSettings
				+ ",\n}";
		}
	}

	record ClimateSettings(boolean hasPrecipitation, float temperature, Biome.TemperatureModifier temperatureModifier, float downfall) {
		public static final MapCodec<Biome.ClimateSettings> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.BOOL.fieldOf("has_precipitation").forGetter(climateSettings -> climateSettings.hasPrecipitation),
					Codec.FLOAT.fieldOf("temperature").forGetter(climateSettings -> climateSettings.temperature),
					Biome.TemperatureModifier.CODEC
						.optionalFieldOf("temperature_modifier", Biome.TemperatureModifier.NONE)
						.forGetter(climateSettings -> climateSettings.temperatureModifier),
					Codec.FLOAT.fieldOf("downfall").forGetter(climateSettings -> climateSettings.downfall)
				)
				.apply(instance, Biome.ClimateSettings::new)
		);
	}

	public enum Precipitation implements StringRepresentable {
		NONE("none"),
		RAIN("rain"),
		SNOW("snow");

		public static final Codec<Biome.Precipitation> CODEC = StringRepresentable.fromEnum(Biome.Precipitation::values);
		private final String name;

		Precipitation(final String string2) {
			this.name = string2;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	public enum TemperatureModifier implements StringRepresentable {
		NONE("none") {
			@Override
			public float modifyTemperature(BlockPos blockPos, float f) {
				return f;
			}
		},
		FROZEN("frozen") {
			@Override
			public float modifyTemperature(BlockPos blockPos, float f) {
				double d = Biome.FROZEN_TEMPERATURE_NOISE.getValue(blockPos.getX() * 0.05, blockPos.getZ() * 0.05, false) * 7.0;
				double e = Biome.BIOME_INFO_NOISE.getValue(blockPos.getX() * 0.2, blockPos.getZ() * 0.2, false);
				double g = d + e;
				if (g < 0.3) {
					double h = Biome.BIOME_INFO_NOISE.getValue(blockPos.getX() * 0.09, blockPos.getZ() * 0.09, false);
					if (h < 0.8) {
						return 0.2F;
					}
				}

				return f;
			}
		};

		private final String name;
		public static final Codec<Biome.TemperatureModifier> CODEC = StringRepresentable.fromEnum(Biome.TemperatureModifier::values);

		public abstract float modifyTemperature(BlockPos blockPos, float f);

		TemperatureModifier(final String string2) {
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
