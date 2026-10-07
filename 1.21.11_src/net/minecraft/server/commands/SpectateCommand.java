package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public class SpectateCommand {
	private static final SimpleCommandExceptionType ERROR_SELF = new SimpleCommandExceptionType(Component.translatable("commands.spectate.self"));
	private static final DynamicCommandExceptionType ERROR_NOT_SPECTATOR = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("commands.spectate.not_spectator", object)
	);
	private static final DynamicCommandExceptionType ERROR_CANNOT_SPECTATE = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("commands.spectate.cannot_spectate", object)
	);

	public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
		commandDispatcher.register(
			Commands.literal("spectate")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(commandContext -> spectate(commandContext.getSource(), null, commandContext.getSource().getPlayerOrException()))
				.then(
					Commands.argument("target", EntityArgument.entity())
						.executes(
							commandContext -> spectate(
								commandContext.getSource(), EntityArgument.getEntity(commandContext, "target"), commandContext.getSource().getPlayerOrException()
							)
						)
						.then(
							Commands.argument("player", EntityArgument.player())
								.executes(
									commandContext -> spectate(
										commandContext.getSource(), EntityArgument.getEntity(commandContext, "target"), EntityArgument.getPlayer(commandContext, "player")
									)
								)
						)
				)
		);
	}

	private static int spectate(CommandSourceStack commandSourceStack, @Nullable Entity entity, ServerPlayer serverPlayer) throws CommandSyntaxException {
		if (serverPlayer == entity) {
			throw ERROR_SELF.create();
		}

		if (!serverPlayer.isSpectator()) {
			throw ERROR_NOT_SPECTATOR.create(serverPlayer.getDisplayName());
		}

		if (entity != null && entity.getType().clientTrackingRange() == 0) {
			throw ERROR_CANNOT_SPECTATE.create(entity.getDisplayName());
		}

		serverPlayer.setCamera(entity);
		if (entity != null) {
			commandSourceStack.sendSuccess(() -> Component.translatable("commands.spectate.success.started", entity.getDisplayName()), false);
		} else {
			commandSourceStack.sendSuccess(() -> Component.translatable("commands.spectate.success.stopped"), false);
		}

		return 1;
	}
}
