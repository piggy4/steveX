package net.minecraft.world.entity;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.Set;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public interface OwnableEntity {
	@Nullable EntityReference<LivingEntity> getOwnerReference();

	Level level();

	default @Nullable LivingEntity getOwner() {
		return EntityReference.getLivingEntity(this.getOwnerReference(), this.level());
	}

	default @Nullable LivingEntity getRootOwner() {
		Set<Object> set = new ObjectArraySet<>();
		LivingEntity livingEntity = this.getOwner();
		set.add(this);

		while (livingEntity instanceof OwnableEntity) {
			OwnableEntity ownableEntity = (OwnableEntity)livingEntity;
			LivingEntity livingEntity2 = ownableEntity.getOwner();
			if (set.contains(livingEntity2)) {
				return null;
			}

			set.add(livingEntity);
			livingEntity = ownableEntity.getOwner();
		}

		return livingEntity;
	}
}
