package net.minecraft.server.players;

import com.google.gson.JsonObject;

public class UserWhiteListEntry extends StoredUserEntry<NameAndId> {
	public UserWhiteListEntry(NameAndId nameAndId) {
		super(nameAndId);
	}

	public UserWhiteListEntry(JsonObject jsonObject) {
		super(NameAndId.fromJson(jsonObject));
	}

	@Override
	protected void serialize(JsonObject jsonObject) {
		if (this.getUser() != null) {
			this.getUser().appendTo(jsonObject);
		}
	}
}
