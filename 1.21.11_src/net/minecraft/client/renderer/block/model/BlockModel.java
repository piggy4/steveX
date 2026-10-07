package net.minecraft.client.renderer.block.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.model.UnbakedGeometry;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record BlockModel(
	@Nullable UnbakedGeometry geometry,
	UnbakedModel.@Nullable GuiLight guiLight,
	@Nullable Boolean ambientOcclusion,
	@Nullable ItemTransforms transforms,
	TextureSlots.Data textureSlots,
	@Nullable Identifier parent
) implements UnbakedModel {
	@VisibleForTesting
	static final Gson GSON = new GsonBuilder()
		.registerTypeAdapter(BlockModel.class, new BlockModel.Deserializer())
		.registerTypeAdapter(BlockElement.class, new BlockElement.Deserializer())
		.registerTypeAdapter(BlockElementFace.class, new BlockElementFace.Deserializer())
		.registerTypeAdapter(ItemTransform.class, new ItemTransform.Deserializer())
		.registerTypeAdapter(ItemTransforms.class, new ItemTransforms.Deserializer())
		.create();

	public static BlockModel fromStream(Reader reader) {
		return GsonHelper.fromJson(GSON, reader, BlockModel.class);
	}

	@Environment(EnvType.CLIENT)
	public static class Deserializer implements JsonDeserializer<BlockModel> {
		public BlockModel deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
			JsonObject jsonObject = jsonElement.getAsJsonObject();
			UnbakedGeometry unbakedGeometry = this.getElements(jsonDeserializationContext, jsonObject);
			String string = this.getParentName(jsonObject);
			TextureSlots.Data data = this.getTextureMap(jsonObject);
			Boolean boolean_ = this.getAmbientOcclusion(jsonObject);
			ItemTransforms itemTransforms = null;
			if (jsonObject.has("display")) {
				JsonObject jsonObject2 = GsonHelper.getAsJsonObject(jsonObject, "display");
				itemTransforms = jsonDeserializationContext.deserialize(jsonObject2, ItemTransforms.class);
			}

			UnbakedModel.GuiLight guiLight = null;
			if (jsonObject.has("gui_light")) {
				guiLight = UnbakedModel.GuiLight.getByName(GsonHelper.getAsString(jsonObject, "gui_light"));
			}

			Identifier identifier = string.isEmpty() ? null : Identifier.parse(string);
			return new BlockModel(unbakedGeometry, guiLight, boolean_, itemTransforms, data, identifier);
		}

		private TextureSlots.Data getTextureMap(JsonObject jsonObject) {
			if (jsonObject.has("textures")) {
				JsonObject jsonObject2 = GsonHelper.getAsJsonObject(jsonObject, "textures");
				return TextureSlots.parseTextureMap(jsonObject2);
			} else {
				return TextureSlots.Data.EMPTY;
			}
		}

		private String getParentName(JsonObject jsonObject) {
			return GsonHelper.getAsString(jsonObject, "parent", "");
		}

		protected @Nullable Boolean getAmbientOcclusion(JsonObject jsonObject) {
			return jsonObject.has("ambientocclusion") ? GsonHelper.getAsBoolean(jsonObject, "ambientocclusion") : null;
		}

		protected @Nullable UnbakedGeometry getElements(JsonDeserializationContext jsonDeserializationContext, JsonObject jsonObject) {
			if (!jsonObject.has("elements")) {
				return null;
			}

			List<BlockElement> list = new ArrayList<>();

			for (JsonElement jsonElement : GsonHelper.getAsJsonArray(jsonObject, "elements")) {
				list.add(jsonDeserializationContext.deserialize(jsonElement, BlockElement.class));
			}

			return new SimpleUnbakedGeometry(list);
		}
	}
}
