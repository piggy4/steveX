package net.minecraft.server.jsonrpc.internalapi;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.server.dedicated.DedicatedServer;

public class MinecraftExecutorServiceImpl implements MinecraftExecutorService {
	private final DedicatedServer server;

	public MinecraftExecutorServiceImpl(DedicatedServer dedicatedServer) {
		this.server = dedicatedServer;
	}

	@Override
	public <V> CompletableFuture<V> submit(Supplier<V> supplier) {
		return this.server.submit(supplier);
	}

	@Override
	public CompletableFuture<Void> submit(Runnable runnable) {
		return this.server.submit(runnable);
	}
}
