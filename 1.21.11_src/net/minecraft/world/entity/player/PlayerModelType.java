package net.minecraft.world.entity.player;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

public enum PlayerModelType implements StringRepresentable {
	SLIM("slim", "slim"),
	WIDE("wide", "default");

	public static final Codec<PlayerModelType> CODEC = StringRepresentable.fromEnum(PlayerModelType::values);
	private static final Function<String, PlayerModelType> NAME_LOOKUP = StringRepresentable.createNameLookup(
		values(), playerModelType -> playerModelType.legacyServicesId
	);
	public static final StreamCodec<ByteBuf, PlayerModelType> STREAM_CODEC = ByteBufCodecs.BOOL
		.map(boolean_ -> boolean_ ? SLIM : WIDE, playerModelType -> playerModelType == SLIM);
	private final String id;
	private final String legacyServicesId;

	PlayerModelType(final String string2, final String string3) {
		this.id = string2;
		this.legacyServicesId = string3;
	}

	public static PlayerModelType byLegacyServicesName(@Nullable String string) {
		return Objects.requireNonNullElse(NAME_LOOKUP.apply(string), WIDE);
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}
}
