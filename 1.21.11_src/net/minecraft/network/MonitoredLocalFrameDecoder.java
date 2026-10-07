package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class MonitoredLocalFrameDecoder extends ChannelInboundHandlerAdapter {
	private final BandwidthDebugMonitor monitor;

	public MonitoredLocalFrameDecoder(BandwidthDebugMonitor bandwidthDebugMonitor) {
		this.monitor = bandwidthDebugMonitor;
	}

	@Override
	public void channelRead(ChannelHandlerContext channelHandlerContext, Object object) {
		object = HiddenByteBuf.unpack(object);
		if (object instanceof ByteBuf byteBuf) {
			this.monitor.onReceive(byteBuf.readableBytes());
		}

		channelHandlerContext.fireChannelRead(object);
	}
}
