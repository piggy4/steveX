package net.minecraft.server.permissions;

public interface LevelBasedPermissionSet extends PermissionSet {
	@Deprecated
	LevelBasedPermissionSet ALL = create(PermissionLevel.ALL);
	LevelBasedPermissionSet MODERATOR = create(PermissionLevel.MODERATORS);
	LevelBasedPermissionSet GAMEMASTER = create(PermissionLevel.GAMEMASTERS);
	LevelBasedPermissionSet ADMIN = create(PermissionLevel.ADMINS);
	LevelBasedPermissionSet OWNER = create(PermissionLevel.OWNERS);

	PermissionLevel level();

	@Override
	default boolean hasPermission(Permission permission) {
		if (permission instanceof Permission.HasCommandLevel hasCommandLevel) {
			return this.level().isEqualOrHigherThan(hasCommandLevel.level());
		} else {
			return permission.equals(Permissions.COMMANDS_ENTITY_SELECTORS) ? this.level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS) : false;
		}
	}

	@Override
	default PermissionSet union(PermissionSet permissionSet) {
		if (permissionSet instanceof LevelBasedPermissionSet levelBasedPermissionSet) {
			return this.level().isEqualOrHigherThan(levelBasedPermissionSet.level()) ? levelBasedPermissionSet : this;
		} else {
			return PermissionSet.super.union(permissionSet);
		}
	}

	static LevelBasedPermissionSet forLevel(PermissionLevel permissionLevel) {
		return switch (permissionLevel) {
			case ALL -> ALL;
			case MODERATORS -> MODERATOR;
			case GAMEMASTERS -> GAMEMASTER;
			case ADMINS -> ADMIN;
			case OWNERS -> OWNER;
		};
	}

	private static LevelBasedPermissionSet create(PermissionLevel permissionLevel) {
		return new LevelBasedPermissionSet() {
			@Override
			public PermissionLevel level() {
				return permissionLevel;
			}

			@Override
			public String toString() {
				return "permission level: " + permissionLevel.name();
			}
		};
	}
}
