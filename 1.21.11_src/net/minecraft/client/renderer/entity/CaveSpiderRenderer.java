package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.spider.CaveSpider;

@Environment(EnvType.CLIENT)
public class CaveSpiderRenderer extends SpiderRenderer<CaveSpider> {
	private static final Identifier CAVE_SPIDER_LOCATION = Identifier.withDefaultNamespace("textures/entity/spider/cave_spider.png");

	public CaveSpiderRenderer(EntityRendererProvider.Context context) {
		super(context, ModelLayers.CAVE_SPIDER);
		this.shadowRadius = 0.56F;
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState livingEntityRenderState) {
		return CAVE_SPIDER_LOCATION;
	}
}
