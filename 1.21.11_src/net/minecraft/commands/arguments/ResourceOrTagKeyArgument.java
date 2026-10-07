package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Either;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

public class ResourceOrTagKeyArgument<T> implements ArgumentType<ResourceOrTagKeyArgument.Result<T>> {
	private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012", "#skeletons", "#minecraft:skeletons");
	final ResourceKey<? extends Registry<T>> registryKey;

	public ResourceOrTagKeyArgument(ResourceKey<? extends Registry<T>> resourceKey) {
		this.registryKey = resourceKey;
	}

	public static <T> ResourceOrTagKeyArgument<T> resourceOrTagKey(ResourceKey<? extends Registry<T>> resourceKey) {
		return new ResourceOrTagKeyArgument<>(resourceKey);
	}

	public static <T> ResourceOrTagKeyArgument.Result<T> getResourceOrTagKey(
		CommandContext<CommandSourceStack> commandContext,
		String string,
		ResourceKey<Registry<T>> resourceKey,
		DynamicCommandExceptionType dynamicCommandExceptionType
	) throws CommandSyntaxException {
		ResourceOrTagKeyArgument.Result<?> result = commandContext.getArgument(string, ResourceOrTagKeyArgument.Result.class);
		Optional<ResourceOrTagKeyArgument.Result<T>> optional = result.cast(resourceKey);
		return optional.orElseThrow(() -> dynamicCommandExceptionType.create(result));
	}

	public ResourceOrTagKeyArgument.Result<T> parse(StringReader stringReader) throws CommandSyntaxException {
		if (stringReader.canRead() && stringReader.peek() == '#') {
			int i = stringReader.getCursor();

			try {
				stringReader.skip();
				Identifier identifier = Identifier.read(stringReader);
				return new ResourceOrTagKeyArgument.TagResult<>(TagKey.create(this.registryKey, identifier));
			} catch (CommandSyntaxException commandSyntaxException) {
				stringReader.setCursor(i);
				throw commandSyntaxException;
			}
		} else {
			Identifier identifier2 = Identifier.read(stringReader);
			return new ResourceOrTagKeyArgument.ResourceResult<>(ResourceKey.create(this.registryKey, identifier2));
		}
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
		return SharedSuggestionProvider.listSuggestions(commandContext, suggestionsBuilder, this.registryKey, SharedSuggestionProvider.ElementSuggestionType.ALL);
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}

	public static class Info<T> implements ArgumentTypeInfo<ResourceOrTagKeyArgument<T>, ResourceOrTagKeyArgument.Info<T>.Template> {
		public void serializeToNetwork(ResourceOrTagKeyArgument.Info<T>.Template template, FriendlyByteBuf friendlyByteBuf) {
			friendlyByteBuf.writeResourceKey(template.registryKey);
		}

		public ResourceOrTagKeyArgument.Info<T>.Template deserializeFromNetwork(FriendlyByteBuf friendlyByteBuf) {
			return new ResourceOrTagKeyArgument.Info.Template(friendlyByteBuf.readRegistryKey());
		}

		public void serializeToJson(ResourceOrTagKeyArgument.Info<T>.Template template, JsonObject jsonObject) {
			jsonObject.addProperty("registry", template.registryKey.identifier().toString());
		}

		public ResourceOrTagKeyArgument.Info<T>.Template unpack(ResourceOrTagKeyArgument<T> resourceOrTagKeyArgument) {
			return new ResourceOrTagKeyArgument.Info.Template(resourceOrTagKeyArgument.registryKey);
		}

		public final class Template implements ArgumentTypeInfo.Template<ResourceOrTagKeyArgument<T>> {
			final ResourceKey<? extends Registry<T>> registryKey;

			Template(final ResourceKey<? extends Registry<T>> resourceKey) {
				this.registryKey = resourceKey;
			}

			public ResourceOrTagKeyArgument<T> instantiate(CommandBuildContext commandBuildContext) {
				return new ResourceOrTagKeyArgument<>(this.registryKey);
			}

			@Override
			public ArgumentTypeInfo<ResourceOrTagKeyArgument<T>, ?> type() {
				return Info.this;
			}
		}
	}

	record ResourceResult<T>(ResourceKey<T> key) implements ResourceOrTagKeyArgument.Result<T> {
		@Override
		public Either<ResourceKey<T>, TagKey<T>> unwrap() {
			return Either.left(this.key);
		}

		@Override
		public <E> Optional<ResourceOrTagKeyArgument.Result<E>> cast(ResourceKey<? extends Registry<E>> resourceKey) {
			return this.key.cast(resourceKey).map(ResourceOrTagKeyArgument.ResourceResult::new);
		}

		public boolean test(Holder<T> holder) {
			return holder.is(this.key);
		}

		@Override
		public String asPrintable() {
			return this.key.identifier().toString();
		}
	}

	public interface Result<T> extends Predicate<Holder<T>> {
		Either<ResourceKey<T>, TagKey<T>> unwrap();

		<E> Optional<ResourceOrTagKeyArgument.Result<E>> cast(ResourceKey<? extends Registry<E>> resourceKey);

		String asPrintable();
	}

	record TagResult<T>(TagKey<T> key) implements ResourceOrTagKeyArgument.Result<T> {
		@Override
		public Either<ResourceKey<T>, TagKey<T>> unwrap() {
			return Either.right(this.key);
		}

		@Override
		public <E> Optional<ResourceOrTagKeyArgument.Result<E>> cast(ResourceKey<? extends Registry<E>> resourceKey) {
			return this.key.cast(resourceKey).map(ResourceOrTagKeyArgument.TagResult::new);
		}

		public boolean test(Holder<T> holder) {
			return holder.is(this.key);
		}

		@Override
		public String asPrintable() {
			return "#" + this.key.location();
		}
	}
}
