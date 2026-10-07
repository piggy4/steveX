package net.minecraft.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class LocalFrameDecoder extends ChannelInboundHandlerAdapter {
	@Override
	public void channelRead(ChannelHandlerContext channelHandlerContext, Object object) {
		channelHandlerContext.fireChannelRead(HiddenByteBuf.unpack(object));
	}
}
