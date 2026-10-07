package net.minecraft.client.resources.model;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.PlaceholderLookupProvider;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ClientItemInfoLoader {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final FileToIdConverter LISTER = FileToIdConverter.json("items");

	public static CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> scheduleLoad(ResourceManager resourceManager, Executor executor) {
		RegistryAccess.Frozen frozen = ClientRegistryLayer.createRegistryAccess().compositeAccess();
		return CompletableFuture.<Map<Identifier, Resource>>supplyAsync(() -> LISTER.listMatchingResources(resourceManager), executor)
			.thenCompose(
				map -> {
					List<CompletableFuture<ClientItemInfoLoader.PendingLoad>> list = new ArrayList<>(map.size());
					map.forEach(
						(identifier, resource) -> list.add(
							CompletableFuture.supplyAsync(
								() -> {
									Identifier identifier2 = LISTER.fileToId(identifier);

									try (Reader reader = resource.openAsReader()) {
										PlaceholderLookupProvider placeholderLookupProvider = new PlaceholderLookupProvider(frozen);
										DynamicOps<JsonElement> dynamicOps = placeholderLookupProvider.createSerializationContext(JsonOps.INSTANCE);
										ClientItem clientItem = ClientItem.CODEC
											.parse(dynamicOps, StrictJsonParser.parse(reader))
											.ifError(error -> LOGGER.error("Couldn't parse item model '{}' from pack '{}': {}", identifier2, resource.sourcePackId(), error.message()))
											.result()
											.map(
												clientItemx -> placeholderLookupProvider.hasRegisteredPlaceholders()
													? clientItemx.withRegistrySwapper(placeholderLookupProvider.createSwapper())
													: clientItemx
											)
											.orElse(null);
										return new ClientItemInfoLoader.PendingLoad(identifier2, clientItem);
									} catch (Exception exception) {
										LOGGER.error("Failed to open item model {} from pack '{}'", identifier, resource.sourcePackId(), exception);
										return new ClientItemInfoLoader.PendingLoad(identifier2, null);
									}
								},
								executor
							)
						)
					);
					return Util.sequence(list).thenApply(listx -> {
						Map<Identifier, ClientItem> mapx = new HashMap<>();

						for (ClientItemInfoLoader.PendingLoad pendingLoad : listx) {
							if (pendingLoad.clientItemInfo != null) {
								mapx.put(pendingLoad.id, pendingLoad.clientItemInfo);
							}
						}

						return new ClientItemInfoLoader.LoadedClientInfos(mapx);
					});
				}
			);
	}

	@Environment(EnvType.CLIENT)
	public record LoadedClientInfos(Map<Identifier, ClientItem> contents) {
	}

	@Environment(EnvType.CLIENT)
	record PendingLoad(Identifier id, @Nullable ClientItem clientItemInfo) {
	}
}
