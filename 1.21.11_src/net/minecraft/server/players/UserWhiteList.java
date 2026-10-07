package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.io.File;
import java.util.Objects;
import net.minecraft.server.notifications.NotificationService;

public class UserWhiteList extends StoredUserList<NameAndId, UserWhiteListEntry> {
	public UserWhiteList(File file, NotificationService notificationService) {
		super(file, notificationService);
	}

	@Override
	protected StoredUserEntry<NameAndId> createEntry(JsonObject jsonObject) {
		return new UserWhiteListEntry(jsonObject);
	}

	public boolean isWhiteListed(NameAndId nameAndId) {
		return this.contains(nameAndId);
	}

	public boolean add(UserWhiteListEntry userWhiteListEntry) {
		if (super.add(userWhiteListEntry)) {
			if (userWhiteListEntry.getUser() != null) {
				this.notificationService.playerAddedToAllowlist(userWhiteListEntry.getUser());
			}

			return true;
		} else {
			return false;
		}
	}

	public boolean remove(NameAndId nameAndId) {
		if (super.remove(nameAndId)) {
			this.notificationService.playerRemovedFromAllowlist(nameAndId);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void clear() {
		for (UserWhiteListEntry userWhiteListEntry : this.getEntries()) {
			if (userWhiteListEntry.getUser() != null) {
				this.notificationService.playerRemovedFromAllowlist(userWhiteListEntry.getUser());
			}
		}

		super.clear();
	}

	@Override
	public String[] getUserList() {
		return this.getEntries().stream().map(StoredUserEntry::getUser).filter(Objects::nonNull).map(NameAndId::name).toArray(String[]::new);
	}

	protected String getKeyForUser(NameAndId nameAndId) {
		return nameAndId.id().toString();
	}
}
