package net.minecraft.network;

import io.netty.handler.codec.EncoderException;
import net.minecraft.network.codec.IdDispatchCodec;

public class SkipPacketEncoderException extends EncoderException implements IdDispatchCodec.DontDecorateException, SkipPacketException {
	public SkipPacketEncoderException(String string) {
		super(string);
	}

	public SkipPacketEncoderException(Throwable throwable) {
		super(throwable);
	}
}
