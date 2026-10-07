package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequences;
import org.jspecify.annotations.Nullable;

public class RandomCommand {
	private static final SimpleCommandExceptionType ERROR_RANGE_TOO_LARGE = new SimpleCommandExceptionType(
		Component.translatable("commands.random.error.range_too_large")
	);
	private static final SimpleCommandExceptionType ERROR_RANGE_TOO_SMALL = new SimpleCommandExceptionType(
		Component.translatable("commands.random.error.range_too_small")
	);

	public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
		commandDispatcher.register(
			Commands.literal("random")
				.then(drawRandomValueTree("value", false))
				.then(drawRandomValueTree("roll", true))
				.then(
					Commands.literal("reset")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(
							Commands.literal("*")
								.executes(commandContext -> resetAllSequences(commandContext.getSource()))
								.then(
									Commands.argument("seed", IntegerArgumentType.integer())
										.executes(
											commandContext -> resetAllSequencesAndSetNewDefaults(commandContext.getSource(), IntegerArgumentType.getInteger(commandContext, "seed"), true, true)
										)
										.then(
											Commands.argument("includeWorldSeed", BoolArgumentType.bool())
												.executes(
													commandContext -> resetAllSequencesAndSetNewDefaults(
														commandContext.getSource(),
														IntegerArgumentType.getInteger(commandContext, "seed"),
														BoolArgumentType.getBool(commandContext, "includeWorldSeed"),
														true
													)
												)
												.then(
													Commands.argument("includeSequenceId", BoolArgumentType.bool())
														.executes(
															commandContext -> resetAllSequencesAndSetNewDefaults(
																commandContext.getSource(),
																IntegerArgumentType.getInteger(commandContext, "seed"),
																BoolArgumentType.getBool(commandContext, "includeWorldSeed"),
																BoolArgumentType.getBool(commandContext, "includeSequenceId")
															)
														)
												)
										)
								)
						)
						.then(
							Commands.argument("sequence", IdentifierArgument.id())
								.suggests(RandomCommand::suggestRandomSequence)
								.executes(commandContext -> resetSequence(commandContext.getSource(), IdentifierArgument.getId(commandContext, "sequence")))
								.then(
									Commands.argument("seed", IntegerArgumentType.integer())
										.executes(
											commandContext -> resetSequence(
												commandContext.getSource(),
												IdentifierArgument.getId(commandContext, "sequence"),
												IntegerArgumentType.getInteger(commandContext, "seed"),
												true,
												true
											)
										)
										.then(
											Commands.argument("includeWorldSeed", BoolArgumentType.bool())
												.executes(
													commandContext -> resetSequence(
														commandContext.getSource(),
														IdentifierArgument.getId(commandContext, "sequence"),
														IntegerArgumentType.getInteger(commandContext, "seed"),
														BoolArgumentType.getBool(commandContext, "includeWorldSeed"),
														true
													)
												)
												.then(
													Commands.argument("includeSequenceId", BoolArgumentType.bool())
														.executes(
															commandContext -> resetSequence(
																commandContext.getSource(),
																IdentifierArgument.getId(commandContext, "sequence"),
																IntegerArgumentType.getInteger(commandContext, "seed"),
																BoolArgumentType.getBool(commandContext, "includeWorldSeed"),
																BoolArgumentType.getBool(commandContext, "includeSequenceId")
															)
														)
												)
										)
								)
						)
				)
		);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> drawRandomValueTree(String string, boolean bl) {
		return Commands.literal(string)
			.then(
				Commands.argument("range", RangeArgument.intRange())
					.executes(commandContext -> randomSample(commandContext.getSource(), RangeArgument.Ints.getRange(commandContext, "range"), null, bl))
					.then(
						Commands.argument("sequence", IdentifierArgument.id())
							.suggests(RandomCommand::suggestRandomSequence)
							.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
							.executes(
								commandContext -> randomSample(
									commandContext.getSource(), RangeArgument.Ints.getRange(commandContext, "range"), IdentifierArgument.getId(commandContext, "sequence"), bl
								)
							)
					)
			);
	}

	private static CompletableFuture<Suggestions> suggestRandomSequence(CommandContext<CommandSourceStack> commandContext, SuggestionsBuilder suggestionsBuilder) {
		List<String> list = Lists.newArrayList();
		commandContext.getSource().getLevel().getRandomSequences().forAllSequences((identifier, randomSequence) -> list.add(identifier.toString()));
		return SharedSuggestionProvider.suggest(list, suggestionsBuilder);
	}

	private static int randomSample(CommandSourceStack commandSourceStack, MinMaxBounds.Ints ints, @Nullable Identifier identifier, boolean bl) throws CommandSyntaxException {
		RandomSource randomSource;
		if (identifier != null) {
			randomSource = commandSourceStack.getLevel().getRandomSequence(identifier);
		} else {
			randomSource = commandSourceStack.getLevel().getRandom();
		}

		int i = ints.min().orElse(Integer.MIN_VALUE);
		int j = ints.max().orElse(Integer.MAX_VALUE);
		long l = (long)j - i;
		if (l == 0L) {
			throw ERROR_RANGE_TOO_SMALL.create();
		}

		if (l >= 2147483647L) {
			throw ERROR_RANGE_TOO_LARGE.create();
		}

		int k = Mth.randomBetweenInclusive(randomSource, i, j);
		if (bl) {
			commandSourceStack.getServer()
				.getPlayerList()
				.broadcastSystemMessage(Component.translatable("commands.random.roll", commandSourceStack.getDisplayName(), k, i, j), false);
		} else {
			commandSourceStack.sendSuccess(() -> Component.translatable("commands.random.sample.success", k), false);
		}

		return k;
	}

	private static int resetSequence(CommandSourceStack commandSourceStack, Identifier identifier) throws CommandSyntaxException {
		ServerLevel serverLevel = commandSourceStack.getLevel();
		serverLevel.getRandomSequences().reset(identifier, serverLevel.getSeed());
		commandSourceStack.sendSuccess(() -> Component.translatable("commands.random.reset.success", Component.translationArg(identifier)), false);
		return 1;
	}

	private static int resetSequence(CommandSourceStack commandSourceStack, Identifier identifier, int i, boolean bl, boolean bl2) throws CommandSyntaxException {
		ServerLevel serverLevel = commandSourceStack.getLevel();
		serverLevel.getRandomSequences().reset(identifier, serverLevel.getSeed(), i, bl, bl2);
		commandSourceStack.sendSuccess(() -> Component.translatable("commands.random.reset.success", Component.translationArg(identifier)), false);
		return 1;
	}

	private static int resetAllSequences(CommandSourceStack commandSourceStack) {
		int i = commandSourceStack.getLevel().getRandomSequences().clear();
		commandSourceStack.sendSuccess(() -> Component.translatable("commands.random.reset.all.success", i), false);
		return i;
	}

	private static int resetAllSequencesAndSetNewDefaults(CommandSourceStack commandSourceStack, int i, boolean bl, boolean bl2) {
		RandomSequences randomSequences = commandSourceStack.getLevel().getRandomSequences();
		randomSequences.setSeedDefaults(i, bl, bl2);
		int j = randomSequences.clear();
		commandSourceStack.sendSuccess(() -> Component.translatable("commands.random.reset.all.success", j), false);
		return j;
	}
}
