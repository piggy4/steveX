package net.minecraft.server.network;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.local.LocalAddress;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RateKickingConnection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerConnectionListener {
	private static final Logger LOGGER = LogUtils.getLogger();
	final MinecraftServer server;
	public volatile boolean running;
	private final List<ChannelFuture> channels = Collections.synchronizedList(Lists.newArrayList());
	final List<Connection> connections = Collections.synchronizedList(Lists.newArrayList());

	public ServerConnectionListener(MinecraftServer minecraftServer) {
		this.server = minecraftServer;
		this.running = true;
	}

	public void startTcpServerListener(@Nullable InetAddress inetAddress, int i) throws IOException {
		synchronized (this.channels) {
			EventLoopGroupHolder eventLoopGroupHolder = EventLoopGroupHolder.remote(this.server.useNativeTransport());
			this.channels.add(new ServerBootstrap().channel(eventLoopGroupHolder.serverChannelCls()).childHandler(new ChannelInitializer<Channel>() {
				@Override
				protected void initChannel(Channel channel) {
					try {
						channel.config().setOption(ChannelOption.TCP_NODELAY, true);
					} catch (ChannelException var5) {
					}

					ChannelPipeline channelPipeline = channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
					if (ServerConnectionListener.this.server.repliesToStatus()) {
						channelPipeline.addLast("legacy_query", new LegacyQueryHandler(ServerConnectionListener.this.getServer()));
					}

					Connection.configureSerialization(channelPipeline, PacketFlow.SERVERBOUND, false, null);
					int ix = ServerConnectionListener.this.server.getRateLimitPacketsPerSecond();
					Connection connection = ix > 0 ? new RateKickingConnection(ix) : new Connection(PacketFlow.SERVERBOUND);
					ServerConnectionListener.this.connections.add(connection);
					connection.configurePacketHandler(channelPipeline);
					connection.setListenerForServerboundHandshake(new ServerHandshakePacketListenerImpl(ServerConnectionListener.this.server, connection));
				}
			}).group(eventLoopGroupHolder.eventLoopGroup()).localAddress(inetAddress, i).bind().syncUninterruptibly());
		}
	}

	public SocketAddress startMemoryChannel() {
		ChannelFuture channelFuture;
		synchronized (this.channels) {
			channelFuture = new ServerBootstrap()
				.channel(EventLoopGroupHolder.local().serverChannelCls())
				.childHandler(
					new ChannelInitializer<Channel>() {
						@Override
						protected void initChannel(Channel channel) {
							Connection connection = new Connection(PacketFlow.SERVERBOUND);
							connection.setListenerForServerboundHandshake(new MemoryServerHandshakePacketListenerImpl(ServerConnectionListener.this.server, connection));
							ServerConnectionListener.this.connections.add(connection);
							ChannelPipeline channelPipeline = channel.pipeline();
							Connection.configureInMemoryPipeline(channelPipeline, PacketFlow.SERVERBOUND);
							if (SharedConstants.DEBUG_FAKE_LATENCY_MS > 0) {
								channelPipeline.addLast(
									"latency", new ServerConnectionListener.LatencySimulator(SharedConstants.DEBUG_FAKE_LATENCY_MS, SharedConstants.DEBUG_FAKE_JITTER_MS)
								);
							}

							connection.configurePacketHandler(channelPipeline);
						}
					}
				)
				.group(EventLoopGroupHolder.local().eventLoopGroup())
				.localAddress(LocalAddress.ANY)
				.bind()
				.syncUninterruptibly();
			this.channels.add(channelFuture);
		}

		return channelFuture.channel().localAddress();
	}

	public void stop() {
		this.running = false;

		for (ChannelFuture channelFuture : this.channels) {
			try {
				channelFuture.channel().close().sync();
			} catch (InterruptedException interruptedException) {
				LOGGER.error("Interrupted whilst closing channel");
			}
		}
	}

	public void tick() {
		synchronized (this.connections) {
			Iterator<Connection> iterator = this.connections.iterator();

			while (iterator.hasNext()) {
				Connection connection = iterator.next();
				if (!connection.isConnecting()) {
					if (connection.isConnected()) {
						try {
							connection.tick();
						} catch (Exception exception) {
							if (connection.isMemoryConnection()) {
								throw new ReportedException(CrashReport.forThrowable(exception, "Ticking memory connection"));
							}

							LOGGER.warn("Failed to handle packet for {}", connection.getLoggableAddress(this.server.logIPs()), exception);
							Component component = Component.literal("Internal server error");
							connection.send(new ClientboundDisconnectPacket(component), PacketSendListener.thenRun(() -> connection.disconnect(component)));
							connection.setReadOnly();
						}
					} else {
						iterator.remove();
						connection.handleDisconnection();
					}
				}
			}
		}
	}

	public MinecraftServer getServer() {
		return this.server;
	}

	public List<Connection> getConnections() {
		return this.connections;
	}

	static class LatencySimulator extends ChannelInboundHandlerAdapter {
		private static final Timer TIMER = new HashedWheelTimer();
		private final int delay;
		private final int jitter;
		private final List<ServerConnectionListener.LatencySimulator.DelayedMessage> queuedMessages = Lists.newArrayList();

		public LatencySimulator(int i, int j) {
			this.delay = i;
			this.jitter = j;
		}

		@Override
		public void channelRead(ChannelHandlerContext channelHandlerContext, Object object) {
			this.delayDownstream(channelHandlerContext, object);
		}

		private void delayDownstream(ChannelHandlerContext channelHandlerContext, Object object) {
			int i = this.delay + (int)(Math.random() * this.jitter);
			this.queuedMessages.add(new ServerConnectionListener.LatencySimulator.DelayedMessage(channelHandlerContext, object));
			TIMER.newTimeout(this::onTimeout, i, TimeUnit.MILLISECONDS);
		}

		private void onTimeout(Timeout timeout) {
			ServerConnectionListener.LatencySimulator.DelayedMessage delayedMessage = this.queuedMessages.remove(0);
			delayedMessage.ctx.fireChannelRead(delayedMessage.msg);
		}

		static class DelayedMessage {
			public final ChannelHandlerContext ctx;
			public final Object msg;

			public DelayedMessage(ChannelHandlerContext channelHandlerContext, Object object) {
				this.ctx = channelHandlerContext;
				this.msg = object;
			}
		}
	}
}
