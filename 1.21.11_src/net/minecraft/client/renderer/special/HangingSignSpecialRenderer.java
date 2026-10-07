package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.HangingSignRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public class HangingSignSpecialRenderer implements NoDataSpecialModelRenderer {
	private final MaterialSet materials;
	private final Model.Simple model;
	private final Material material;

	public HangingSignSpecialRenderer(MaterialSet materialSet, Model.Simple simple, Material material) {
		this.materials = materialSet;
		this.model = simple;
		this.material = material;
	}

	@Override
	public void submit(ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k) {
		HangingSignRenderer.submitSpecial(this.materials, poseStack, submitNodeCollector, i, j, this.model, this.material);
	}

	@Override
	public void getExtents(Consumer<Vector3fc> consumer) {
		PoseStack poseStack = new PoseStack();
		HangingSignRenderer.translateBase(poseStack, 0.0F);
		poseStack.scale(1.0F, -1.0F, -1.0F);
		this.model.root().getExtentsForGui(poseStack, consumer);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(WoodType woodType, Optional<Identifier> texture) implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<HangingSignSpecialRenderer.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					WoodType.CODEC.fieldOf("wood_type").forGetter(HangingSignSpecialRenderer.Unbaked::woodType),
					Identifier.CODEC.optionalFieldOf("texture").forGetter(HangingSignSpecialRenderer.Unbaked::texture)
				)
				.apply(instance, HangingSignSpecialRenderer.Unbaked::new)
		);

		public Unbaked(WoodType woodType) {
			this(woodType, Optional.empty());
		}

		@Override
		public MapCodec<HangingSignSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext bakingContext) {
			Model.Simple simple = HangingSignRenderer.createSignModel(bakingContext.entityModelSet(), this.woodType, HangingSignRenderer.AttachmentType.CEILING_MIDDLE);
			Material material = this.texture.map(Sheets.HANGING_SIGN_MAPPER::apply).orElseGet(() -> Sheets.getHangingSignMaterial(this.woodType));
			return new HangingSignSpecialRenderer(bakingContext.materials(), simple, material);
		}
	}
}
