package net.minecraft.server.players;

import com.google.gson.JsonObject;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;

public class ServerOpListEntry extends StoredUserEntry<NameAndId> {
	private final LevelBasedPermissionSet permissions;
	private final boolean bypassesPlayerLimit;

	public ServerOpListEntry(NameAndId nameAndId, LevelBasedPermissionSet levelBasedPermissionSet, boolean bl) {
		super(nameAndId);
		this.permissions = levelBasedPermissionSet;
		this.bypassesPlayerLimit = bl;
	}

	public ServerOpListEntry(JsonObject jsonObject) {
		super(NameAndId.fromJson(jsonObject));
		PermissionLevel permissionLevel = jsonObject.has("level") ? PermissionLevel.byId(jsonObject.get("level").getAsInt()) : PermissionLevel.ALL;
		this.permissions = LevelBasedPermissionSet.forLevel(permissionLevel);
		this.bypassesPlayerLimit = jsonObject.has("bypassesPlayerLimit") && jsonObject.get("bypassesPlayerLimit").getAsBoolean();
	}

	public LevelBasedPermissionSet permissions() {
		return this.permissions;
	}

	public boolean getBypassesPlayerLimit() {
		return this.bypassesPlayerLimit;
	}

	@Override
	protected void serialize(JsonObject jsonObject) {
		if (this.getUser() != null) {
			this.getUser().appendTo(jsonObject);
			jsonObject.addProperty("level", this.permissions.level().id());
			jsonObject.addProperty("bypassesPlayerLimit", this.bypassesPlayerLimit);
		}
	}
}
