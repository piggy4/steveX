package net.minecraft.server.permissions;

public interface PermissionSet {
	PermissionSet NO_PERMISSIONS = permission -> false;
	PermissionSet ALL_PERMISSIONS = permission -> true;

	boolean hasPermission(Permission permission);

	default PermissionSet union(PermissionSet permissionSet) {
		return permissionSet instanceof PermissionSetUnion ? permissionSet.union(this) : new PermissionSetUnion(this, permissionSet);
	}
}
