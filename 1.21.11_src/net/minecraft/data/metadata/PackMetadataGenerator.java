package net.minecraft.data.metadata;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.DetectedVersion;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FeatureFlagsMetadataSection;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.world.flag.FeatureFlagSet;

public class PackMetadataGenerator implements DataProvider {
	private final PackOutput output;
	private final Map<String, Supplier<JsonElement>> elements = new HashMap<>();

	public PackMetadataGenerator(PackOutput packOutput) {
		this.output = packOutput;
	}

	public <T> PackMetadataGenerator add(MetadataSectionType<T> metadataSectionType, T object) {
		this.elements
			.put(
				metadataSectionType.name(),
				() -> metadataSectionType.codec().encodeStart(JsonOps.INSTANCE, object).getOrThrow(IllegalArgumentException::new).getAsJsonObject()
			);
		return this;
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cachedOutput) {
		JsonObject jsonObject = new JsonObject();
		this.elements.forEach((string, supplier) -> jsonObject.add(string, supplier.get()));
		return DataProvider.saveStable(cachedOutput, jsonObject, this.output.getOutputFolder().resolve("pack.mcmeta"));
	}

	@Override
	public String getName() {
		return "Pack Metadata";
	}

	public static PackMetadataGenerator forFeaturePack(PackOutput packOutput, Component component) {
		return new PackMetadataGenerator(packOutput)
			.add(PackMetadataSection.SERVER_TYPE, new PackMetadataSection(component, DetectedVersion.BUILT_IN.packVersion(PackType.SERVER_DATA).minorRange()));
	}

	public static PackMetadataGenerator forFeaturePack(PackOutput packOutput, Component component, FeatureFlagSet featureFlagSet) {
		return forFeaturePack(packOutput, component).add(FeatureFlagsMetadataSection.TYPE, new FeatureFlagsMetadataSection(featureFlagSet));
	}
}
