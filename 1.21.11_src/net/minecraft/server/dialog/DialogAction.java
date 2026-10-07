package net.minecraft.server.dialog;

import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

public enum DialogAction implements StringRepresentable {
	CLOSE(0, "close"),
	NONE(1, "none"),
	WAIT_FOR_RESPONSE(2, "wait_for_response");

	public static final IntFunction<DialogAction> BY_ID = ByIdMap.continuous(dialogAction -> dialogAction.id, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
	public static final StringRepresentable.EnumCodec<DialogAction> CODEC = StringRepresentable.fromEnum(DialogAction::values);
	public static final StreamCodec<ByteBuf, DialogAction> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, dialogAction -> dialogAction.id);
	private final int id;
	private final String name;

	DialogAction(final int j, final String string2) {
		this.id = j;
		this.name = string2;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public boolean willUnpause() {
		return this == CLOSE || this == WAIT_FOR_RESPONSE;
	}
}
