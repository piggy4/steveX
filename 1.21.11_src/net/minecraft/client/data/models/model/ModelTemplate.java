package net.minecraft.client.data.models.model;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

@Environment(EnvType.CLIENT)
public class ModelTemplate {
	private final Optional<Identifier> model;
	private final Set<TextureSlot> requiredSlots;
	private final Optional<String> suffix;

	public ModelTemplate(Optional<Identifier> optional, Optional<String> optional2, TextureSlot... textureSlots) {
		this.model = optional;
		this.suffix = optional2;
		this.requiredSlots = ImmutableSet.copyOf(textureSlots);
	}

	public Identifier getDefaultModelLocation(Block block) {
		return ModelLocationUtils.getModelLocation(block, this.suffix.orElse(""));
	}

	public Identifier create(Block block, TextureMapping textureMapping, BiConsumer<Identifier, ModelInstance> biConsumer) {
		return this.create(ModelLocationUtils.getModelLocation(block, this.suffix.orElse("")), textureMapping, biConsumer);
	}

	public Identifier createWithSuffix(Block block, String string, TextureMapping textureMapping, BiConsumer<Identifier, ModelInstance> biConsumer) {
		return this.create(ModelLocationUtils.getModelLocation(block, string + this.suffix.orElse("")), textureMapping, biConsumer);
	}

	public Identifier createWithOverride(Block block, String string, TextureMapping textureMapping, BiConsumer<Identifier, ModelInstance> biConsumer) {
		return this.create(ModelLocationUtils.getModelLocation(block, string), textureMapping, biConsumer);
	}

	public Identifier create(Item item, TextureMapping textureMapping, BiConsumer<Identifier, ModelInstance> biConsumer) {
		return this.create(ModelLocationUtils.getModelLocation(item, this.suffix.orElse("")), textureMapping, biConsumer);
	}

	public Identifier create(Identifier identifier, TextureMapping textureMapping, BiConsumer<Identifier, ModelInstance> biConsumer) {
		Map<TextureSlot, Identifier> map = this.createMap(textureMapping);
		biConsumer.accept(identifier, () -> {
			JsonObject jsonObject = new JsonObject();
			this.model.ifPresent(identifierx -> jsonObject.addProperty("parent", identifierx.toString()));
			if (!map.isEmpty()) {
				JsonObject jsonObject2 = new JsonObject();
				map.forEach((textureSlot, identifierx) -> jsonObject2.addProperty(textureSlot.getId(), identifierx.toString()));
				jsonObject.add("textures", jsonObject2);
			}

			return jsonObject;
		});
		return identifier;
	}

	private Map<TextureSlot, Identifier> createMap(TextureMapping textureMapping) {
		return Streams.concat(this.requiredSlots.stream(), textureMapping.getForced()).collect(ImmutableMap.toImmutableMap(Function.identity(), textureMapping::get));
	}
}
