package net.minecraft.server.jsonrpc.methods;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.util.Util;

public class OperatorService {
	public static List<OperatorService.OperatorDto> get(MinecraftApi minecraftApi) {
		return minecraftApi.operatorListService()
			.getEntries()
			.stream()
			.filter(serverOpListEntry -> serverOpListEntry.getUser() != null)
			.map(OperatorService.OperatorDto::from)
			.toList();
	}

	public static List<OperatorService.OperatorDto> clear(MinecraftApi minecraftApi, ClientInfo clientInfo) {
		minecraftApi.operatorListService().clear(clientInfo);
		return get(minecraftApi);
	}

	public static List<OperatorService.OperatorDto> remove(MinecraftApi minecraftApi, List<PlayerDto> list, ClientInfo clientInfo) {
		List<CompletableFuture<Optional<NameAndId>>> list2 = list.stream()
			.map(playerDto -> minecraftApi.playerListService().getUser(playerDto.id(), playerDto.name()))
			.toList();

		for (Optional<NameAndId> optional : Util.sequence(list2).join()) {
			optional.ifPresent(nameAndId -> minecraftApi.operatorListService().deop(nameAndId, clientInfo));
		}

		return get(minecraftApi);
	}

	public static List<OperatorService.OperatorDto> add(MinecraftApi minecraftApi, List<OperatorService.OperatorDto> list, ClientInfo clientInfo) {
		List<CompletableFuture<Optional<OperatorService.Op>>> list2 = list.stream()
			.map(
				operatorDto -> minecraftApi.playerListService()
					.getUser(operatorDto.player().id(), operatorDto.player().name())
					.thenApply(optionalx -> optionalx.map(nameAndId -> new OperatorService.Op(nameAndId, operatorDto.permissionLevel(), operatorDto.bypassesPlayerLimit())))
			)
			.toList();

		for (Optional<OperatorService.Op> optional : Util.sequence(list2).join()) {
			optional.ifPresent(op -> minecraftApi.operatorListService().op(op.user(), op.permissionLevel(), op.bypassesPlayerLimit(), clientInfo));
		}

		return get(minecraftApi);
	}

	public static List<OperatorService.OperatorDto> set(MinecraftApi minecraftApi, List<OperatorService.OperatorDto> list, ClientInfo clientInfo) {
		List<CompletableFuture<Optional<OperatorService.Op>>> list2 = list.stream()
			.map(
				operatorDto -> minecraftApi.playerListService()
					.getUser(operatorDto.player().id(), operatorDto.player().name())
					.thenApply(optional -> optional.map(nameAndId -> new OperatorService.Op(nameAndId, operatorDto.permissionLevel(), operatorDto.bypassesPlayerLimit())))
			)
			.toList();
		Set<OperatorService.Op> set = Util.sequence(list2).join().stream().flatMap(Optional::stream).collect(Collectors.toSet());
		Set<OperatorService.Op> set2 = minecraftApi.operatorListService()
			.getEntries()
			.stream()
			.filter(serverOpListEntry -> serverOpListEntry.getUser() != null)
			.map(
				serverOpListEntry -> new OperatorService.Op(
					serverOpListEntry.getUser(), Optional.of(serverOpListEntry.permissions().level()), Optional.of(serverOpListEntry.getBypassesPlayerLimit())
				)
			)
			.collect(Collectors.toSet());
		set2.stream().filter(op -> !set.contains(op)).forEach(op -> minecraftApi.operatorListService().deop(op.user(), clientInfo));
		set.stream()
			.filter(op -> !set2.contains(op))
			.forEach(op -> minecraftApi.operatorListService().op(op.user(), op.permissionLevel(), op.bypassesPlayerLimit(), clientInfo));
		return get(minecraftApi);
	}

	record Op(NameAndId user, Optional<PermissionLevel> permissionLevel, Optional<Boolean> bypassesPlayerLimit) {
	}

	public record OperatorDto(PlayerDto player, Optional<PermissionLevel> permissionLevel, Optional<Boolean> bypassesPlayerLimit) {
		public static final MapCodec<OperatorService.OperatorDto> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					PlayerDto.CODEC.codec().fieldOf("player").forGetter(OperatorService.OperatorDto::player),
					PermissionLevel.INT_CODEC.optionalFieldOf("permissionLevel").forGetter(OperatorService.OperatorDto::permissionLevel),
					Codec.BOOL.optionalFieldOf("bypassesPlayerLimit").forGetter(OperatorService.OperatorDto::bypassesPlayerLimit)
				)
				.apply(instance, OperatorService.OperatorDto::new)
		);

		public static OperatorService.OperatorDto from(ServerOpListEntry serverOpListEntry) {
			return new OperatorService.OperatorDto(
				PlayerDto.from(Objects.requireNonNull(serverOpListEntry.getUser())),
				Optional.of(serverOpListEntry.permissions().level()),
				Optional.of(serverOpListEntry.getBypassesPlayerLimit())
			);
		}
	}
}
