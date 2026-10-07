package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.util.Date;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class UserBanListEntry extends BanListEntry<NameAndId> {
	private static final Component MESSAGE_UNKNOWN_USER = Component.translatable("commands.banlist.entry.unknown");

	public UserBanListEntry(@Nullable NameAndId nameAndId) {
		this(nameAndId, null, null, null, null);
	}

	public UserBanListEntry(@Nullable NameAndId nameAndId, @Nullable Date date, @Nullable String string, @Nullable Date date2, @Nullable String string2) {
		super(nameAndId, date, string, date2, string2);
	}

	public UserBanListEntry(JsonObject jsonObject) {
		super(NameAndId.fromJson(jsonObject), jsonObject);
	}

	@Override
	protected void serialize(JsonObject jsonObject) {
		if (this.getUser() != null) {
			this.getUser().appendTo(jsonObject);
			super.serialize(jsonObject);
		}
	}

	@Override
	public Component getDisplayName() {
		NameAndId nameAndId = this.getUser();
		return nameAndId != null ? Component.literal(nameAndId.name()) : MESSAGE_UNKNOWN_USER;
	}
}
