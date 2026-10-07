package net.minecraft.server;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class ServerFunctionManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Identifier TICK_FUNCTION_TAG = Identifier.withDefaultNamespace("tick");
	private static final Identifier LOAD_FUNCTION_TAG = Identifier.withDefaultNamespace("load");
	private final MinecraftServer server;
	private List<CommandFunction<CommandSourceStack>> ticking = ImmutableList.of();
	private boolean postReload;
	private ServerFunctionLibrary library;

	public ServerFunctionManager(MinecraftServer minecraftServer, ServerFunctionLibrary serverFunctionLibrary) {
		this.server = minecraftServer;
		this.library = serverFunctionLibrary;
		this.postReload(serverFunctionLibrary);
	}

	public CommandDispatcher<CommandSourceStack> getDispatcher() {
		return this.server.getCommands().getDispatcher();
	}

	public void tick() {
		if (this.server.tickRateManager().runsNormally()) {
			if (this.postReload) {
				this.postReload = false;
				Collection<CommandFunction<CommandSourceStack>> collection = this.library.getTag(LOAD_FUNCTION_TAG);
				this.executeTagFunctions(collection, LOAD_FUNCTION_TAG);
			}

			this.executeTagFunctions(this.ticking, TICK_FUNCTION_TAG);
		}
	}

	private void executeTagFunctions(Collection<CommandFunction<CommandSourceStack>> collection, Identifier identifier) {
		Profiler.get().push(identifier::toString);

		for (CommandFunction<CommandSourceStack> commandFunction : collection) {
			this.execute(commandFunction, this.getGameLoopSender());
		}

		Profiler.get().pop();
	}

	public void execute(CommandFunction<CommandSourceStack> commandFunction, CommandSourceStack commandSourceStack) {
		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push(() -> "function " + commandFunction.id());

		try {
			InstantiatedFunction<CommandSourceStack> instantiatedFunction = commandFunction.instantiate(null, this.getDispatcher());
			Commands.executeCommandInContext(
				commandSourceStack,
				executionContext -> ExecutionContext.queueInitialFunctionCall(executionContext, instantiatedFunction, commandSourceStack, CommandResultCallback.EMPTY)
			);
		} catch (FunctionInstantiationException var9) {
		} catch (Exception exception) {
			LOGGER.warn("Failed to execute function {}", commandFunction.id(), exception);
		} finally {
			profilerFiller.pop();
		}
	}

	public void replaceLibrary(ServerFunctionLibrary serverFunctionLibrary) {
		this.library = serverFunctionLibrary;
		this.postReload(serverFunctionLibrary);
	}

	private void postReload(ServerFunctionLibrary serverFunctionLibrary) {
		this.ticking = List.copyOf(serverFunctionLibrary.getTag(TICK_FUNCTION_TAG));
		this.postReload = true;
	}

	public CommandSourceStack getGameLoopSender() {
		return this.server.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER).withSuppressedOutput();
	}

	public Optional<CommandFunction<CommandSourceStack>> get(Identifier identifier) {
		return this.library.getFunction(identifier);
	}

	public List<CommandFunction<CommandSourceStack>> getTag(Identifier identifier) {
		return this.library.getTag(identifier);
	}

	public Iterable<Identifier> getFunctionNames() {
		return this.library.getFunctions().keySet();
	}

	public Iterable<Identifier> getTagNames() {
		return this.library.getAvailableTags();
	}
}
