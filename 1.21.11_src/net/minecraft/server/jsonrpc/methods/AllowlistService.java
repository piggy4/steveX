package net.minecraft.server.jsonrpc.methods;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.StoredUserEntry;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.util.Util;

public class AllowlistService {
	public static List<PlayerDto> get(MinecraftApi minecraftApi) {
		return minecraftApi.allowListService()
			.getEntries()
			.stream()
			.filter(userWhiteListEntry -> userWhiteListEntry.getUser() != null)
			.map(userWhiteListEntry -> PlayerDto.from(userWhiteListEntry.getUser()))
			.toList();
	}

	public static List<PlayerDto> add(MinecraftApi minecraftApi, List<PlayerDto> list, ClientInfo clientInfo) {
		List<CompletableFuture<Optional<NameAndId>>> list2 = list.stream()
			.map(playerDto -> minecraftApi.playerListService().getUser(playerDto.id(), playerDto.name()))
			.toList();

		for (Optional<NameAndId> optional : Util.sequence(list2).join()) {
			optional.ifPresent(nameAndId -> minecraftApi.allowListService().add(new UserWhiteListEntry(nameAndId), clientInfo));
		}

		return get(minecraftApi);
	}

	public static List<PlayerDto> clear(MinecraftApi minecraftApi, ClientInfo clientInfo) {
		minecraftApi.allowListService().clear(clientInfo);
		return get(minecraftApi);
	}

	public static List<PlayerDto> remove(MinecraftApi minecraftApi, List<PlayerDto> list, ClientInfo clientInfo) {
		List<CompletableFuture<Optional<NameAndId>>> list2 = list.stream()
			.map(playerDto -> minecraftApi.playerListService().getUser(playerDto.id(), playerDto.name()))
			.toList();

		for (Optional<NameAndId> optional : Util.sequence(list2).join()) {
			optional.ifPresent(nameAndId -> minecraftApi.allowListService().remove(nameAndId, clientInfo));
		}

		minecraftApi.allowListService().kickUnlistedPlayers(clientInfo);
		return get(minecraftApi);
	}

	public static List<PlayerDto> set(MinecraftApi minecraftApi, List<PlayerDto> list, ClientInfo clientInfo) {
		List<CompletableFuture<Optional<NameAndId>>> list2 = list.stream()
			.map(playerDto -> minecraftApi.playerListService().getUser(playerDto.id(), playerDto.name()))
			.toList();
		Set<NameAndId> set = Util.sequence(list2).join().stream().flatMap(Optional::stream).collect(Collectors.toSet());
		Set<NameAndId> set2 = minecraftApi.allowListService().getEntries().stream().map(StoredUserEntry::getUser).collect(Collectors.toSet());
		set2.stream().filter(nameAndId -> !set.contains(nameAndId)).forEach(nameAndId -> minecraftApi.allowListService().remove(nameAndId, clientInfo));
		set.stream()
			.filter(nameAndId -> !set2.contains(nameAndId))
			.forEach(nameAndId -> minecraftApi.allowListService().add(new UserWhiteListEntry(nameAndId), clientInfo));
		minecraftApi.allowListService().kickUnlistedPlayers(clientInfo);
		return get(minecraftApi);
	}
}
