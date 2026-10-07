package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.io.File;
import java.net.SocketAddress;
import net.minecraft.server.notifications.NotificationService;
import org.jspecify.annotations.Nullable;

public class IpBanList extends StoredUserList<String, IpBanListEntry> {
	public IpBanList(File file, NotificationService notificationService) {
		super(file, notificationService);
	}

	@Override
	protected StoredUserEntry<String> createEntry(JsonObject jsonObject) {
		return new IpBanListEntry(jsonObject);
	}

	public boolean isBanned(SocketAddress socketAddress) {
		String string = this.getIpFromAddress(socketAddress);
		return this.contains(string);
	}

	public boolean isBanned(String string) {
		return this.contains(string);
	}

	public @Nullable IpBanListEntry get(SocketAddress socketAddress) {
		String string = this.getIpFromAddress(socketAddress);
		return this.get(string);
	}

	private String getIpFromAddress(SocketAddress socketAddress) {
		String string = socketAddress.toString();
		if (string.contains("/")) {
			string = string.substring(string.indexOf(47) + 1);
		}

		if (string.contains(":")) {
			string = string.substring(0, string.indexOf(58));
		}

		return string;
	}

	public boolean add(IpBanListEntry ipBanListEntry) {
		if (super.add(ipBanListEntry)) {
			if (ipBanListEntry.getUser() != null) {
				this.notificationService.ipBanned(ipBanListEntry);
			}

			return true;
		} else {
			return false;
		}
	}

	public boolean remove(String string) {
		if (super.remove(string)) {
			this.notificationService.ipUnbanned(string);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void clear() {
		for (IpBanListEntry ipBanListEntry : this.getEntries()) {
			if (ipBanListEntry.getUser() != null) {
				this.notificationService.ipUnbanned(ipBanListEntry.getUser());
			}
		}

		super.clear();
	}
}
