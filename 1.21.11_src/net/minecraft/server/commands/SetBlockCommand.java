package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.jspecify.annotations.Nullable;

public class SetBlockCommand {
	private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.setblock.failed"));

	public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher, CommandBuildContext commandBuildContext) {
		Predicate<BlockInWorld> predicate = blockInWorld -> blockInWorld.getLevel().isEmptyBlock(blockInWorld.getPos());
		commandDispatcher.register(
			Commands.literal("setblock")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(
					Commands.argument("pos", BlockPosArgument.blockPos())
						.then(
							Commands.argument("block", BlockStateArgument.block(commandBuildContext))
								.executes(
									commandContext -> setBlock(
										commandContext.getSource(),
										BlockPosArgument.getLoadedBlockPos(commandContext, "pos"),
										BlockStateArgument.getBlock(commandContext, "block"),
										SetBlockCommand.Mode.REPLACE,
										null,
										false
									)
								)
								.then(
									Commands.literal("destroy")
										.executes(
											commandContext -> setBlock(
												commandContext.getSource(),
												BlockPosArgument.getLoadedBlockPos(commandContext, "pos"),
												BlockStateArgument.getBlock(commandContext, "block"),
												SetBlockCommand.Mode.DESTROY,
												null,
												false
											)
										)
								)
								.then(
									Commands.literal("keep")
										.executes(
											commandContext -> setBlock(
												commandContext.getSource(),
												BlockPosArgument.getLoadedBlockPos(commandContext, "pos"),
												BlockStateArgument.getBlock(commandContext, "block"),
												SetBlockCommand.Mode.REPLACE,
												predicate,
												false
											)
										)
								)
								.then(
									Commands.literal("replace")
										.executes(
											commandContext -> setBlock(
												commandContext.getSource(),
												BlockPosArgument.getLoadedBlockPos(commandContext, "pos"),
												BlockStateArgument.getBlock(commandContext, "block"),
												SetBlockCommand.Mode.REPLACE,
												null,
												false
											)
										)
								)
								.then(
									Commands.literal("strict")
										.executes(
											commandContext -> setBlock(
												commandContext.getSource(),
												BlockPosArgument.getLoadedBlockPos(commandContext, "pos"),
												BlockStateArgument.getBlock(commandContext, "block"),
												SetBlockCommand.Mode.REPLACE,
												null,
												true
											)
										)
								)
						)
				)
		);
	}

	private static int setBlock(
		CommandSourceStack commandSourceStack,
		BlockPos blockPos,
		BlockInput blockInput,
		SetBlockCommand.Mode mode,
		@Nullable Predicate<BlockInWorld> predicate,
		boolean bl
	) throws CommandSyntaxException {
		ServerLevel serverLevel = commandSourceStack.getLevel();
		if (serverLevel.isDebug()) {
			throw ERROR_FAILED.create();
		}

		if (predicate != null && !predicate.test(new BlockInWorld(serverLevel, blockPos, true))) {
			throw ERROR_FAILED.create();
		}

		boolean bl2;
		if (mode == SetBlockCommand.Mode.DESTROY) {
			serverLevel.destroyBlock(blockPos, true);
			bl2 = !blockInput.getState().isAir() || !serverLevel.getBlockState(blockPos).isAir();
		} else {
			bl2 = true;
		}

		BlockState blockState = serverLevel.getBlockState(blockPos);
		if (bl2 && !blockInput.place(serverLevel, blockPos, 2 | (bl ? 816 : 256))) {
			throw ERROR_FAILED.create();
		}

		if (!bl) {
			serverLevel.updateNeighboursOnBlockSet(blockPos, blockState);
		}

		commandSourceStack.sendSuccess(() -> Component.translatable("commands.setblock.success", blockPos.getX(), blockPos.getY(), blockPos.getZ()), true);
		return 1;
	}

	public enum Mode {
		REPLACE,
		DESTROY;
	}
}
