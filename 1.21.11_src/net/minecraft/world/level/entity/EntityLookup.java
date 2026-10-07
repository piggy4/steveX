package net.minecraft.world.level.entity;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.AbortableIterationConsumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class EntityLookup<T extends EntityAccess> {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Int2ObjectMap<T> byId = new Int2ObjectLinkedOpenHashMap<>();
	private final Map<UUID, T> byUuid = Maps.newHashMap();

	public <U extends T> void getEntities(EntityTypeTest<T, U> entityTypeTest, AbortableIterationConsumer<U> abortableIterationConsumer) {
		for (T entityAccess : this.byId.values()) {
			U entityAccess2 = (U)entityTypeTest.tryCast(entityAccess);
			if (entityAccess2 != null && abortableIterationConsumer.accept(entityAccess2).shouldAbort()) {
				return;
			}
		}
	}

	public Iterable<T> getAllEntities() {
		return Iterables.unmodifiableIterable(this.byId.values());
	}

	public void add(T entityAccess) {
		UUID uUID = entityAccess.getUUID();
		if (this.byUuid.containsKey(uUID)) {
			LOGGER.warn("Duplicate entity UUID {}: {}", uUID, entityAccess);
		} else {
			this.byUuid.put(uUID, entityAccess);
			this.byId.put(entityAccess.getId(), entityAccess);
		}
	}

	public void remove(T entityAccess) {
		this.byUuid.remove(entityAccess.getUUID());
		this.byId.remove(entityAccess.getId());
	}

	public @Nullable T getEntity(int i) {
		return this.byId.get(i);
	}

	public @Nullable T getEntity(UUID uUID) {
		return this.byUuid.get(uUID);
	}

	public int count() {
		return this.byUuid.size();
	}
}
