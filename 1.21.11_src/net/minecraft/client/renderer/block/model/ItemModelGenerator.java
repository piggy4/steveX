package net.minecraft.client.renderer.block.model;

import com.mojang.math.Quadrant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.client.resources.model.UnbakedGeometry;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ItemModelGenerator implements UnbakedModel {
	public static final Identifier GENERATED_ITEM_MODEL_ID = Identifier.withDefaultNamespace("builtin/generated");
	public static final List<String> LAYERS = List.of("layer0", "layer1", "layer2", "layer3", "layer4");
	private static final float MIN_Z = 7.5F;
	private static final float MAX_Z = 8.5F;
	private static final TextureSlots.Data TEXTURE_SLOTS = new TextureSlots.Data.Builder().addReference("particle", "layer0").build();
	private static final BlockElementFace.UVs SOUTH_FACE_UVS = new BlockElementFace.UVs(0.0F, 0.0F, 16.0F, 16.0F);
	private static final BlockElementFace.UVs NORTH_FACE_UVS = new BlockElementFace.UVs(16.0F, 0.0F, 0.0F, 16.0F);
	private static final float UV_SHRINK = 0.1F;

	@Override
	public TextureSlots.Data textureSlots() {
		return TEXTURE_SLOTS;
	}

	@Override
	public UnbakedGeometry geometry() {
		return ItemModelGenerator::bake;
	}

	@Override
	public UnbakedModel.@Nullable GuiLight guiLight() {
		return UnbakedModel.GuiLight.FRONT;
	}

	private static QuadCollection bake(TextureSlots textureSlots, ModelBaker modelBaker, ModelState modelState, ModelDebugName modelDebugName) {
		List<BlockElement> list = new ArrayList<>();

		for (int i = 0; i < LAYERS.size(); i++) {
			String string = LAYERS.get(i);
			Material material = textureSlots.getMaterial(string);
			if (material == null) {
				break;
			}

			SpriteContents spriteContents = modelBaker.sprites().get(material, modelDebugName).contents();
			list.addAll(processFrames(i, string, spriteContents));
		}

		return SimpleUnbakedGeometry.bake(list, textureSlots, modelBaker, modelState, modelDebugName);
	}

	private static List<BlockElement> processFrames(int i, String string, SpriteContents spriteContents) {
		Map<Direction, BlockElementFace> map = Map.of(
			Direction.SOUTH,
			new BlockElementFace(null, i, string, SOUTH_FACE_UVS, Quadrant.R0),
			Direction.NORTH,
			new BlockElementFace(null, i, string, NORTH_FACE_UVS, Quadrant.R0)
		);
		List<BlockElement> list = new ArrayList<>();
		list.add(new BlockElement(new Vector3f(0.0F, 0.0F, 7.5F), new Vector3f(16.0F, 16.0F, 8.5F), map));
		list.addAll(createSideElements(spriteContents, string, i));
		return list;
	}

	private static List<BlockElement> createSideElements(SpriteContents spriteContents, String string, int i) {
		float f = 16.0F / spriteContents.width();
		float g = 16.0F / spriteContents.height();
		List<BlockElement> list = new ArrayList<>();

		for (ItemModelGenerator.SideFace sideFace : getSideFaces(spriteContents)) {
			float h = sideFace.x();
			float j = sideFace.y();
			ItemModelGenerator.SideDirection sideDirection = sideFace.facing();
			float k = h + 0.1F;
			float l = h + 1.0F - 0.1F;
			float m;
			float n;
			if (sideDirection.isHorizontal()) {
				m = j + 0.1F;
				n = j + 1.0F - 0.1F;
			} else {
				m = j + 1.0F - 0.1F;
				n = j + 0.1F;
			}

			float o = h;
			float p = j;
			float q = h;
			float r = j;
			switch (sideDirection) {
				case UP:
					q++;
					break;
				case DOWN:
					q++;
					p++;
					r++;
					break;
				case LEFT:
					r++;
					break;
				case RIGHT:
					o++;
					q++;
					r++;
			}

			o *= f;
			q *= f;
			p *= g;
			r *= g;
			p = 16.0F - p;
			r = 16.0F - r;
			Map<Direction, BlockElementFace> map = Map.of(
				sideDirection.getDirection(), new BlockElementFace(null, i, string, new BlockElementFace.UVs(k * f, m * f, l * g, n * g), Quadrant.R0)
			);
			switch (sideDirection) {
				case UP:
					list.add(new BlockElement(new Vector3f(o, p, 7.5F), new Vector3f(q, p, 8.5F), map));
					break;
				case DOWN:
					list.add(new BlockElement(new Vector3f(o, r, 7.5F), new Vector3f(q, r, 8.5F), map));
					break;
				case LEFT:
					list.add(new BlockElement(new Vector3f(o, p, 7.5F), new Vector3f(o, r, 8.5F), map));
					break;
				case RIGHT:
					list.add(new BlockElement(new Vector3f(q, p, 7.5F), new Vector3f(q, r, 8.5F), map));
			}
		}

		return list;
	}

	private static Collection<ItemModelGenerator.SideFace> getSideFaces(SpriteContents spriteContents) {
		int i = spriteContents.width();
		int j = spriteContents.height();
		Set<ItemModelGenerator.SideFace> set = new HashSet<>();
		spriteContents.getUniqueFrames().forEach(k -> {
			for (int l = 0; l < j; l++) {
				for (int m = 0; m < i; m++) {
					boolean bl = !isTransparent(spriteContents, k, m, l, i, j);
					if (bl) {
						checkTransition(ItemModelGenerator.SideDirection.UP, set, spriteContents, k, m, l, i, j);
						checkTransition(ItemModelGenerator.SideDirection.DOWN, set, spriteContents, k, m, l, i, j);
						checkTransition(ItemModelGenerator.SideDirection.LEFT, set, spriteContents, k, m, l, i, j);
						checkTransition(ItemModelGenerator.SideDirection.RIGHT, set, spriteContents, k, m, l, i, j);
					}
				}
			}
		});
		return set;
	}

	private static void checkTransition(
		ItemModelGenerator.SideDirection sideDirection, Set<ItemModelGenerator.SideFace> set, SpriteContents spriteContents, int i, int j, int k, int l, int m
	) {
		if (isTransparent(spriteContents, i, j - sideDirection.direction.getStepX(), k - sideDirection.direction.getStepY(), l, m)) {
			set.add(new ItemModelGenerator.SideFace(sideDirection, j, k));
		}
	}

	private static boolean isTransparent(SpriteContents spriteContents, int i, int j, int k, int l, int m) {
		return j >= 0 && k >= 0 && j < l && k < m ? spriteContents.isTransparent(i, j, k) : true;
	}

	@Environment(EnvType.CLIENT)
	enum SideDirection {
		UP(Direction.UP),
		DOWN(Direction.DOWN),
		LEFT(Direction.EAST),
		RIGHT(Direction.WEST);

		final Direction direction;

		SideDirection(final Direction direction) {
			this.direction = direction;
		}

		public Direction getDirection() {
			return this.direction;
		}

		boolean isHorizontal() {
			return this == DOWN || this == UP;
		}
	}

	@Environment(EnvType.CLIENT)
	record SideFace(ItemModelGenerator.SideDirection facing, int x, int y) {
	}
}
