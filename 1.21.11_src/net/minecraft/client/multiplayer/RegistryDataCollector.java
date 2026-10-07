package net.minecraft.client.multiplayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.tags.TagLoader;
import net.minecraft.tags.TagNetworkSerialization;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class RegistryDataCollector {
	private RegistryDataCollector.@Nullable ContentsCollector contentsCollector;
	private RegistryDataCollector.@Nullable TagCollector tagCollector;

	public void appendContents(ResourceKey<? extends Registry<?>> resourceKey, List<RegistrySynchronization.PackedRegistryEntry> list) {
		if (this.contentsCollector == null) {
			this.contentsCollector = new RegistryDataCollector.ContentsCollector();
		}

		this.contentsCollector.append(resourceKey, list);
	}

	public void appendTags(Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> map) {
		if (this.tagCollector == null) {
			this.tagCollector = new RegistryDataCollector.TagCollector();
		}

		map.forEach(this.tagCollector::append);
	}

	private static <T> Registry.PendingTags<T> resolveRegistryTags(
		RegistryAccess.Frozen frozen, ResourceKey<? extends Registry<? extends T>> resourceKey, TagNetworkSerialization.NetworkPayload networkPayload
	) {
		Registry<T> registry = frozen.lookupOrThrow(resourceKey);
		return registry.prepareTagReload(networkPayload.resolve(registry));
	}

	private RegistryAccess loadNewElementsAndTags(ResourceProvider resourceProvider, RegistryDataCollector.ContentsCollector contentsCollector, boolean bl) {
		LayeredRegistryAccess<ClientRegistryLayer> layeredRegistryAccess = ClientRegistryLayer.createRegistryAccess();
		RegistryAccess.Frozen frozen = layeredRegistryAccess.getAccessForLoading(ClientRegistryLayer.REMOTE);
		Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> map = new HashMap<>();
		contentsCollector.elements
			.forEach(
				(resourceKey, listx) -> map.put(
					(ResourceKey<? extends Registry<?>>)resourceKey, new RegistryDataLoader.NetworkedRegistryData(listx, TagNetworkSerialization.NetworkPayload.EMPTY)
				)
			);
		List<Registry.PendingTags<?>> list = new ArrayList<>();
		if (this.tagCollector != null) {
			this.tagCollector.forEach((resourceKey, networkPayload) -> {
				if (!networkPayload.isEmpty()) {
					if (RegistrySynchronization.isNetworkable((ResourceKey<? extends Registry<?>>)resourceKey)) {
						map.compute((ResourceKey<? extends Registry<?>>)resourceKey, (resourceKeyx, networkedRegistryData) -> {
							List<RegistrySynchronization.PackedRegistryEntry> listxx = networkedRegistryData != null ? networkedRegistryData.elements() : List.of();
							return new RegistryDataLoader.NetworkedRegistryData(listxx, networkPayload);
						});
					} else if (!bl) {
						list.add(resolveRegistryTags(frozen, (ResourceKey<? extends Registry<?>>)resourceKey, networkPayload));
					}
				}
			});
		}

		List<HolderLookup.RegistryLookup<?>> list2 = TagLoader.buildUpdatedLookups(frozen, list);

		RegistryAccess.Frozen frozen2;
		try {
			frozen2 = RegistryDataLoader.load(map, resourceProvider, list2, RegistryDataLoader.SYNCHRONIZED_REGISTRIES).freeze();
		} catch (Exception exception) {
			CrashReport crashReport = CrashReport.forThrowable(exception, "Network Registry Load");
			addCrashDetails(crashReport, map, list);
			throw new ReportedException(crashReport);
		}

		RegistryAccess registryAccess = layeredRegistryAccess.replaceFrom(ClientRegistryLayer.REMOTE, frozen2).compositeAccess();
		list.forEach(Registry.PendingTags::apply);
		return registryAccess;
	}

	private static void addCrashDetails(
		CrashReport crashReport, Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> map, List<Registry.PendingTags<?>> list
	) {
		CrashReportCategory crashReportCategory = crashReport.addCategory("Received Elements and Tags");
		crashReportCategory.setDetail(
			"Dynamic Registries",
			() -> map.entrySet()
				.stream()
				.sorted(Comparator.comparing(entry -> entry.getKey().identifier()))
				.map(
					entry -> String.format(
						Locale.ROOT, "\n\t\t%s: elements=%d tags=%d", entry.getKey().identifier(), entry.getValue().elements().size(), entry.getValue().tags().size()
					)
				)
				.collect(Collectors.joining())
		);
		crashReportCategory.setDetail(
			"Static Registries",
			() -> list.stream()
				.sorted(Comparator.comparing(pendingTags -> pendingTags.key().identifier()))
				.map(pendingTags -> String.format(Locale.ROOT, "\n\t\t%s: tags=%d", pendingTags.key().identifier(), pendingTags.size()))
				.collect(Collectors.joining())
		);
	}

	private void loadOnlyTags(RegistryDataCollector.TagCollector tagCollector, RegistryAccess.Frozen frozen, boolean bl) {
		tagCollector.forEach((resourceKey, networkPayload) -> {
			if (bl || RegistrySynchronization.isNetworkable((ResourceKey<? extends Registry<?>>)resourceKey)) {
				resolveRegistryTags(frozen, (ResourceKey<? extends Registry<?>>)resourceKey, networkPayload).apply();
			}
		});
	}

	public RegistryAccess.Frozen collectGameRegistries(ResourceProvider resourceProvider, RegistryAccess.Frozen frozen, boolean bl) {
		RegistryAccess registryAccess;
		if (this.contentsCollector != null) {
			registryAccess = this.loadNewElementsAndTags(resourceProvider, this.contentsCollector, bl);
		} else {
			if (this.tagCollector != null) {
				this.loadOnlyTags(this.tagCollector, frozen, !bl);
			}

			registryAccess = frozen;
		}

		return registryAccess.freeze();
	}

	@Environment(EnvType.CLIENT)
	static class ContentsCollector {
		final Map<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> elements = new HashMap<>();

		public void append(ResourceKey<? extends Registry<?>> resourceKey, List<RegistrySynchronization.PackedRegistryEntry> list) {
			this.elements.computeIfAbsent(resourceKey, resourceKeyx -> new ArrayList<>()).addAll(list);
		}
	}

	@Environment(EnvType.CLIENT)
	static class TagCollector {
		private final Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags = new HashMap<>();

		public void append(ResourceKey<? extends Registry<?>> resourceKey, TagNetworkSerialization.NetworkPayload networkPayload) {
			this.tags.put(resourceKey, networkPayload);
		}

		public void forEach(BiConsumer<? super ResourceKey<? extends Registry<?>>, ? super TagNetworkSerialization.NetworkPayload> biConsumer) {
			this.tags.forEach(biConsumer);
		}
	}
}
