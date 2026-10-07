package net.minecraft.network.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.PacketListener;
import org.jspecify.annotations.Nullable;

public interface BundlerInfo {
	int BUNDLE_SIZE_LIMIT = 4096;

	static <T extends PacketListener, P extends BundlePacket<? super T>> BundlerInfo createForPacket(
		PacketType<P> packetType, Function<Iterable<Packet<? super T>>, P> function, BundleDelimiterPacket<? super T> bundleDelimiterPacket
	) {
		return new BundlerInfo() {
			@Override
			public void unbundlePacket(Packet<?> packet, Consumer<Packet<?>> consumer) {
				if (packet.type() == packetType) {
					P bundlePacket = (P)packet;
					consumer.accept(bundleDelimiterPacket);
					bundlePacket.subPackets().forEach(consumer);
					consumer.accept(bundleDelimiterPacket);
				} else {
					consumer.accept(packet);
				}
			}

			@Override
			public BundlerInfo.@Nullable Bundler startPacketBundling(Packet<?> packet) {
				return packet == bundleDelimiterPacket ? new BundlerInfo.Bundler() {
					private final List<Packet<? super T>> bundlePackets = new ArrayList<>();

					@Override
					public @Nullable Packet<?> addPacket(Packet<?> packet) {
						if (packet == bundleDelimiterPacket) {
							return function.apply(this.bundlePackets);
						}

						Packet<T> packet2 = (Packet<T>)packet;
						if (this.bundlePackets.size() >= 4096) {
							throw new IllegalStateException("Too many packets in a bundle");
						}

						this.bundlePackets.add(packet2);
						return null;
					}
				} : null;
			}
		};
	}

	void unbundlePacket(Packet<?> packet, Consumer<Packet<?>> consumer);

	BundlerInfo.@Nullable Bundler startPacketBundling(Packet<?> packet);

	interface Bundler {
		@Nullable Packet<?> addPacket(Packet<?> packet);
	}
}
