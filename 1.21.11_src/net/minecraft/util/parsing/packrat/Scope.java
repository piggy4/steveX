package net.minecraft.util.parsing.packrat;

import com.google.common.annotations.VisibleForTesting;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class Scope {
	private static final int NOT_FOUND = -1;
	private static final Object FRAME_START_MARKER = new Object() {
		@Override
		public String toString() {
			return "frame";
		}
	};
	private static final int ENTRY_STRIDE = 2;
	private @Nullable Object[] stack = new Object[128];
	private int topEntryKeyIndex = 0;
	private int topMarkerKeyIndex = 0;

	public Scope() {
		this.stack[0] = FRAME_START_MARKER;
		this.stack[1] = null;
	}

	private int valueIndex(Atom<?> atom) {
		for (int i = this.topEntryKeyIndex; i > this.topMarkerKeyIndex; i -= 2) {
			Object object = this.stack[i];
			assert object instanceof Atom;
			if (object == atom) {
				return i + 1;
			}
		}

		return -1;
	}

	public int valueIndexForAny(Atom<?>... atoms) {
		for (int i = this.topEntryKeyIndex; i > this.topMarkerKeyIndex; i -= 2) {
			Object object = this.stack[i];
			assert object instanceof Atom;

			for (Atom<?> atom : atoms) {
				if (atom == object) {
					return i + 1;
				}
			}
		}

		return -1;
	}

	private void ensureCapacity(int i) {
		int j = this.stack.length;
		int k = this.topEntryKeyIndex + 1;
		int l = k + i * 2;
		if (l >= j) {
			int m = Util.growByHalf(j, l + 1);
			Object[] objects = new Object[m];
			System.arraycopy(this.stack, 0, objects, 0, j);
			this.stack = objects;
		}

		assert this.validateStructure();
	}

	private void setupNewFrame() {
		this.topEntryKeyIndex += 2;
		this.stack[this.topEntryKeyIndex] = FRAME_START_MARKER;
		this.stack[this.topEntryKeyIndex + 1] = this.topMarkerKeyIndex;
		this.topMarkerKeyIndex = this.topEntryKeyIndex;
	}

	public void pushFrame() {
		this.ensureCapacity(1);
		this.setupNewFrame();
		assert this.validateStructure();
	}

	private int getPreviousMarkerIndex(int i) {
		return (Integer)this.stack[i + 1];
	}

	public void popFrame() {
		assert this.topMarkerKeyIndex != 0;
		this.topEntryKeyIndex = this.topMarkerKeyIndex - 2;
		this.topMarkerKeyIndex = this.getPreviousMarkerIndex(this.topMarkerKeyIndex);
		assert this.validateStructure();
	}

	public void splitFrame() {
		int i = this.topMarkerKeyIndex;
		int j = (this.topEntryKeyIndex - this.topMarkerKeyIndex) / 2;
		this.ensureCapacity(j + 1);
		this.setupNewFrame();
		int k = i + 2;
		int l = this.topEntryKeyIndex;

		for (int m = 0; m < j; m++) {
			l += 2;
			Object object = this.stack[k];
			assert object != null;
			this.stack[l] = object;
			this.stack[l + 1] = null;
			k += 2;
		}

		this.topEntryKeyIndex = l;
		assert this.validateStructure();
	}

	public void clearFrameValues() {
		for (int i = this.topEntryKeyIndex; i > this.topMarkerKeyIndex; i -= 2) {
			assert this.stack[i] instanceof Atom;
			this.stack[i + 1] = null;
		}

		assert this.validateStructure();
	}

	public void mergeFrame() {
		int i = this.getPreviousMarkerIndex(this.topMarkerKeyIndex);
		int j = i;
		int k = this.topMarkerKeyIndex;

		while (k < this.topEntryKeyIndex) {
			j += 2;
			k += 2;
			Object object = this.stack[k];
			assert object instanceof Atom;
			Object object2 = this.stack[k + 1];
			Object object3 = this.stack[j];
			if (object3 != object) {
				this.stack[j] = object;
				this.stack[j + 1] = object2;
			} else if (object2 != null) {
				this.stack[j + 1] = object2;
			}
		}

		this.topEntryKeyIndex = j;
		this.topMarkerKeyIndex = i;
		assert this.validateStructure();
	}

	public <T> void put(Atom<T> atom, @Nullable T object) {
		int i = this.valueIndex(atom);
		if (i != -1) {
			this.stack[i] = object;
		} else {
			this.ensureCapacity(1);
			this.topEntryKeyIndex += 2;
			this.stack[this.topEntryKeyIndex] = atom;
			this.stack[this.topEntryKeyIndex + 1] = object;
		}

		assert this.validateStructure();
	}

	public <T> @Nullable T get(Atom<T> atom) {
		int i = this.valueIndex(atom);
		return (T)(i != -1 ? this.stack[i] : null);
	}

	public <T> T getOrThrow(Atom<T> atom) {
		int i = this.valueIndex(atom);
		if (i == -1) {
			throw new IllegalArgumentException("No value for atom " + atom);
		} else {
			return (T)this.stack[i];
		}
	}

	public <T> T getOrDefault(Atom<T> atom, T object) {
		int i = this.valueIndex(atom);
		return (T)(i != -1 ? this.stack[i] : object);
	}

	@SafeVarargs
	public final <T> @Nullable T getAny(Atom<? extends T>... atoms) {
		int i = this.valueIndexForAny(atoms);
		return (T)(i != -1 ? this.stack[i] : null);
	}

	@SafeVarargs
	public final <T> T getAnyOrThrow(Atom<? extends T>... atoms) {
		int i = this.valueIndexForAny(atoms);
		if (i == -1) {
			throw new IllegalArgumentException("No value for atoms " + Arrays.toString(atoms));
		} else {
			return (T)this.stack[i];
		}
	}

	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		boolean bl = true;

		for (int i = 0; i <= this.topEntryKeyIndex; i += 2) {
			Object object = this.stack[i];
			Object object2 = this.stack[i + 1];
			if (object == FRAME_START_MARKER) {
				stringBuilder.append('|');
				bl = true;
			} else {
				if (!bl) {
					stringBuilder.append(',');
				}

				bl = false;
				stringBuilder.append(object).append(':').append(object2);
			}
		}

		return stringBuilder.toString();
	}

	@VisibleForTesting
	public Map<Atom<?>, ?> lastFrame() {
		HashMap<Atom<?>, Object> hashMap = new HashMap<>();

		for (int i = this.topEntryKeyIndex; i > this.topMarkerKeyIndex; i -= 2) {
			Object object = this.stack[i];
			Object object2 = this.stack[i + 1];
			hashMap.put((Atom<?>)object, object2);
		}

		return hashMap;
	}

	public boolean hasOnlySingleFrame() {
		for (int i = this.topEntryKeyIndex; i > 0; i--) {
			if (this.stack[i] == FRAME_START_MARKER) {
				return false;
			}
		}

		if (this.stack[0] != FRAME_START_MARKER) {
			throw new IllegalStateException("Corrupted stack");
		} else {
			return true;
		}
	}

	private boolean validateStructure() {
		assert this.topMarkerKeyIndex >= 0;
		assert this.topEntryKeyIndex >= this.topMarkerKeyIndex;

		for (int i = 0; i <= this.topEntryKeyIndex; i += 2) {
			Object object = this.stack[i];
			if (object != FRAME_START_MARKER && !(object instanceof Atom)) {
				return false;
			}
		}

		for (int i = this.topMarkerKeyIndex; i != 0; i = this.getPreviousMarkerIndex(i)) {
			Object object = this.stack[i];
			if (object != FRAME_START_MARKER) {
				return false;
			}
		}

		return true;
	}
}
