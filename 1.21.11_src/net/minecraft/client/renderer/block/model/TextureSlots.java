package net.minecraft.client.renderer.block.model;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class TextureSlots {
	public static final TextureSlots EMPTY = new TextureSlots(Map.of());
	private static final char REFERENCE_CHAR = '#';
	private final Map<String, Material> resolvedValues;

	TextureSlots(Map<String, Material> map) {
		this.resolvedValues = map;
	}

	public @Nullable Material getMaterial(String string) {
		if (isTextureReference(string)) {
			string = string.substring(1);
		}

		return this.resolvedValues.get(string);
	}

	private static boolean isTextureReference(String string) {
		return string.charAt(0) == '#';
	}

	public static TextureSlots.Data parseTextureMap(JsonObject jsonObject) {
		TextureSlots.Data.Builder builder = new TextureSlots.Data.Builder();

		for (Entry<String, JsonElement> entry : jsonObject.entrySet()) {
			parseEntry(entry.getKey(), entry.getValue().getAsString(), builder);
		}

		return builder.build();
	}

	private static void parseEntry(String string, String string2, TextureSlots.Data.Builder builder) {
		if (isTextureReference(string2)) {
			builder.addReference(string, string2.substring(1));
		} else {
			Identifier identifier = Identifier.tryParse(string2);
			if (identifier == null) {
				throw new JsonParseException(string2 + " is not valid resource location");
			}

			builder.addTexture(string, new Material(ModelManager.BLOCK_OR_ITEM, identifier));
		}
	}

	@Environment(EnvType.CLIENT)
	public record Data(Map<String, TextureSlots.SlotContents> values) {
		public static final TextureSlots.Data EMPTY = new TextureSlots.Data(Map.of());

		@Environment(EnvType.CLIENT)
		public static class Builder {
			private final Map<String, TextureSlots.SlotContents> textureMap = new HashMap<>();

			public TextureSlots.Data.Builder addReference(String string, String string2) {
				this.textureMap.put(string, new TextureSlots.Reference(string2));
				return this;
			}

			public TextureSlots.Data.Builder addTexture(String string, Material material) {
				this.textureMap.put(string, new TextureSlots.Value(material));
				return this;
			}

			public TextureSlots.Data build() {
				return this.textureMap.isEmpty() ? TextureSlots.Data.EMPTY : new TextureSlots.Data(Map.copyOf(this.textureMap));
			}
		}
	}

	@Environment(EnvType.CLIENT)
	record Reference(String target) implements TextureSlots.SlotContents {
	}

	@Environment(EnvType.CLIENT)
	public static class Resolver {
		private static final Logger LOGGER = LogUtils.getLogger();
		private final List<TextureSlots.Data> entries = new ArrayList<>();

		public TextureSlots.Resolver addLast(TextureSlots.Data data) {
			this.entries.addLast(data);
			return this;
		}

		public TextureSlots.Resolver addFirst(TextureSlots.Data data) {
			this.entries.addFirst(data);
			return this;
		}

		public TextureSlots resolve(ModelDebugName modelDebugName) {
			if (this.entries.isEmpty()) {
				return TextureSlots.EMPTY;
			}

			Object2ObjectMap<String, Material> object2ObjectMap = new Object2ObjectArrayMap<>();
			Object2ObjectMap<String, TextureSlots.Reference> object2ObjectMap2 = new Object2ObjectArrayMap<>();

			for (TextureSlots.Data data : Lists.reverse(this.entries)) {
				data.values.forEach((string, slotContents) -> {
					switch (slotContents) {
						case TextureSlots.Value value:
							object2ObjectMap2.remove(string);
							object2ObjectMap.put(string, value.material());
							break;
						case TextureSlots.Reference reference:
							object2ObjectMap.remove(string);
							object2ObjectMap2.put(string, reference);
							break;
						default:
							throw new MatchException(null, null);
					}
				});
			}

			if (object2ObjectMap2.isEmpty()) {
				return new TextureSlots(object2ObjectMap);
			}

			boolean bl = true;

			while (bl) {
				bl = false;
				ObjectIterator<it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry<String, TextureSlots.Reference>> objectIterator = Object2ObjectMaps.fastIterator(
					object2ObjectMap2
				);

				while (objectIterator.hasNext()) {
					it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry<String, TextureSlots.Reference> entry = objectIterator.next();
					Material material = object2ObjectMap.get(entry.getValue().target);
					if (material != null) {
						object2ObjectMap.put(entry.getKey(), material);
						objectIterator.remove();
						bl = true;
					}
				}
			}

			if (!object2ObjectMap2.isEmpty()) {
				LOGGER.warn(
					"Unresolved texture references in {}:\n{}",
					modelDebugName.debugName(),
					object2ObjectMap2.entrySet()
						.stream()
						.map(entryx -> "\t#" + (String)entryx.getKey() + "-> #" + ((TextureSlots.Reference)entryx.getValue()).target + "\n")
						.collect(Collectors.joining())
				);
			}

			return new TextureSlots(object2ObjectMap);
		}
	}

	@Environment(EnvType.CLIENT)
	public sealed interface SlotContents permits TextureSlots.Value, TextureSlots.Reference {
	}

	@Environment(EnvType.CLIENT)
	record Value(Material material) implements TextureSlots.SlotContents {
	}
}
