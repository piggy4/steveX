package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;

public class ParticleArgument implements ArgumentType<ParticleOptions> {
	private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "particle{foo:bar}");
	public static final DynamicCommandExceptionType ERROR_UNKNOWN_PARTICLE = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("particle.notFound", object)
	);
	public static final DynamicCommandExceptionType ERROR_INVALID_OPTIONS = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("particle.invalidOptions", object)
	);
	private final HolderLookup.Provider registries;
	private static final TagParser<?> VALUE_PARSER = TagParser.create(NbtOps.INSTANCE);

	public ParticleArgument(CommandBuildContext commandBuildContext) {
		this.registries = commandBuildContext;
	}

	public static ParticleArgument particle(CommandBuildContext commandBuildContext) {
		return new ParticleArgument(commandBuildContext);
	}

	public static ParticleOptions getParticle(CommandContext<CommandSourceStack> commandContext, String string) {
		return commandContext.getArgument(string, ParticleOptions.class);
	}

	public ParticleOptions parse(StringReader stringReader) throws CommandSyntaxException {
		return readParticle(stringReader, this.registries);
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}

	public static ParticleOptions readParticle(StringReader stringReader, HolderLookup.Provider provider) throws CommandSyntaxException {
		ParticleType<?> particleType = readParticleType(stringReader, provider.lookupOrThrow(Registries.PARTICLE_TYPE));
		return readParticle(VALUE_PARSER, stringReader, (ParticleType<ParticleOptions>)particleType, provider);
	}

	private static ParticleType<?> readParticleType(StringReader stringReader, HolderLookup<ParticleType<?>> holderLookup) throws CommandSyntaxException {
		Identifier identifier = Identifier.read(stringReader);
		ResourceKey<ParticleType<?>> resourceKey = ResourceKey.create(Registries.PARTICLE_TYPE, identifier);
		return holderLookup.get(resourceKey).orElseThrow(() -> ERROR_UNKNOWN_PARTICLE.createWithContext(stringReader, identifier)).value();
	}

	private static <T extends ParticleOptions, O> T readParticle(
		TagParser<O> tagParser, StringReader stringReader, ParticleType<T> particleType, HolderLookup.Provider provider
	) throws CommandSyntaxException {
		RegistryOps<O> registryOps = provider.createSerializationContext(tagParser.getOps());
		O object;
		if (stringReader.canRead() && stringReader.peek() == '{') {
			object = tagParser.parseAsArgument(stringReader);
		} else {
			object = registryOps.emptyMap();
		}

		return particleType.codec().codec().parse(registryOps, object).getOrThrow(ERROR_INVALID_OPTIONS::create);
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
		HolderLookup.RegistryLookup<ParticleType<?>> registryLookup = this.registries.lookupOrThrow(Registries.PARTICLE_TYPE);
		return SharedSuggestionProvider.suggestResource(registryLookup.listElementIds().map(ResourceKey::identifier), suggestionsBuilder);
	}
}
