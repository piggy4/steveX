package net.minecraft.data.info;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

public class RegistryDumpReport implements DataProvider {
	private final PackOutput output;

	public RegistryDumpReport(PackOutput packOutput) {
		this.output = packOutput;
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cachedOutput) {
		JsonObject jsonObject = new JsonObject();
		BuiltInRegistries.REGISTRY
			.listElements()
			.forEach(reference -> jsonObject.add(reference.key().identifier().toString(), dumpRegistry((Registry<?>)reference.value())));
		Path path = this.output.getOutputFolder(PackOutput.Target.REPORTS).resolve("registries.json");
		return DataProvider.saveStable(cachedOutput, jsonObject, path);
	}

	private static <T> JsonElement dumpRegistry(Registry<T> registry) {
		JsonObject jsonObject = new JsonObject();
		if (registry instanceof DefaultedRegistry) {
			Identifier identifier = ((DefaultedRegistry)registry).getDefaultKey();
			jsonObject.addProperty("default", identifier.toString());
		}

		int i = BuiltInRegistries.REGISTRY.getId(registry);
		jsonObject.addProperty("protocol_id", i);
		JsonObject jsonObject2 = new JsonObject();
		registry.listElements().forEach(reference -> {
			T object = reference.value();
			int ix = registry.getId(object);
			JsonObject jsonObject2x = new JsonObject();
			jsonObject2x.addProperty("protocol_id", ix);
			jsonObject2.add(reference.key().identifier().toString(), jsonObject2x);
		});
		jsonObject.add("entries", jsonObject2);
		return jsonObject;
	}

	@Override
	public String getName() {
		return "Registry Dump";
	}
}
