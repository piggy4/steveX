package net.minecraft.client.resources.model;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class QuadCollection {
	public static final QuadCollection EMPTY = new QuadCollection(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
	private final List<BakedQuad> all;
	private final List<BakedQuad> unculled;
	private final List<BakedQuad> north;
	private final List<BakedQuad> south;
	private final List<BakedQuad> east;
	private final List<BakedQuad> west;
	private final List<BakedQuad> up;
	private final List<BakedQuad> down;

	QuadCollection(
		List<BakedQuad> list,
		List<BakedQuad> list2,
		List<BakedQuad> list3,
		List<BakedQuad> list4,
		List<BakedQuad> list5,
		List<BakedQuad> list6,
		List<BakedQuad> list7,
		List<BakedQuad> list8
	) {
		this.all = list;
		this.unculled = list2;
		this.north = list3;
		this.south = list4;
		this.east = list5;
		this.west = list6;
		this.up = list7;
		this.down = list8;
	}

	public List<BakedQuad> getQuads(@Nullable Direction direction) {
		return switch (direction) {
			case null -> this.unculled;
			case NORTH -> this.north;
			case SOUTH -> this.south;
			case EAST -> this.east;
			case WEST -> this.west;
			case UP -> this.up;
			case DOWN -> this.down;
		};
	}

	public List<BakedQuad> getAll() {
		return this.all;
	}

	@Environment(EnvType.CLIENT)
	public static class Builder {
		private final ImmutableList.Builder<BakedQuad> unculledFaces = ImmutableList.builder();
		private final Multimap<Direction, BakedQuad> culledFaces = ArrayListMultimap.create();

		public QuadCollection.Builder addCulledFace(Direction direction, BakedQuad bakedQuad) {
			this.culledFaces.put(direction, bakedQuad);
			return this;
		}

		public QuadCollection.Builder addUnculledFace(BakedQuad bakedQuad) {
			this.unculledFaces.add(bakedQuad);
			return this;
		}

		private static QuadCollection createFromSublists(List<BakedQuad> list, int i, int j, int k, int l, int m, int n, int o) {
			int p = 0;
			int var16;
			List<BakedQuad> list2 = list.subList(p, var16 = p + i);
			List<BakedQuad> list3 = list.subList(var16, p = var16 + j);
			int var18;
			List<BakedQuad> list4 = list.subList(p, var18 = p + k);
			List<BakedQuad> list5 = list.subList(var18, p = var18 + l);
			int var20;
			List<BakedQuad> list6 = list.subList(p, var20 = p + m);
			List<BakedQuad> list7 = list.subList(var20, p = var20 + n);
			List<BakedQuad> list8 = list.subList(p, p + o);
			return new QuadCollection(list, list2, list3, list4, list5, list6, list7, list8);
		}

		public QuadCollection build() {
			ImmutableList<BakedQuad> immutableList = this.unculledFaces.build();
			if (this.culledFaces.isEmpty()) {
				return immutableList.isEmpty()
					? QuadCollection.EMPTY
					: new QuadCollection(immutableList, immutableList, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
			}

			ImmutableList.Builder<BakedQuad> builder = ImmutableList.builder();
			builder.addAll(immutableList);
			Collection<BakedQuad> collection = this.culledFaces.get(Direction.NORTH);
			builder.addAll(collection);
			Collection<BakedQuad> collection2 = this.culledFaces.get(Direction.SOUTH);
			builder.addAll(collection2);
			Collection<BakedQuad> collection3 = this.culledFaces.get(Direction.EAST);
			builder.addAll(collection3);
			Collection<BakedQuad> collection4 = this.culledFaces.get(Direction.WEST);
			builder.addAll(collection4);
			Collection<BakedQuad> collection5 = this.culledFaces.get(Direction.UP);
			builder.addAll(collection5);
			Collection<BakedQuad> collection6 = this.culledFaces.get(Direction.DOWN);
			builder.addAll(collection6);
			return createFromSublists(
				builder.build(),
				immutableList.size(),
				collection.size(),
				collection2.size(),
				collection3.size(),
				collection4.size(),
				collection5.size(),
				collection6.size()
			);
		}
	}
}
