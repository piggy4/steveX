package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public class BlockMarker extends SingleQuadParticle {
	private final SingleQuadParticle.Layer layer;

	BlockMarker(ClientLevel clientLevel, double d, double e, double f, BlockState blockState) {
		super(clientLevel, d, e, f, Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(blockState));
		this.gravity = 0.0F;
		this.lifetime = 80;
		this.hasPhysics = false;
		this.layer = this.sprite.atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS) ? SingleQuadParticle.Layer.TERRAIN : SingleQuadParticle.Layer.ITEMS;
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return this.layer;
	}

	@Override
	public float getQuadSize(float f) {
		return 0.5F;
	}

	@Environment(EnvType.CLIENT)
	public static class Provider implements ParticleProvider<BlockParticleOption> {
		public Particle createParticle(
			BlockParticleOption blockParticleOption, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			return new BlockMarker(clientLevel, d, e, f, blockParticleOption.getState());
		}
	}
}
