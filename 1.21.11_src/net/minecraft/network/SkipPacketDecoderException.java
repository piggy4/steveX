package net.minecraft.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.IdDispatchCodec;

public class SkipPacketDecoderException extends DecoderException implements IdDispatchCodec.DontDecorateException, SkipPacketException {
	public SkipPacketDecoderException(String string) {
		super(string);
	}

	public SkipPacketDecoderException(Throwable throwable) {
		super(throwable);
	}
}
