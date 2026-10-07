package net.minecraft.server.packs.resources;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class ProfiledReloadInstance extends SimpleReloadInstance<ProfiledReloadInstance.State> {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Stopwatch total = Stopwatch.createUnstarted();

	public static ReloadInstance of(
		ResourceManager resourceManager, List<PreparableReloadListener> list, Executor executor, Executor executor2, CompletableFuture<Unit> completableFuture
	) {
		ProfiledReloadInstance profiledReloadInstance = new ProfiledReloadInstance(list);
		profiledReloadInstance.startTasks(
			executor,
			executor2,
			resourceManager,
			list,
			(sharedState, preparationBarrier, preparableReloadListener, executor2x, executor3) -> {
				AtomicLong atomicLong = new AtomicLong();
				AtomicLong atomicLong2 = new AtomicLong();
				AtomicLong atomicLong3 = new AtomicLong();
				AtomicLong atomicLong4 = new AtomicLong();
				CompletableFuture<Void> completableFuturex = preparableReloadListener.reload(
					sharedState,
					profiledExecutor(executor2x, atomicLong, atomicLong2, preparableReloadListener.getName()),
					preparationBarrier,
					profiledExecutor(executor3, atomicLong3, atomicLong4, preparableReloadListener.getName())
				);
				return completableFuturex.thenApplyAsync(void_ -> {
					LOGGER.debug("Finished reloading {}", preparableReloadListener.getName());
					return new ProfiledReloadInstance.State(preparableReloadListener.getName(), atomicLong, atomicLong2, atomicLong3, atomicLong4);
				}, executor2);
			},
			completableFuture
		);
		return profiledReloadInstance;
	}

	private ProfiledReloadInstance(List<PreparableReloadListener> list) {
		super(list);
		this.total.start();
	}

	@Override
	protected CompletableFuture<List<ProfiledReloadInstance.State>> prepareTasks(
		Executor executor,
		Executor executor2,
		ResourceManager resourceManager,
		List<PreparableReloadListener> list,
		SimpleReloadInstance.StateFactory<ProfiledReloadInstance.State> stateFactory,
		CompletableFuture<?> completableFuture
	) {
		return super.prepareTasks(executor, executor2, resourceManager, list, stateFactory, completableFuture).thenApplyAsync(this::finish, executor2);
	}

	private static Executor profiledExecutor(Executor executor, AtomicLong atomicLong, AtomicLong atomicLong2, String string) {
		return runnable -> executor.execute(() -> {
			ProfilerFiller profilerFiller = Profiler.get();
			profilerFiller.push(string);
			long l = Util.getNanos();
			runnable.run();
			atomicLong.addAndGet(Util.getNanos() - l);
			atomicLong2.incrementAndGet();
			profilerFiller.pop();
		});
	}

	private List<ProfiledReloadInstance.State> finish(List<ProfiledReloadInstance.State> list) {
		this.total.stop();
		long l = 0L;
		LOGGER.info("Resource reload finished after {} ms", this.total.elapsed(TimeUnit.MILLISECONDS));

		for (ProfiledReloadInstance.State state : list) {
			long m = TimeUnit.NANOSECONDS.toMillis(state.preparationNanos.get());
			long n = state.preparationCount.get();
			long o = TimeUnit.NANOSECONDS.toMillis(state.reloadNanos.get());
			long p = state.reloadCount.get();
			long q = m + o;
			long r = n + p;
			String string = state.name;
			LOGGER.info("{} took approximately {} tasks/{} ms ({} tasks/{} ms preparing, {} tasks/{} ms applying)", string, r, q, n, m, p, o);
			l += o;
		}

		LOGGER.info("Total blocking time: {} ms", l);
		return list;
	}

	public record State(String name, AtomicLong preparationNanos, AtomicLong preparationCount, AtomicLong reloadNanos, AtomicLong reloadCount) {
	}
}
