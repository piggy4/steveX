package net.minecraft.server.permissions;

import java.util.function.Predicate;

public record PermissionProviderCheck<T extends PermissionSetSupplier>(PermissionCheck test) implements Predicate<T> {
	public boolean test(T permissionSetSupplier) {
		return this.test.check(permissionSetSupplier.permissions());
	}
}
