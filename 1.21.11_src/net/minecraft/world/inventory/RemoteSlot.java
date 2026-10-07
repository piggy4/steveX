package net.minecraft.world.inventory;

import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public interface RemoteSlot {
	RemoteSlot PLACEHOLDER = new RemoteSlot() {
		@Override
		public void receive(HashedStack hashedStack) {
		}

		@Override
		public void force(ItemStack itemStack) {
		}

		@Override
		public boolean matches(ItemStack itemStack) {
			return true;
		}
	};

	void force(ItemStack itemStack);

	void receive(HashedStack hashedStack);

	boolean matches(ItemStack itemStack);

	class Synchronized implements RemoteSlot {
		private final HashedPatchMap.HashGenerator hasher;
		private @Nullable ItemStack remoteStack = null;
		private @Nullable HashedStack remoteHash = null;

		public Synchronized(HashedPatchMap.HashGenerator hashGenerator) {
			this.hasher = hashGenerator;
		}

		@Override
		public void force(ItemStack itemStack) {
			this.remoteStack = itemStack.copy();
			this.remoteHash = null;
		}

		@Override
		public void receive(HashedStack hashedStack) {
			this.remoteStack = null;
			this.remoteHash = hashedStack;
		}

		@Override
		public boolean matches(ItemStack itemStack) {
			if (this.remoteStack != null) {
				return ItemStack.matches(this.remoteStack, itemStack);
			} else if (this.remoteHash != null && this.remoteHash.matches(itemStack, this.hasher)) {
				this.remoteStack = itemStack.copy();
				return true;
			} else {
				return false;
			}
		}

		public void copyFrom(RemoteSlot.Synchronized synchronized_) {
			this.remoteStack = synchronized_.remoteStack;
			this.remoteHash = synchronized_.remoteHash;
		}
	}
}
