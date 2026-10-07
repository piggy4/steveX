package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

@Environment(EnvType.CLIENT)
public abstract class RisingParticle extends SingleQuadParticle {
	protected RisingParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f, g, h, i, textureAtlasSprite);
		this.friction = 0.96F;
		this.xd = this.xd * 0.01F + g;
		this.yd = this.yd * 0.01F + h;
		this.zd = this.zd * 0.01F + i;
		this.x = this.x + (this.random.nextFloat() - this.random.nextFloat()) * 0.05F;
		this.y = this.y + (this.random.nextFloat() - this.random.nextFloat()) * 0.05F;
		this.z = this.z + (this.random.nextFloat() - this.random.nextFloat()) * 0.05F;
		this.lifetime = (int)(8.0 / (this.random.nextFloat() * 0.8 + 0.2)) + 4;
	}
}
