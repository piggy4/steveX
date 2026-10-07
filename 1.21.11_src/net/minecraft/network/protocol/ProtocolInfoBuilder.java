package net.minecraft.network.protocol;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.ClientboundPacketListener;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.ServerboundPacketListener;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import org.jspecify.annotations.Nullable;

public class ProtocolInfoBuilder<T extends PacketListener, B extends ByteBuf, C> {
	final ConnectionProtocol protocol;
	final PacketFlow flow;
	private final List<ProtocolInfoBuilder.CodecEntry<T, ?, B, C>> codecs = new ArrayList<>();
	private @Nullable BundlerInfo bundlerInfo;

	public ProtocolInfoBuilder(ConnectionProtocol connectionProtocol, PacketFlow packetFlow) {
		this.protocol = connectionProtocol;
		this.flow = packetFlow;
	}

	public <P extends Packet<? super T>> ProtocolInfoBuilder<T, B, C> addPacket(PacketType<P> packetType, StreamCodec<? super B, P> streamCodec) {
		this.codecs.add(new ProtocolInfoBuilder.CodecEntry<>(packetType, streamCodec, null));
		return this;
	}

	public <P extends Packet<? super T>> ProtocolInfoBuilder<T, B, C> addPacket(
		PacketType<P> packetType, StreamCodec<? super B, P> streamCodec, CodecModifier<B, P, C> codecModifier
	) {
		this.codecs.add(new ProtocolInfoBuilder.CodecEntry<>(packetType, streamCodec, codecModifier));
		return this;
	}

	public <P extends BundlePacket<? super T>, D extends BundleDelimiterPacket<? super T>> ProtocolInfoBuilder<T, B, C> withBundlePacket(
		PacketType<P> packetType, Function<Iterable<Packet<? super T>>, P> function, D bundleDelimiterPacket
	) {
		StreamCodec<ByteBuf, D> streamCodec = StreamCodec.unit(bundleDelimiterPacket);
		PacketType<D> packetType2 = (PacketType<D>)bundleDelimiterPacket.type();
		this.codecs.add(new ProtocolInfoBuilder.CodecEntry<>(packetType2, streamCodec, null));
		this.bundlerInfo = BundlerInfo.createForPacket(packetType, function, bundleDelimiterPacket);
		return this;
	}

	StreamCodec<ByteBuf, Packet<? super T>> buildPacketCodec(Function<ByteBuf, B> function, List<ProtocolInfoBuilder.CodecEntry<T, ?, B, C>> list, C object) {
		ProtocolCodecBuilder<ByteBuf, T> protocolCodecBuilder = new ProtocolCodecBuilder<>(this.flow);

		for (ProtocolInfoBuilder.CodecEntry<T, ?, B, C> codecEntry : list) {
			codecEntry.addToBuilder(protocolCodecBuilder, function, object);
		}

		return protocolCodecBuilder.build();
	}

	private static ProtocolInfo.Details buildDetails(
		ConnectionProtocol connectionProtocol, PacketFlow packetFlow, List<? extends ProtocolInfoBuilder.CodecEntry<?, ?, ?, ?>> list
	) {
		return new ProtocolInfo.Details() {
			@Override
			public ConnectionProtocol id() {
				return connectionProtocol;
			}

			@Override
			public PacketFlow flow() {
				return packetFlow;
			}

			@Override
			public void listPackets(ProtocolInfo.Details.PacketVisitor packetVisitor) {
				for (int i = 0; i < list.size(); i++) {
					ProtocolInfoBuilder.CodecEntry<?, ?, ?, ?> codecEntry = (ProtocolInfoBuilder.CodecEntry<?, ?, ?, ?>)list.get(i);
					packetVisitor.accept(codecEntry.type, i);
				}
			}
		};
	}

	public SimpleUnboundProtocol<T, B> buildUnbound(C object) {
		final List<ProtocolInfoBuilder.CodecEntry<T, ?, B, C>> list = List.copyOf(this.codecs);
		final BundlerInfo bundlerInfo = this.bundlerInfo;
		final ProtocolInfo.Details details = buildDetails(this.protocol, this.flow, list);
		return new SimpleUnboundProtocol<T, B>() {
			@Override
			public ProtocolInfo<T> bind(Function<ByteBuf, B> function) {
				return new ProtocolInfoBuilder.Implementation<>(
					ProtocolInfoBuilder.this.protocol, ProtocolInfoBuilder.this.flow, ProtocolInfoBuilder.this.buildPacketCodec(function, list, object), bundlerInfo
				);
			}

			@Override
			public ProtocolInfo.Details details() {
				return details;
			}
		};
	}

