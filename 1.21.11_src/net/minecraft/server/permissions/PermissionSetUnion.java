package net.minecraft.server.permissions;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

public class PermissionSetUnion implements PermissionSet {
	private final ReferenceSet<PermissionSet> permissions = new ReferenceArraySet<>();

	PermissionSetUnion(PermissionSet permissionSet, PermissionSet permissionSet2) {
		this.permissions.add(permissionSet);
		this.permissions.add(permissionSet2);
		this.ensureNoUnionsWithinUnions();
	}

	private PermissionSetUnion(ReferenceSet<PermissionSet> referenceSet, PermissionSet permissionSet) {
		this.permissions.addAll(referenceSet);
		this.permissions.add(permissionSet);
		this.ensureNoUnionsWithinUnions();
	}

	private PermissionSetUnion(ReferenceSet<PermissionSet> referenceSet, ReferenceSet<PermissionSet> referenceSet2) {
		this.permissions.addAll(referenceSet);
		this.permissions.addAll(referenceSet2);
		this.ensureNoUnionsWithinUnions();
	}

	@Override
	public boolean hasPermission(Permission permission) {
		for (PermissionSet permissionSet : this.permissions) {
			if (permissionSet.hasPermission(permission)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public PermissionSet union(PermissionSet permissionSet) {
		return permissionSet instanceof PermissionSetUnion permissionSetUnion
			? new PermissionSetUnion(this.permissions, permissionSetUnion.permissions)
			: new PermissionSetUnion(this.permissions, permissionSet);
	}

	@VisibleForTesting
	public ReferenceSet<PermissionSet> getPermissions() {
		return new ReferenceArraySet<>(this.permissions);
	}

	private void ensureNoUnionsWithinUnions() {
		for (PermissionSet permissionSet : this.permissions) {
			if (permissionSet instanceof PermissionSetUnion) {
				throw new IllegalArgumentException("Cannot have PermissionSetUnion within another PermissionSetUnion");
			}
		}
	}
}
