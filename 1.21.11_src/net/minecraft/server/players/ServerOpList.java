package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.io.File;
import java.util.Objects;
import net.minecraft.server.notifications.NotificationService;

public class ServerOpList extends StoredUserList<NameAndId, ServerOpListEntry> {
	public ServerOpList(File file, NotificationService notificationService) {
		super(file, notificationService);
	}

	@Override
	protected StoredUserEntry<NameAndId> createEntry(JsonObject jsonObject) {
		return new ServerOpListEntry(jsonObject);
	}

	@Override
	public String[] getUserList() {
		return this.getEntries().stream().map(StoredUserEntry::getUser).filter(Objects::nonNull).map(NameAndId::name).toArray(String[]::new);
	}

	public boolean add(ServerOpListEntry serverOpListEntry) {
		if (super.add(serverOpListEntry)) {
			if (serverOpListEntry.getUser() != null) {
				this.notificationService.playerOped(serverOpListEntry);
			}

			return true;
		} else {
			return false;
		}
	}

	public boolean remove(NameAndId nameAndId) {
		ServerOpListEntry serverOpListEntry = this.get(nameAndId);
		if (super.remove(nameAndId)) {
			if (serverOpListEntry != null) {
				this.notificationService.playerDeoped(serverOpListEntry);
			}

			return true;
		} else {
			return false;
		}
	}

	@Override
	public void clear() {
		for (ServerOpListEntry serverOpListEntry : this.getEntries()) {
			if (serverOpListEntry.getUser() != null) {
				this.notificationService.playerDeoped(serverOpListEntry);
			}
		}

		super.clear();
	}

	public boolean canBypassPlayerLimit(NameAndId nameAndId) {
		ServerOpListEntry serverOpListEntry = this.get(nameAndId);
		return serverOpListEntry != null ? serverOpListEntry.getBypassesPlayerLimit() : false;
	}

	protected String getKeyForUser(NameAndId nameAndId) {
		return nameAndId.id().toString();
	}
}
