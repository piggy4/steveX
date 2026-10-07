package net.minecraft.server.jsonrpc.methods;

import com.google.common.net.InetAddresses;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

public class IpBanlistService {
	private static final String BAN_SOURCE = "Management server";

	public static List<IpBanlistService.IpBanDto> get(MinecraftApi minecraftApi) {
		return minecraftApi.banListService().getIpBanEntries().stream().map(IpBanlistService.IpBan::from).map(IpBanlistService.IpBanDto::from).toList();
	}

	public static List<IpBanlistService.IpBanDto> add(MinecraftApi minecraftApi, List<IpBanlistService.IncomingIpBanDto> list, ClientInfo clientInfo) {
		list.stream()
			.map(incomingIpBanDto -> banIp(minecraftApi, incomingIpBanDto, clientInfo))
			.flatMap(Collection::stream)
			.forEach(serverPlayer -> serverPlayer.connection.disconnect(Component.translatable("multiplayer.disconnect.ip_banned")));
		return get(minecraftApi);
	}

	private static List<ServerPlayer> banIp(MinecraftApi minecraftApi, IpBanlistService.IncomingIpBanDto incomingIpBanDto, ClientInfo clientInfo) {
		IpBanlistService.IpBan ipBan = incomingIpBanDto.toIpBan();
		if (ipBan != null) {
			return banIp(minecraftApi, ipBan, clientInfo);
		}

		if (incomingIpBanDto.player().isPresent()) {
			Optional<ServerPlayer> optional = minecraftApi.playerListService().getPlayer(incomingIpBanDto.player().get().id(), incomingIpBanDto.player().get().name());
			if (optional.isPresent()) {
				return banIp(minecraftApi, incomingIpBanDto.toIpBan(optional.get()), clientInfo);
			}
		}

		return List.of();
	}

	private static List<ServerPlayer> banIp(MinecraftApi minecraftApi, IpBanlistService.IpBan ipBan, ClientInfo clientInfo) {
		minecraftApi.banListService().addIpBan(ipBan.toIpBanEntry(), clientInfo);
		return minecraftApi.playerListService().getPlayersWithAddress(ipBan.ip());
	}

	public static List<IpBanlistService.IpBanDto> clear(MinecraftApi minecraftApi, ClientInfo clientInfo) {
		minecraftApi.banListService().clearIpBans(clientInfo);
		return get(minecraftApi);
	}

	public static List<IpBanlistService.IpBanDto> remove(MinecraftApi minecraftApi, List<String> list, ClientInfo clientInfo) {
		list.forEach(string -> minecraftApi.banListService().removeIpBan(string, clientInfo));
		return get(minecraftApi);
	}

	public static List<IpBanlistService.IpBanDto> set(MinecraftApi minecraftApi, List<IpBanlistService.IpBanDto> list, ClientInfo clientInfo) {
		Set<IpBanlistService.IpBan> set = list.stream()
			.filter(ipBanDto -> InetAddresses.isInetAddress(ipBanDto.ip()))
			.map(IpBanlistService.IpBanDto::toIpBan)
			.collect(Collectors.toSet());
		Set<IpBanlistService.IpBan> set2 = minecraftApi.banListService().getIpBanEntries().stream().map(IpBanlistService.IpBan::from).collect(Collectors.toSet());
		set2.stream().filter(ipBan -> !set.contains(ipBan)).forEach(ipBan -> minecraftApi.banListService().removeIpBan(ipBan.ip(), clientInfo));
		set.stream().filter(ipBan -> !set2.contains(ipBan)).forEach(ipBan -> minecraftApi.banListService().addIpBan(ipBan.toIpBanEntry(), clientInfo));
		set.stream()
			.filter(ipBan -> !set2.contains(ipBan))
			.flatMap(ipBan -> minecraftApi.playerListService().getPlayersWithAddress(ipBan.ip()).stream())
			.forEach(serverPlayer -> serverPlayer.connection.disconnect(Component.translatable("multiplayer.disconnect.ip_banned")));
		return get(minecraftApi);
	}

	public record IncomingIpBanDto(Optional<PlayerDto> player, Optional<String> ip, Optional<String> reason, Optional<String> source, Optional<Instant> expires) {
		public static final MapCodec<IpBanlistService.IncomingIpBanDto> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					PlayerDto.CODEC.codec().optionalFieldOf("player").forGetter(IpBanlistService.IncomingIpBanDto::player),
					Codec.STRING.optionalFieldOf("ip").forGetter(IpBanlistService.IncomingIpBanDto::ip),
					Codec.STRING.optionalFieldOf("reason").forGetter(IpBanlistService.IncomingIpBanDto::reason),
					Codec.STRING.optionalFieldOf("source").forGetter(IpBanlistService.IncomingIpBanDto::source),
					ExtraCodecs.INSTANT_ISO8601.optionalFieldOf("expires").forGetter(IpBanlistService.IncomingIpBanDto::expires)
				)
				.apply(instance, IpBanlistService.IncomingIpBanDto::new)
		);

		IpBanlistService.IpBan toIpBan(ServerPlayer serverPlayer) {
			return new IpBanlistService.IpBan(serverPlayer.getIpAddress(), this.reason().orElse(null), this.source().orElse("Management server"), this.expires());
		}

		IpBanlistService.@Nullable IpBan toIpBan() {
			return !this.ip().isEmpty() && InetAddresses.isInetAddress(this.ip().get())
				? new IpBanlistService.IpBan(this.ip().get(), this.reason().orElse(null), this.source().orElse("Management server"), this.expires())
				: null;
		}
	}

	record IpBan(String ip, @Nullable String reason, String source, Optional<Instant> expires) {
		static IpBanlistService.IpBan from(IpBanListEntry ipBanListEntry) {
			return new IpBanlistService.IpBan(
				Objects.requireNonNull(ipBanListEntry.getUser()),
				ipBanListEntry.getReason(),
				ipBanListEntry.getSource(),
				Optional.ofNullable(ipBanListEntry.getExpires()).map(Date::toInstant)
			);
		}

		IpBanListEntry toIpBanEntry() {
			return new IpBanListEntry(this.ip(), null, this.source(), this.expires().map(Date::from).orElse(null), this.reason());
		}
	}

	public record IpBanDto(String ip, Optional<String> reason, Optional<String> source, Optional<Instant> expires) {
		public static final MapCodec<IpBanlistService.IpBanDto> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.STRING.fieldOf("ip").forGetter(IpBanlistService.IpBanDto::ip),
					Codec.STRING.optionalFieldOf("reason").forGetter(IpBanlistService.IpBanDto::reason),
					Codec.STRING.optionalFieldOf("source").forGetter(IpBanlistService.IpBanDto::source),
					ExtraCodecs.INSTANT_ISO8601.optionalFieldOf("expires").forGetter(IpBanlistService.IpBanDto::expires)
				)
				.apply(instance, IpBanlistService.IpBanDto::new)
		);

		private static IpBanlistService.IpBanDto from(IpBanlistService.IpBan ipBan) {
			return new IpBanlistService.IpBanDto(ipBan.ip(), Optional.ofNullable(ipBan.reason()), Optional.of(ipBan.source()), ipBan.expires());
		}

		public static IpBanlistService.IpBanDto from(IpBanListEntry ipBanListEntry) {
			return from(IpBanlistService.IpBan.from(ipBanListEntry));
		}

		private IpBanlistService.IpBan toIpBan() {
			return new IpBanlistService.IpBan(this.ip(), this.reason().orElse(null), this.source().orElse("Management server"), this.expires());
		}
	}
}
