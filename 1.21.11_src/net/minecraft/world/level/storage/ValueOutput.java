package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.serialization.v1.view.FabricWriteView;
import org.jspecify.annotations.Nullable;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.serialization.v1.view.FabricWriteView} injected by mod fabric-serialization-api-v1</p>
 */
public interface ValueOutput extends FabricWriteView {
	<T> void store(String string, Codec<T> codec, T object);

	<T> void storeNullable(String string, Codec<T> codec, @Nullable T object);

	@Deprecated
	<T> void store(MapCodec<T> mapCodec, T object);

	void putBoolean(String string, boolean bl);

	void putByte(String string, byte b);

	void putShort(String string, short s);

	void putInt(String string, int i);

	void putLong(String string, long l);

	void putFloat(String string, float f);

	void putDouble(String string, double d);

	void putString(String string, String string2);

	void putIntArray(String string, int[] is);

	ValueOutput child(String string);

	ValueOutput.ValueOutputList childrenList(String string);

	<T> ValueOutput.TypedOutputList<T> list(String string, Codec<T> codec);

	void discard(String string);

	boolean isEmpty();

	interface TypedOutputList<T> {
		void add(T object);

		boolean isEmpty();
	}

	interface ValueOutputList {
		ValueOutput addChild();

		void discardLast();

		boolean isEmpty();
	}
}
