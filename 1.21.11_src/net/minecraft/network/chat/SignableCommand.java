package net.minecraft.network.chat;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.SignedArgument;
import org.jspecify.annotations.Nullable;

public record SignableCommand<S>(List<SignableCommand.Argument<S>> arguments) {
	public static <S> boolean hasSignableArguments(ParseResults<S> parseResults) {
		return !of(parseResults).arguments().isEmpty();
	}

	public static <S> SignableCommand<S> of(ParseResults<S> parseResults) {
		String string = parseResults.getReader().getString();
		CommandContextBuilder<S> commandContextBuilder = parseResults.getContext();
		CommandContextBuilder<S> commandContextBuilder2 = commandContextBuilder;
		List<SignableCommand.Argument<S>> list = collectArguments(string, commandContextBuilder2);

		CommandContextBuilder<S> commandContextBuilder3;
		while ((commandContextBuilder3 = commandContextBuilder2.getChild()) != null && commandContextBuilder3.getRootNode() != commandContextBuilder.getRootNode()) {
			list.addAll(collectArguments(string, commandContextBuilder3));
			commandContextBuilder2 = commandContextBuilder3;
		}

		return new SignableCommand<>(list);
	}

	private static <S> List<SignableCommand.Argument<S>> collectArguments(String string, CommandContextBuilder<S> commandContextBuilder) {
		List<SignableCommand.Argument<S>> list = new ArrayList<>();

		for (ParsedCommandNode<S> parsedCommandNode : commandContextBuilder.getNodes()) {
			if (parsedCommandNode.getNode() instanceof ArgumentCommandNode<S, ?> argumentCommandNode && argumentCommandNode.getType() instanceof SignedArgument) {
				ParsedArgument<S, ?> parsedArgument = commandContextBuilder.getArguments().get(argumentCommandNode.getName());
				if (parsedArgument != null) {
					String string2 = parsedArgument.getRange().get(string);
					list.add(new SignableCommand.Argument<>(argumentCommandNode, string2));
				}
			}
		}

		return list;
	}

	public SignableCommand.@Nullable Argument<S> getArgument(String string) {
		for (SignableCommand.Argument<S> argument : this.arguments) {
			if (string.equals(argument.name())) {
				return argument;
			}
		}

		return null;
	}

	public record Argument<S>(ArgumentCommandNode<S, ?> node, String value) {
		public String name() {
			return this.node.getName();
		}
	}
}
