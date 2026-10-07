package net.minecraft.client.data.models.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class DelegatedModel implements ModelInstance {
	private final Identifier parent;

	public DelegatedModel(Identifier identifier) {
		this.parent = identifier;
	}

	public JsonElement get() {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("parent", this.parent.toString());
		return jsonObject;
	}
}
