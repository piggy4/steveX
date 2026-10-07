package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public class BreakingItemParticle extends SingleQuadParticle {
	private final float uo;
	private final float vo;
	private final SingleQuadParticle.Layer layer;

	BreakingItemParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, TextureAtlasSprite textureAtlasSprite) {
		this(clientLevel, d, e, f, textureAtlasSprite);
		this.xd *= 0.1F;
		this.yd *= 0.1F;
		this.zd *= 0.1F;
		this.xd += g;
		this.yd += h;
		this.zd += i;
	}

	protected BreakingItemParticle(ClientLevel clientLevel, double d, double e, double f, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f, 0.0, 0.0, 0.0, textureAtlasSprite);
		this.gravity = 1.0F;
		this.quadSize /= 2.0F;
		this.uo = this.random.nextFloat() * 3.0F;
		this.vo = this.random.nextFloat() * 3.0F;
		this.layer = textureAtlasSprite.atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS) ? SingleQuadParticle.Layer.TERRAIN : SingleQuadParticle.Layer.ITEMS;
	}

	@Override
	protected float getU0() {
		return this.sprite.getU((this.uo + 1.0F) / 4.0F);
	}

	@Override
	protected float getU1() {
		return this.sprite.getU(this.uo / 4.0F);
	}

	@Override
	protected float getV0() {
		return this.sprite.getV(this.vo / 4.0F);
	}

	@Override
	protected float getV1() {
		return this.sprite.getV((this.vo + 1.0F) / 4.0F);
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return this.layer;
	}

	@Environment(EnvType.CLIENT)
	public static class CobwebProvider extends BreakingItemParticle.ItemParticleProvider<SimpleParticleType> {
		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			return new BreakingItemParticle(clientLevel, d, e, f, this.getSprite(new ItemStack(Items.COBWEB), clientLevel, randomSource));
		}
	}

	@Environment(EnvType.CLIENT)
	public abstract static class ItemParticleProvider<T extends ParticleOptions> implements ParticleProvider<T> {
		private final ItemStackRenderState scratchRenderState = new ItemStackRenderState();

		protected TextureAtlasSprite getSprite(ItemStack itemStack, ClientLevel clientLevel, RandomSource randomSource) {
			Minecraft.getInstance().getItemModelResolver().updateForTopItem(this.scratchRenderState, itemStack, ItemDisplayContext.GROUND, clientLevel, null, 0);
			TextureAtlasSprite textureAtlasSprite = this.scratchRenderState.pickParticleIcon(randomSource);
			return textureAtlasSprite != null ? textureAtlasSprite : Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS).missingSprite();
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Provider extends BreakingItemParticle.ItemParticleProvider<ItemParticleOption> {
		public Particle createParticle(
			ItemParticleOption itemParticleOption, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			return new BreakingItemParticle(clientLevel, d, e, f, g, h, i, this.getSprite(itemParticleOption.getItem(), clientLevel, randomSource));
		}
	}

	@Environment(EnvType.CLIENT)
	public static class SlimeProvider extends BreakingItemParticle.ItemParticleProvider<SimpleParticleType> {
		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			return new BreakingItemParticle(clientLevel, d, e, f, this.getSprite(new ItemStack(Items.SLIME_BALL), clientLevel, randomSource));
		}
	}

	@Environment(EnvType.CLIENT)
	public static class SnowballProvider extends BreakingItemParticle.ItemParticleProvider<SimpleParticleType> {
		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			return new BreakingItemParticle(clientLevel, d, e, f, this.getSprite(new ItemStack(Items.SNOWBALL), clientLevel, randomSource));
		}
	}
}
