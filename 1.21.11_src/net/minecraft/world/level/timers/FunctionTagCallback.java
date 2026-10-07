package net.minecraft.world.level.timers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;

public record FunctionTagCallback(Identifier tagId) implements TimerCallback<MinecraftServer> {
	public static final MapCodec<FunctionTagCallback> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(Identifier.CODEC.fieldOf("Name").forGetter(FunctionTagCallback::tagId)).apply(instance, FunctionTagCallback::new)
	);

	public void handle(MinecraftServer minecraftServer, TimerQueue<MinecraftServer> timerQueue, long l) {
		ServerFunctionManager serverFunctionManager = minecraftServer.getFunctions();

		for (CommandFunction<CommandSourceStack> commandFunction : serverFunctionManager.getTag(this.tagId)) {
			serverFunctionManager.execute(commandFunction, serverFunctionManager.getGameLoopSender());
		}
	}

	@Override
	public MapCodec<FunctionTagCallback> codec() {
		return CODEC;
	}
}
