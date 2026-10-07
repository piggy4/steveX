package net.minecraft.client.model.geom.builders;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

@Environment(EnvType.CLIENT)
public class PartDefinition {
	private final List<CubeDefinition> cubes;
	private final PartPose partPose;
	private final Map<String, PartDefinition> children = Maps.newHashMap();

	PartDefinition(List<CubeDefinition> list, PartPose partPose) {
		this.cubes = list;
		this.partPose = partPose;
	}

	public PartDefinition addOrReplaceChild(String string, CubeListBuilder cubeListBuilder, PartPose partPose) {
		PartDefinition partDefinition = new PartDefinition(cubeListBuilder.getCubes(), partPose);
		return this.addOrReplaceChild(string, partDefinition);
	}

	public PartDefinition addOrReplaceChild(String string, PartDefinition partDefinition) {
		PartDefinition partDefinition2 = this.children.put(string, partDefinition);
		if (partDefinition2 != null) {
			partDefinition.children.putAll(partDefinition2.children);
		}

		return partDefinition;
	}

	public PartDefinition clearRecursively() {
		for (String string : this.children.keySet()) {
			this.clearChild(string).clearRecursively();
		}

		return this;
	}

	public PartDefinition clearChild(String string) {
		PartDefinition partDefinition = this.children.get(string);
		if (partDefinition == null) {
			throw new IllegalArgumentException("No child with name: " + string);
		} else {
			return this.addOrReplaceChild(string, CubeListBuilder.create(), partDefinition.partPose);
		}
	}

	public void retainPartsAndChildren(Set<String> set) {
		for (Entry<String, PartDefinition> entry : this.children.entrySet()) {
			PartDefinition partDefinition = entry.getValue();
			if (!set.contains(entry.getKey())) {
				this.addOrReplaceChild(entry.getKey(), CubeListBuilder.create(), partDefinition.partPose).retainPartsAndChildren(set);
			}
		}
	}

	public void retainExactParts(Set<String> set) {
		for (Entry<String, PartDefinition> entry : this.children.entrySet()) {
			PartDefinition partDefinition = entry.getValue();
			if (set.contains(entry.getKey())) {
				partDefinition.clearRecursively();
			} else {
				this.addOrReplaceChild(entry.getKey(), CubeListBuilder.create(), partDefinition.partPose).retainExactParts(set);
			}
		}
	}

	public ModelPart bake(int i, int j) {
		Object2ObjectArrayMap<String, ModelPart> object2ObjectArrayMap = this.children
			.entrySet()
			.stream()
			.collect(
				Collectors.toMap(Entry::getKey, entry -> ((PartDefinition)entry.getValue()).bake(i, j), (modelPartx, modelPart2) -> modelPartx, Object2ObjectArrayMap::new)
			);
		List<ModelPart.Cube> list = this.cubes.stream().map(cubeDefinition -> cubeDefinition.bake(i, j)).toList();
		ModelPart modelPart = new ModelPart(list, object2ObjectArrayMap);
		modelPart.setInitialPose(this.partPose);
		modelPart.loadPose(this.partPose);
		return modelPart;
	}

	public PartDefinition getChild(String string) {
		return this.children.get(string);
	}

	public Set<Entry<String, PartDefinition>> getChildren() {
		return this.children.entrySet();
	}

	public PartDefinition transformed(UnaryOperator<PartPose> unaryOperator) {
		PartDefinition partDefinition = new PartDefinition(this.cubes, unaryOperator.apply(this.partPose));
		partDefinition.children.putAll(this.children);
		return partDefinition;
	}
}
