package net.minecraft.client.renderer.entity;

import com.google.common.collect.ImmutableMap.Builder;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.EquipmentSlot;

@Environment(EnvType.CLIENT)
public record ArmorModelSet<T>(T head, T chest, T legs, T feet) {
	public T get(EquipmentSlot equipmentSlot) {
		return (T)(switch (equipmentSlot) {
			case HEAD -> (Object)this.head;
			case CHEST -> (Object)this.chest;
			case LEGS -> (Object)this.legs;
			case FEET -> (Object)this.feet;
			default -> throw new IllegalStateException("No model for slot: " + equipmentSlot);
		});
	}

	public <U> ArmorModelSet<U> map(Function<? super T, ? extends U> function) {
		return (ArmorModelSet<U>)(new ArmorModelSet<>(function.apply(this.head), function.apply(this.chest), function.apply(this.legs), function.apply(this.feet)));
	}

	public void putFrom(ArmorModelSet<LayerDefinition> armorModelSet, Builder<T, LayerDefinition> builder) {
		builder.put(this.head, armorModelSet.head);
		builder.put(this.chest, armorModelSet.chest);
		builder.put(this.legs, armorModelSet.legs);
		builder.put(this.feet, armorModelSet.feet);
	}

	public static <M extends HumanoidModel<?>> ArmorModelSet<M> bake(
		ArmorModelSet<ModelLayerLocation> armorModelSet, EntityModelSet entityModelSet, Function<ModelPart, M> function
	) {
		return armorModelSet.map(modelLayerLocation -> function.apply(entityModelSet.bakeLayer(modelLayerLocation)));
	}
}
