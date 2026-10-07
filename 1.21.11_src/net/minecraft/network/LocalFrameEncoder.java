package net.minecraft.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

public class LocalFrameEncoder extends ChannelOutboundHandlerAdapter {
	@Override
	public void write(ChannelHandlerContext channelHandlerContext, Object object, ChannelPromise channelPromise) {
		channelHandlerContext.write(HiddenByteBuf.pack(object), channelPromise);
	}
}
