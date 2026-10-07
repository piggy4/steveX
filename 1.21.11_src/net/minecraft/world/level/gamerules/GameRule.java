package net.minecraft.world.level.gamerules;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Objects;
import java.util.function.ToIntFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlagSet;

public final class GameRule<T> implements FeatureElement {
	private final GameRuleCategory category;
	private final GameRuleType gameRuleType;
	private final ArgumentType<T> argument;
	private final GameRules.VisitorCaller<T> visitorCaller;
	private final Codec<T> valueCodec;
	private final ToIntFunction<T> commandResultFunction;
	private final T defaultValue;
	private final FeatureFlagSet requiredFeatures;

	public GameRule(
		GameRuleCategory gameRuleCategory,
		GameRuleType gameRuleType,
		ArgumentType<T> argumentType,
		GameRules.VisitorCaller<T> visitorCaller,
		Codec<T> codec,
		ToIntFunction<T> toIntFunction,
		T object,
		FeatureFlagSet featureFlagSet
	) {
		this.category = gameRuleCategory;
		this.gameRuleType = gameRuleType;
		this.argument = argumentType;
		this.visitorCaller = visitorCaller;
		this.valueCodec = codec;
		this.commandResultFunction = toIntFunction;
		this.defaultValue = object;
		this.requiredFeatures = featureFlagSet;
	}

	@Override
	public String toString() {
		return this.id();
	}

	public String id() {
		return this.getIdentifier().toShortString();
	}

	public Identifier getIdentifier() {
		return Objects.requireNonNull(BuiltInRegistries.GAME_RULE.getKey(this));
	}

	public String getDescriptionId() {
		return Util.makeDescriptionId("gamerule", this.getIdentifier());
	}

	public String serialize(T object) {
		return object.toString();
	}

	public DataResult<T> deserialize(String string) {
		try {
			StringReader stringReader = new StringReader(string);
			T object = this.argument.parse(stringReader);
			return stringReader.canRead() ? DataResult.error(() -> "Failed to deserialize; trailing characters", object) : DataResult.success(object);
		} catch (CommandSyntaxException commandSyntaxException) {
			return DataResult.error(() -> "Failed to deserialize");
		}
	}

	public Class<T> valueClass() {
		return (Class<T>)this.defaultValue.getClass();
	}

	public void callVisitor(GameRuleTypeVisitor gameRuleTypeVisitor) {
		this.visitorCaller.call(gameRuleTypeVisitor, this);
	}

	public int getCommandResult(T object) {
		return this.commandResultFunction.applyAsInt(object);
	}

	public GameRuleCategory category() {
		return this.category;
	}

	public GameRuleType gameRuleType() {
		return this.gameRuleType;
	}

	public ArgumentType<T> argument() {
		return this.argument;
	}

	public Codec<T> valueCodec() {
		return this.valueCodec;
	}

	public T defaultValue() {
		return this.defaultValue;
	}

	@Override
	public FeatureFlagSet requiredFeatures() {
		return this.requiredFeatures;
	}
}
