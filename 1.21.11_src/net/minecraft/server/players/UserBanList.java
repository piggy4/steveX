package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.io.File;
import java.util.Objects;
import net.minecraft.server.notifications.NotificationService;

public class UserBanList extends StoredUserList<NameAndId, UserBanListEntry> {
	public UserBanList(File file, NotificationService notificationService) {
		super(file, notificationService);
	}

	@Override
	protected StoredUserEntry<NameAndId> createEntry(JsonObject jsonObject) {
		return new UserBanListEntry(jsonObject);
	}

	public boolean isBanned(NameAndId nameAndId) {
		return this.contains(nameAndId);
	}

	@Override
	public String[] getUserList() {
		return this.getEntries().stream().map(StoredUserEntry::getUser).filter(Objects::nonNull).map(NameAndId::name).toArray(String[]::new);
	}

	protected String getKeyForUser(NameAndId nameAndId) {
		return nameAndId.id().toString();
	}

	public boolean add(UserBanListEntry userBanListEntry) {
		if (super.add(userBanListEntry)) {
			if (userBanListEntry.getUser() != null) {
				this.notificationService.playerBanned(userBanListEntry);
			}

			return true;
		} else {
			return false;
		}
	}

	public boolean remove(NameAndId nameAndId) {
		if (super.remove(nameAndId)) {
			this.notificationService.playerUnbanned(nameAndId);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void clear() {
		for (UserBanListEntry userBanListEntry : this.getEntries()) {
			if (userBanListEntry.getUser() != null) {
				this.notificationService.playerUnbanned(userBanListEntry.getUser());
			}
		}

		super.clear();
	}
}