	public UnboundProtocol<T, B, C> buildUnbound() {
		final List<ProtocolInfoBuilder.CodecEntry<T, ?, B, C>> list = List.copyOf(this.codecs);
		final BundlerInfo bundlerInfo = this.bundlerInfo;
		final ProtocolInfo.Details details = buildDetails(this.protocol, this.flow, list);
		return new UnboundProtocol<T, B, C>() {
			@Override
			public ProtocolInfo<T> bind(Function<ByteBuf, B> function, C object) {
				return new ProtocolInfoBuilder.Implementation<>(
					ProtocolInfoBuilder.this.protocol, ProtocolInfoBuilder.this.flow, ProtocolInfoBuilder.this.buildPacketCodec(function, list, object), bundlerInfo
				);
			}

			@Override
			public ProtocolInfo.Details details() {
				return details;
			}
		};
	}

	private static <L extends PacketListener, B extends ByteBuf> SimpleUnboundProtocol<L, B> protocol(
		ConnectionProtocol connectionProtocol, PacketFlow packetFlow, Consumer<ProtocolInfoBuilder<L, B, Unit>> consumer
	) {
		ProtocolInfoBuilder<L, B, Unit> protocolInfoBuilder = new ProtocolInfoBuilder<>(connectionProtocol, packetFlow);
		consumer.accept(protocolInfoBuilder);
		return protocolInfoBuilder.buildUnbound(Unit.INSTANCE);
	}

	public static <T extends ServerboundPacketListener, B extends ByteBuf> SimpleUnboundProtocol<T, B> serverboundProtocol(
		ConnectionProtocol connectionProtocol, Consumer<ProtocolInfoBuilder<T, B, Unit>> consumer
	) {
		return protocol(connectionProtocol, PacketFlow.SERVERBOUND, consumer);
	}

	public static <T extends ClientboundPacketListener, B extends ByteBuf> SimpleUnboundProtocol<T, B> clientboundProtocol(
		ConnectionProtocol connectionProtocol, Consumer<ProtocolInfoBuilder<T, B, Unit>> consumer
	) {
		return protocol(connectionProtocol, PacketFlow.CLIENTBOUND, consumer);
	}

	private static <L extends PacketListener, B extends ByteBuf, C> UnboundProtocol<L, B, C> contextProtocol(
		ConnectionProtocol connectionProtocol, PacketFlow packetFlow, Consumer<ProtocolInfoBuilder<L, B, C>> consumer
	) {
		ProtocolInfoBuilder<L, B, C> protocolInfoBuilder = new ProtocolInfoBuilder<>(connectionProtocol, packetFlow);
		consumer.accept(protocolInfoBuilder);
		return protocolInfoBuilder.buildUnbound();
	}

	public static <T extends ServerboundPacketListener, B extends ByteBuf, C> UnboundProtocol<T, B, C> contextServerboundProtocol(
		ConnectionProtocol connectionProtocol, Consumer<ProtocolInfoBuilder<T, B, C>> consumer
	) {
		return contextProtocol(connectionProtocol, PacketFlow.SERVERBOUND, consumer);
	}

	public static <T extends ClientboundPacketListener, B extends ByteBuf, C> UnboundProtocol<T, B, C> contextClientboundProtocol(
		ConnectionProtocol connectionProtocol, Consumer<ProtocolInfoBuilder<T, B, C>> consumer
	) {
		return contextProtocol(connectionProtocol, PacketFlow.CLIENTBOUND, consumer);
	}

	record CodecEntry<T extends PacketListener, P extends Packet<? super T>, B extends ByteBuf, C>(
		PacketType<P> type, StreamCodec<? super B, P> serializer, @Nullable CodecModifier<B, P, C> modifier
	) {

		public void addToBuilder(ProtocolCodecBuilder<ByteBuf, T> protocolCodecBuilder, Function<ByteBuf, B> function, C object) {
			StreamCodec<? super B, P> streamCodec;
			if (this.modifier != null) {
				streamCodec = this.modifier.apply(this.serializer, object);
			} else {
				streamCodec = this.serializer;
			}

			StreamCodec<ByteBuf, P> streamCodec2 = streamCodec.mapStream(function);
			protocolCodecBuilder.add(this.type, streamCodec2);
		}
	}

	record Implementation<L extends PacketListener>(
		ConnectionProtocol id, PacketFlow flow, StreamCodec<ByteBuf, Packet<? super L>> codec, @Nullable BundlerInfo bundlerInfo
	) implements ProtocolInfo<L> {
	}
}
