package net.minecraft.client.model.geom;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.builders.LayerDefinition;

@Environment(EnvType.CLIENT)
public class EntityModelSet {
	public static final EntityModelSet EMPTY = new EntityModelSet(Map.of());
	private final Map<ModelLayerLocation, LayerDefinition> roots;

	public EntityModelSet(Map<ModelLayerLocation, LayerDefinition> map) {
		this.roots = map;
	}

	public ModelPart bakeLayer(ModelLayerLocation modelLayerLocation) {
		LayerDefinition layerDefinition = this.roots.get(modelLayerLocation);
		if (layerDefinition == null) {
			throw new IllegalArgumentException("No model for layer " + modelLayerLocation);
		} else {
			return layerDefinition.bakeRoot();
		}
	}

	public static EntityModelSet vanilla() {
		return new EntityModelSet(ImmutableMap.copyOf(LayerDefinitions.createRoots()));
	}
}
