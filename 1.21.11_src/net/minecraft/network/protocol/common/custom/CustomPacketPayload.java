package net.minecraft.network.protocol.common.custom;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.resources.Identifier;

public interface CustomPacketPayload {
	CustomPacketPayload.Type<? extends CustomPacketPayload> type();

	static <B extends ByteBuf, T extends CustomPacketPayload> StreamCodec<B, T> codec(
		StreamMemberEncoder<B, T> streamMemberEncoder, StreamDecoder<B, T> streamDecoder
	) {
		return StreamCodec.ofMember(streamMemberEncoder, streamDecoder);
	}

	static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> createType(String string) {
		return new CustomPacketPayload.Type<>(Identifier.withDefaultNamespace(string));
	}

	static <B extends FriendlyByteBuf> StreamCodec<B, CustomPacketPayload> codec(
		CustomPacketPayload.FallbackProvider<B> fallbackProvider, List<CustomPacketPayload.TypeAndCodec<? super B, ?>> list
	) {
		final Map<Identifier, StreamCodec<? super B, ? extends CustomPacketPayload>> map = list.stream()
			.collect(Collectors.toUnmodifiableMap(typeAndCodec -> typeAndCodec.type().id(), CustomPacketPayload.TypeAndCodec::codec));
		return new StreamCodec<B, CustomPacketPayload>() {
			private StreamCodec<? super B, ? extends CustomPacketPayload> findCodec(Identifier identifier) {
				StreamCodec<? super B, ? extends CustomPacketPayload> streamCodec = map.get(identifier);
				return streamCodec != null ? streamCodec : fallbackProvider.create(identifier);
			}

			private <T extends CustomPacketPayload> void writeCap(B friendlyByteBuf, CustomPacketPayload.Type<T> type, CustomPacketPayload customPacketPayload) {
				friendlyByteBuf.writeIdentifier(type.id());
				StreamCodec<B, T> streamCodec = this.findCodec(type.id);
				streamCodec.encode(friendlyByteBuf, (T)customPacketPayload);
			}

			public void encode(B friendlyByteBuf, CustomPacketPayload customPacketPayload) {
				this.writeCap(friendlyByteBuf, customPacketPayload.type(), customPacketPayload);
			}

			public CustomPacketPayload decode(B friendlyByteBuf) {
				Identifier identifier = friendlyByteBuf.readIdentifier();
				return (CustomPacketPayload)this.findCodec(identifier).decode(friendlyByteBuf);
			}
		};
	}

	interface FallbackProvider<B extends FriendlyByteBuf> {
		StreamCodec<B, ? extends CustomPacketPayload> create(Identifier identifier);
	}

	record Type<T extends CustomPacketPayload>(Identifier id) {
	}

	record TypeAndCodec<B extends FriendlyByteBuf, T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<B, T> codec) {
	}
}
