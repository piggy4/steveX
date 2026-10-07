package net.minecraft.server.packs;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.GsonHelper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class AbstractPackResources implements PackResources {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final PackLocationInfo location;

	protected AbstractPackResources(PackLocationInfo packLocationInfo) {
		this.location = packLocationInfo;
	}

	@Override
	public <T> @Nullable T getMetadataSection(MetadataSectionType<T> metadataSectionType) throws IOException {
		IoSupplier<InputStream> ioSupplier = this.getRootResource("pack.mcmeta");
		if (ioSupplier == null) {
			return null;
		}

		try (InputStream inputStream = ioSupplier.get()) {
			return getMetadataFromStream(metadataSectionType, inputStream, this.location);
		}
	}

	public static <T> @Nullable T getMetadataFromStream(MetadataSectionType<T> metadataSectionType, InputStream inputStream, PackLocationInfo packLocationInfo) {
		JsonObject jsonObject;
		try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			jsonObject = GsonHelper.parse(bufferedReader);
		} catch (Exception exception) {
			LOGGER.error("Couldn't load {} {} metadata: {}", packLocationInfo.id(), metadataSectionType.name(), exception.getMessage());
			return null;
		}

		return !jsonObject.has(metadataSectionType.name())
			? null
			: metadataSectionType.codec()
				.parse(JsonOps.INSTANCE, jsonObject.get(metadataSectionType.name()))
				.ifError(error -> LOGGER.error("Couldn't load {} {} metadata: {}", packLocationInfo.id(), metadataSectionType.name(), error.message()))
				.result()
				.orElse(null);
	}

	@Override
	public PackLocationInfo location() {
		return this.location;
	}
}
