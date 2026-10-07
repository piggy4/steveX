package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public abstract class BanListEntry<T> extends StoredUserEntry<T> {
	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
	public static final String EXPIRES_NEVER = "forever";
	protected final Date created;
	protected final String source;
	protected final @Nullable Date expires;
	protected final @Nullable String reason;

	public BanListEntry(@Nullable T object, @Nullable Date date, @Nullable String string, @Nullable Date date2, @Nullable String string2) {
		super(object);
		this.created = date == null ? new Date() : date;
		this.source = string == null ? "(Unknown)" : string;
		this.expires = date2;
		this.reason = string2;
	}

	protected BanListEntry(@Nullable T object, JsonObject jsonObject) {
		super(object);

		Date date;
		try {
			date = jsonObject.has("created") ? DATE_FORMAT.parse(jsonObject.get("created").getAsString()) : new Date();
		} catch (ParseException parseException) {
			date = new Date();
		}

		this.created = date;
		this.source = jsonObject.has("source") ? jsonObject.get("source").getAsString() : "(Unknown)";

		Date date2;
		try {
			date2 = jsonObject.has("expires") ? DATE_FORMAT.parse(jsonObject.get("expires").getAsString()) : null;
		} catch (ParseException parseException2) {
			date2 = null;
		}

		this.expires = date2;
		this.reason = jsonObject.has("reason") ? jsonObject.get("reason").getAsString() : null;
	}

	public Date getCreated() {
		return this.created;
	}

	public String getSource() {
		return this.source;
	}

	public @Nullable Date getExpires() {
		return this.expires;
	}

	public @Nullable String getReason() {
		return this.reason;
	}

	public Component getReasonMessage() {
		String string = this.getReason();
		return string == null ? Component.translatable("multiplayer.disconnect.banned.reason.default") : Component.literal(string);
	}

	public abstract Component getDisplayName();

	@Override
	boolean hasExpired() {
		return this.expires == null ? false : this.expires.before(new Date());
	}

	@Override
	protected void serialize(JsonObject jsonObject) {
		jsonObject.addProperty("created", DATE_FORMAT.format(this.created));
		jsonObject.addProperty("source", this.source);
		jsonObject.addProperty("expires", this.expires == null ? "forever" : DATE_FORMAT.format(this.expires));
		jsonObject.addProperty("reason", this.reason);
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		} else if (object != null && this.getClass() == object.getClass()) {
			BanListEntry<?> banListEntry = (BanListEntry<?>)object;
			return Objects.equals(this.source, banListEntry.source)
				&& Objects.equals(this.expires, banListEntry.expires)
				&& Objects.equals(this.reason, banListEntry.reason)
				&& Objects.equals(this.getUser(), banListEntry.getUser());
		} else {
			return false;
		}
	}
}
