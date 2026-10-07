package net.minecraft.client.resources.model;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

@Environment(EnvType.CLIENT)
public record EquipmentClientInfo(Map<EquipmentClientInfo.LayerType, List<EquipmentClientInfo.Layer>> layers) {
	private static final Codec<List<EquipmentClientInfo.Layer>> LAYER_LIST_CODEC = ExtraCodecs.nonEmptyList(EquipmentClientInfo.Layer.CODEC.listOf());
	public static final Codec<EquipmentClientInfo> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				ExtraCodecs.nonEmptyMap(Codec.unboundedMap(EquipmentClientInfo.LayerType.CODEC, LAYER_LIST_CODEC)).fieldOf("layers").forGetter(EquipmentClientInfo::layers)
			)
			.apply(instance, EquipmentClientInfo::new)
	);

	public static EquipmentClientInfo.Builder builder() {
		return new EquipmentClientInfo.Builder();
	}

	public List<EquipmentClientInfo.Layer> getLayers(EquipmentClientInfo.LayerType layerType) {
		return this.layers.getOrDefault(layerType, List.of());
	}

	@Environment(EnvType.CLIENT)
	public static class Builder {
		private final Map<EquipmentClientInfo.LayerType, List<EquipmentClientInfo.Layer>> layersByType = new EnumMap<>(EquipmentClientInfo.LayerType.class);

		Builder() {
		}

		public EquipmentClientInfo.Builder addHumanoidLayers(Identifier identifier) {
			return this.addHumanoidLayers(identifier, false);
		}

		public EquipmentClientInfo.Builder addHumanoidLayers(Identifier identifier, boolean bl) {
			this.addLayers(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, EquipmentClientInfo.Layer.leatherDyeable(identifier, bl));
			this.addMainHumanoidLayer(identifier, bl);
			return this;
		}

		public EquipmentClientInfo.Builder addMainHumanoidLayer(Identifier identifier, boolean bl) {
			return this.addLayers(EquipmentClientInfo.LayerType.HUMANOID, EquipmentClientInfo.Layer.leatherDyeable(identifier, bl));
		}

		public EquipmentClientInfo.Builder addLayers(EquipmentClientInfo.LayerType layerType, EquipmentClientInfo.Layer... layers) {
			Collections.addAll(this.layersByType.computeIfAbsent(layerType, layerTypex -> new ArrayList<>()), layers);
			return this;
		}

		public EquipmentClientInfo build() {
			return new EquipmentClientInfo(
				this.layersByType.entrySet().stream().collect(ImmutableMap.toImmutableMap(Entry::getKey, entry -> List.copyOf(entry.getValue())))
			);
		}
	}

	@Environment(EnvType.CLIENT)
	public record Dyeable(Optional<Integer> colorWhenUndyed) {
		public static final Codec<EquipmentClientInfo.Dyeable> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(ExtraCodecs.RGB_COLOR_CODEC.optionalFieldOf("color_when_undyed").forGetter(EquipmentClientInfo.Dyeable::colorWhenUndyed))
				.apply(instance, EquipmentClientInfo.Dyeable::new)
		);
	}

	@Environment(EnvType.CLIENT)
	public record Layer(Identifier textureId, Optional<EquipmentClientInfo.Dyeable> dyeable, boolean usePlayerTexture) {
		public static final Codec<EquipmentClientInfo.Layer> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Identifier.CODEC.fieldOf("texture").forGetter(EquipmentClientInfo.Layer::textureId),
					EquipmentClientInfo.Dyeable.CODEC.optionalFieldOf("dyeable").forGetter(EquipmentClientInfo.Layer::dyeable),
					Codec.BOOL.optionalFieldOf("use_player_texture", false).forGetter(EquipmentClientInfo.Layer::usePlayerTexture)
				)
				.apply(instance, EquipmentClientInfo.Layer::new)
		);

		public Layer(Identifier identifier) {
			this(identifier, Optional.empty(), false);
		}

		public static EquipmentClientInfo.Layer leatherDyeable(Identifier identifier, boolean bl) {
			return new EquipmentClientInfo.Layer(identifier, bl ? Optional.of(new EquipmentClientInfo.Dyeable(Optional.of(-6265536))) : Optional.empty(), false);
		}

		public static EquipmentClientInfo.Layer onlyIfDyed(Identifier identifier, boolean bl) {
			return new EquipmentClientInfo.Layer(identifier, bl ? Optional.of(new EquipmentClientInfo.Dyeable(Optional.empty())) : Optional.empty(), false);
		}

		public Identifier getTextureLocation(EquipmentClientInfo.LayerType layerType) {
			return this.textureId.withPath(string -> "textures/entity/equipment/" + layerType.getSerializedName() + "/" + string + ".png");
		}
	}

	@Environment(EnvType.CLIENT)
	public enum LayerType implements StringRepresentable {
		HUMANOID("humanoid"),
		HUMANOID_LEGGINGS("humanoid_leggings"),
		WINGS("wings"),
		WOLF_BODY("wolf_body"),
		HORSE_BODY("horse_body"),
		LLAMA_BODY("llama_body"),
		PIG_SADDLE("pig_saddle"),
		STRIDER_SADDLE("strider_saddle"),
		CAMEL_SADDLE("camel_saddle"),
		CAMEL_HUSK_SADDLE("camel_husk_saddle"),
		HORSE_SADDLE("horse_saddle"),
		DONKEY_SADDLE("donkey_saddle"),
		MULE_SADDLE("mule_saddle"),
		ZOMBIE_HORSE_SADDLE("zombie_horse_saddle"),
		SKELETON_HORSE_SADDLE("skeleton_horse_saddle"),
		HAPPY_GHAST_BODY("happy_ghast_body"),
		NAUTILUS_SADDLE("nautilus_saddle"),
		NAUTILUS_BODY("nautilus_body");

		public static final Codec<EquipmentClientInfo.LayerType> CODEC = StringRepresentable.fromEnum(EquipmentClientInfo.LayerType::values);
		private final String id;

		LayerType(final String string2) {
			this.id = string2;
		}

		@Override
		public String getSerializedName() {
			return this.id;
		}

		public String trimAssetPrefix() {
			return "trims/entity/" + this.id;
		}
	}
}
