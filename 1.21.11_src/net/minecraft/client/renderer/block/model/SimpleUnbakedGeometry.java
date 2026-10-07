package net.minecraft.client.renderer.block.model;

import java.util.List;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.client.resources.model.UnbakedGeometry;
import net.minecraft.core.Direction;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public record SimpleUnbakedGeometry(List<BlockElement> elements) implements UnbakedGeometry {
	@Override
	public QuadCollection bake(TextureSlots textureSlots, ModelBaker modelBaker, ModelState modelState, ModelDebugName modelDebugName) {
		return bake(this.elements, textureSlots, modelBaker, modelState, modelDebugName);
	}

	public static QuadCollection bake(
		List<BlockElement> list, TextureSlots textureSlots, ModelBaker modelBaker, ModelState modelState, ModelDebugName modelDebugName
	) {
		QuadCollection.Builder builder = new QuadCollection.Builder();

		for (BlockElement blockElement : list) {
			boolean bl = true;
			boolean bl2 = true;
			boolean bl3 = true;
			Vector3fc vector3fc = blockElement.from();
			Vector3fc vector3fc2 = blockElement.to();
			if (vector3fc.x() == vector3fc2.x()) {
				bl2 = false;
				bl3 = false;
			}

			if (vector3fc.y() == vector3fc2.y()) {
				bl = false;
				bl3 = false;
			}

			if (vector3fc.z() == vector3fc2.z()) {
				bl = false;
				bl2 = false;
			}

			if (bl || bl2 || bl3) {
				for (Entry<Direction, BlockElementFace> entry : blockElement.faces().entrySet()) {
					Direction direction = entry.getKey();
					BlockElementFace blockElementFace = entry.getValue();

					boolean bl4 = switch (direction.getAxis()) {
						case X -> bl;
						case Y -> bl2;
						case Z -> bl3;
					};
					if (bl4) {
						TextureAtlasSprite textureAtlasSprite = modelBaker.sprites().resolveSlot(textureSlots, blockElementFace.texture(), modelDebugName);
						BakedQuad bakedQuad = FaceBakery.bakeQuad(
							modelBaker.parts(),
							vector3fc,
							vector3fc2,
							blockElementFace,
							textureAtlasSprite,
							direction,
							modelState,
							blockElement.rotation(),
							blockElement.shade(),
							blockElement.lightEmission()
						);
						if (blockElementFace.cullForDirection() == null) {
							builder.addUnculledFace(bakedQuad);
						} else {
							builder.addCulledFace(Direction.rotate(modelState.transformation().getMatrix(), blockElementFace.cullForDirection()), bakedQuad);
						}
					}
				}
			}
		}

		return builder.build();
	}
}
