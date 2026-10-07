package net.minecraft.client.resources.model;

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

@Environment(EnvType.CLIENT)
public class ModelGroupCollector {
	static final int SINGLETON_MODEL_GROUP = -1;
	private static final int INVISIBLE_MODEL_GROUP = 0;

	public static Object2IntMap<BlockState> build(BlockColors blockColors, BlockStateModelLoader.LoadedModels loadedModels) {
		Map<Block, List<Property<?>>> map = new HashMap<>();
		Map<ModelGroupCollector.GroupKey, Set<BlockState>> map2 = new HashMap<>();
		loadedModels.models().forEach((blockState, unbakedRoot) -> {
			List<Property<?>> list = map.computeIfAbsent(blockState.getBlock(), block -> List.copyOf(blockColors.getColoringProperties(block)));
			ModelGroupCollector.GroupKey groupKey = ModelGroupCollector.GroupKey.create(blockState, unbakedRoot, list);
			map2.computeIfAbsent(groupKey, groupKeyx -> Sets.newIdentityHashSet()).add(blockState);
		});
		int i = 1;
		Object2IntMap<BlockState> object2IntMap = new Object2IntOpenHashMap<>();
		object2IntMap.defaultReturnValue(-1);

		for (Set<BlockState> set : map2.values()) {
			Iterator<BlockState> iterator = set.iterator();

			while (iterator.hasNext()) {
				BlockState blockState = iterator.next();
				if (blockState.getRenderShape() != RenderShape.MODEL) {
					iterator.remove();
					object2IntMap.put(blockState, 0);
				}
			}

			if (set.size() > 1) {
				int j = i++;
				set.forEach(blockState -> object2IntMap.put(blockState, j));
			}
		}

		return object2IntMap;
	}

	@Environment(EnvType.CLIENT)
	record GroupKey(Object equalityGroup, List<Object> coloringValues) {
		public static ModelGroupCollector.GroupKey create(BlockState blockState, BlockStateModel.UnbakedRoot unbakedRoot, List<Property<?>> list) {
			List<Object> list2 = getColoringValues(blockState, list);
			Object object = unbakedRoot.visualEqualityGroup(blockState);
			return new ModelGroupCollector.GroupKey(object, list2);
		}

		private static List<Object> getColoringValues(BlockState blockState, List<Property<?>> list) {
			Object[] objects = new Object[list.size()];

			for (int i = 0; i < list.size(); i++) {
				objects[i] = blockState.getValue(list.get(i));
			}

			return List.of(objects);
		}
	}
}
