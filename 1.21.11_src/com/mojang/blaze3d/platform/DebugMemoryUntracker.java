package com.mojang.blaze3d.platform;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.Pointer;

@Environment(EnvType.CLIENT)
public class DebugMemoryUntracker {
	private static final @Nullable MethodHandle UNTRACK = GLX.make(() -> {
		try {
			Lookup lookup = MethodHandles.lookup();
			Class<?> class_ = Class.forName("org.lwjgl.system.MemoryManage$DebugAllocator");
			Method method = class_.getDeclaredMethod("untrack", long.class);
			method.setAccessible(true);
			Field field = Class.forName("org.lwjgl.system.MemoryUtil$LazyInit").getDeclaredField("ALLOCATOR");
			field.setAccessible(true);
			Object object = field.get(null);
			return class_.isInstance(object) ? lookup.unreflect(method) : null;
		} catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException | IllegalAccessException reflectiveOperationException) {
			throw new RuntimeException(reflectiveOperationException);
		}
	});

	public static void untrack(long l) {
		if (UNTRACK != null) {
			try {
				UNTRACK.invoke((long)l);
			} catch (Throwable throwable) {
				throw new RuntimeException(throwable);
			}
		}
	}

	public static void untrack(Pointer pointer) {
		untrack(pointer.address());
	}
}
