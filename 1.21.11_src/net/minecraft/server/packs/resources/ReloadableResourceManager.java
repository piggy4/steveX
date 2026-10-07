package net.minecraft.server.packs.resources;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.Unit;
import org.slf4j.Logger;

public class ReloadableResourceManager implements ResourceManager, AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	private CloseableResourceManager resources;
	private final List<PreparableReloadListener> listeners = Lists.newArrayList();
	private final PackType type;

	public ReloadableResourceManager(PackType packType) {
		this.type = packType;
		this.resources = new MultiPackResourceManager(packType, List.of());
	}

	@Override
	public void close() {
		this.resources.close();
	}

	public void registerReloadListener(PreparableReloadListener preparableReloadListener) {
		this.listeners.add(preparableReloadListener);
	}

	public ReloadInstance createReload(Executor executor, Executor executor2, CompletableFuture<Unit> completableFuture, List<PackResources> list) {
		LOGGER.info("Reloading ResourceManager: {}", LogUtils.defer(() -> list.stream().map(PackResources::packId).collect(Collectors.joining(", "))));
		this.resources.close();
		this.resources = new MultiPackResourceManager(this.type, list);
		return SimpleReloadInstance.create(this.resources, this.listeners, executor, executor2, completableFuture, LOGGER.isDebugEnabled());
	}

	@Override
	public Optional<Resource> getResource(Identifier identifier) {
		return this.resources.getResource(identifier);
	}

	@Override
	public Set<String> getNamespaces() {
		return this.resources.getNamespaces();
	}

	@Override
	public List<Resource> getResourceStack(Identifier identifier) {
		return this.resources.getResourceStack(identifier);
	}

	@Override
	public Map<Identifier, Resource> listResources(String string, Predicate<Identifier> predicate) {
		return this.resources.listResources(string, predicate);
	}

	@Override
	public Map<Identifier, List<Resource>> listResourceStacks(String string, Predicate<Identifier> predicate) {
		return this.resources.listResourceStacks(string, predicate);
	}

	@Override
	public Stream<PackResources> listPacks() {
		return this.resources.listPacks();
	}
}
