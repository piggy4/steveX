package net.minecraft.server.players;

import java.util.Optional;
import java.util.UUID;

public interface UserNameToIdResolver {
	void add(NameAndId nameAndId);

	Optional<NameAndId> get(String string);

	Optional<NameAndId> get(UUID uUID);

	void resolveOfflineUsers(boolean bl);

	void save();
}
