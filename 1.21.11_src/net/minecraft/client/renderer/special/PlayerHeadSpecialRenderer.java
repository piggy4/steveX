package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PlayerHeadSpecialRenderer implements SpecialModelRenderer<PlayerSkinRenderCache.RenderInfo> {
	private final PlayerSkinRenderCache playerSkinRenderCache;
	private final SkullModelBase modelBase;

	PlayerHeadSpecialRenderer(PlayerSkinRenderCache playerSkinRenderCache, SkullModelBase skullModelBase) {
		this.playerSkinRenderCache = playerSkinRenderCache;
		this.modelBase = skullModelBase;
	}

	public void submit(
		PlayerSkinRenderCache.@Nullable RenderInfo renderInfo,
		ItemDisplayContext itemDisplayContext,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int i,
		int j,
		boolean bl,
		int k
	) {
		RenderType renderType = renderInfo != null ? renderInfo.renderType() : PlayerSkinRenderCache.DEFAULT_PLAYER_SKIN_RENDER_TYPE;
		SkullBlockRenderer.submitSkull(null, 180.0F, 0.0F, poseStack, submitNodeCollector, i, this.modelBase, renderType, k, null);
	}

	@Override
	public void getExtents(Consumer<Vector3fc> consumer) {
		PoseStack poseStack = new PoseStack();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		this.modelBase.root().getExtentsForGui(poseStack, consumer);
	}

	public PlayerSkinRenderCache.@Nullable RenderInfo extractArgument(ItemStack itemStack) {
		ResolvableProfile resolvableProfile = itemStack.get(DataComponents.PROFILE);
		return resolvableProfile == null ? null : this.playerSkinRenderCache.getOrDefault(resolvableProfile);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked() implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<PlayerHeadSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(PlayerHeadSpecialRenderer.Unbaked::new);

		@Override
		public MapCodec<PlayerHeadSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public @Nullable SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext bakingContext) {
			SkullModelBase skullModelBase = SkullBlockRenderer.createModel(bakingContext.entityModelSet(), SkullBlock.Types.PLAYER);
			return skullModelBase == null ? null : new PlayerHeadSpecialRenderer(bakingContext.playerSkinRenderCache(), skullModelBase);
		}
	}
}
