package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public class ChestSpecialRenderer implements NoDataSpecialModelRenderer {
	public static final Identifier GIFT_CHEST_TEXTURE = Identifier.withDefaultNamespace("christmas");
	public static final Identifier NORMAL_CHEST_TEXTURE = Identifier.withDefaultNamespace("normal");
	public static final Identifier TRAPPED_CHEST_TEXTURE = Identifier.withDefaultNamespace("trapped");
	public static final Identifier ENDER_CHEST_TEXTURE = Identifier.withDefaultNamespace("ender");
	public static final Identifier COPPER_CHEST_TEXTURE = Identifier.withDefaultNamespace("copper");
	public static final Identifier EXPOSED_COPPER_CHEST_TEXTURE = Identifier.withDefaultNamespace("copper_exposed");
	public static final Identifier WEATHERED_COPPER_CHEST_TEXTURE = Identifier.withDefaultNamespace("copper_weathered");
	public static final Identifier OXIDIZED_COPPER_CHEST_TEXTURE = Identifier.withDefaultNamespace("copper_oxidized");
	private final MaterialSet materials;
	private final ChestModel model;
	private final Material material;
	private final float openness;

	public ChestSpecialRenderer(MaterialSet materialSet, ChestModel chestModel, Material material, float f) {
		this.materials = materialSet;
		this.model = chestModel;
		this.material = material;
		this.openness = f;
	}

	@Override
	public void submit(ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k) {
		submitNodeCollector.submitModel(
			this.model, this.openness, poseStack, this.material.renderType(RenderTypes::entitySolid), i, j, -1, this.materials.get(this.material), k, null
		);
	}

	@Override
	public void getExtents(Consumer<Vector3fc> consumer) {
		PoseStack poseStack = new PoseStack();
		this.model.setupAnim(this.openness);
		this.model.root().getExtentsForGui(poseStack, consumer);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(Identifier texture, float openness) implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<ChestSpecialRenderer.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Identifier.CODEC.fieldOf("texture").forGetter(ChestSpecialRenderer.Unbaked::texture),
					Codec.FLOAT.optionalFieldOf("openness", 0.0F).forGetter(ChestSpecialRenderer.Unbaked::openness)
				)
				.apply(instance, ChestSpecialRenderer.Unbaked::new)
		);

		public Unbaked(Identifier identifier) {
			this(identifier, 0.0F);
		}

		@Override
		public MapCodec<ChestSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext bakingContext) {
			ChestModel chestModel = new ChestModel(bakingContext.entityModelSet().bakeLayer(ModelLayers.CHEST));
			Material material = Sheets.CHEST_MAPPER.apply(this.texture);
			return new ChestSpecialRenderer(bakingContext.materials(), chestModel, material, this.openness);
		}
	}
}
