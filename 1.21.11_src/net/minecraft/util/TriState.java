package net.minecraft.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import java.util.function.Function;

public enum TriState implements StringRepresentable {
	TRUE("true"),
	FALSE("false"),
	DEFAULT("default");

	public static final Codec<TriState> CODEC = Codec.either(Codec.BOOL, StringRepresentable.fromEnum(TriState::values))
		.xmap(either -> either.map(TriState::from, Function.identity()), triState -> {
			return switch (triState) {
				case TRUE -> Either.left(true);
				case FALSE -> Either.left(false);
				case DEFAULT -> Either.right(triState);
			};
		});
	private final String name;

	TriState(final String string2) {
		this.name = string2;
	}

	public static TriState from(boolean bl) {
		return bl ? TRUE : FALSE;
	}

	public boolean toBoolean(boolean bl) {
		return switch (this) {
			case TRUE -> true;
			case FALSE -> false;
			default -> bl;
		};
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
