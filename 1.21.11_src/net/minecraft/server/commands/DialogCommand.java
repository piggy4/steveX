package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;

public class DialogCommand {
	public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher, CommandBuildContext commandBuildContext) {
		commandDispatcher.register(
			Commands.literal("dialog")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(
					Commands.literal("show")
						.then(
							Commands.argument("targets", EntityArgument.players())
								.then(
									Commands.argument("dialog", ResourceOrIdArgument.dialog(commandBuildContext))
										.executes(
											commandContext -> showDialog(
												(CommandSourceStack)commandContext.getSource(),
												EntityArgument.getPlayers(commandContext, "targets"),
												ResourceOrIdArgument.getDialog(commandContext, "dialog")
											)
										)
								)
						)
				)
				.then(
					Commands.literal("clear")
						.then(
							Commands.argument("targets", EntityArgument.players())
								.executes(commandContext -> clearDialog(commandContext.getSource(), EntityArgument.getPlayers(commandContext, "targets")))
						)
				)
		);
	}

	private static int showDialog(CommandSourceStack commandSourceStack, Collection<ServerPlayer> collection, Holder<Dialog> holder) {
		for (ServerPlayer serverPlayer : collection) {
			serverPlayer.openDialog(holder);
		}

		if (collection.size() == 1) {
			commandSourceStack.sendSuccess(() -> Component.translatable("commands.dialog.show.single", collection.iterator().next().getDisplayName()), true);
		} else {
			commandSourceStack.sendSuccess(() -> Component.translatable("commands.dialog.show.multiple", collection.size()), true);
		}

		return collection.size();
	}

	private static int clearDialog(CommandSourceStack commandSourceStack, Collection<ServerPlayer> collection) {
		for (ServerPlayer serverPlayer : collection) {
			serverPlayer.connection.send(ClientboundClearDialogPacket.INSTANCE);
		}

		if (collection.size() == 1) {
			commandSourceStack.sendSuccess(() -> Component.translatable("commands.dialog.clear.single", collection.iterator().next().getDisplayName()), true);
		} else {
			commandSourceStack.sendSuccess(() -> Component.translatable("commands.dialog.clear.multiple", collection.size()), true);
		}

		return collection.size();
	}
}
