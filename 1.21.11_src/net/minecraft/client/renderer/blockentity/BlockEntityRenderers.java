package net.minecraft.client.renderer.blockentity;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

@Environment(EnvType.CLIENT)
public class BlockEntityRenderers {
	private static final Map<BlockEntityType<?>, BlockEntityRendererProvider<?, ?>> PROVIDERS = Maps.newHashMap();

	public static <T extends BlockEntity, S extends BlockEntityRenderState> void register(
		BlockEntityType<? extends T> blockEntityType, BlockEntityRendererProvider<T, S> blockEntityRendererProvider
	) {
		PROVIDERS.put(blockEntityType, blockEntityRendererProvider);
	}

	public static Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>> createEntityRenderers(BlockEntityRendererProvider.Context context) {
		Builder<BlockEntityType<?>, BlockEntityRenderer<?, ?>> builder = ImmutableMap.builder();
		PROVIDERS.forEach((blockEntityType, blockEntityRendererProvider) -> {
			try {
				builder.put((BlockEntityType<?>)blockEntityType, blockEntityRendererProvider.create(context));
			} catch (Exception exception) {
				throw new IllegalStateException("Failed to create model for " + BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey((BlockEntityType<?>)blockEntityType), exception);
			}
		});
		return builder.build();
	}

	static {
		register(BlockEntityType.SIGN, SignRenderer::new);
		register(BlockEntityType.HANGING_SIGN, HangingSignRenderer::new);
		register(BlockEntityType.MOB_SPAWNER, SpawnerRenderer::new);
		register(BlockEntityType.PISTON, context -> new PistonHeadRenderer());
		register(BlockEntityType.CHEST, ChestRenderer::new);
		register(BlockEntityType.ENDER_CHEST, ChestRenderer::new);
		register(BlockEntityType.TRAPPED_CHEST, ChestRenderer::new);
		register(BlockEntityType.ENCHANTING_TABLE, EnchantTableRenderer::new);
		register(BlockEntityType.LECTERN, LecternRenderer::new);
		register(BlockEntityType.END_PORTAL, context -> new TheEndPortalRenderer());
		register(BlockEntityType.END_GATEWAY, context -> new TheEndGatewayRenderer());
		register(BlockEntityType.BEACON, context -> new BeaconRenderer());
		register(BlockEntityType.SKULL, SkullBlockRenderer::new);
		register(BlockEntityType.BANNER, BannerRenderer::new);
		register(BlockEntityType.STRUCTURE_BLOCK, context -> new BlockEntityWithBoundingBoxRenderer());
		register(BlockEntityType.TEST_INSTANCE_BLOCK, context -> new TestInstanceRenderer());
		register(BlockEntityType.SHULKER_BOX, ShulkerBoxRenderer::new);
		register(BlockEntityType.BED, BedRenderer::new);
		register(BlockEntityType.CONDUIT, ConduitRenderer::new);
		register(BlockEntityType.BELL, BellRenderer::new);
		register(BlockEntityType.CAMPFIRE, CampfireRenderer::new);
		register(BlockEntityType.BRUSHABLE_BLOCK, BrushableBlockRenderer::new);
		register(BlockEntityType.DECORATED_POT, DecoratedPotRenderer::new);
		register(BlockEntityType.TRIAL_SPAWNER, TrialSpawnerRenderer::new);
		register(BlockEntityType.VAULT, VaultRenderer::new);
		register(BlockEntityType.COPPER_GOLEM_STATUE, CopperGolemStatueBlockRenderer::new);
		register(BlockEntityType.SHELF, ShelfRenderer::new);
	}
}
