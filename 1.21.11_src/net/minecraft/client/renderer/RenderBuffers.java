package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.SequencedMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public class RenderBuffers {
	private final SectionBufferBuilderPack fixedBufferPack = new SectionBufferBuilderPack();
	private final SectionBufferBuilderPool sectionBufferPool;
	private final MultiBufferSource.BufferSource bufferSource;
	private final MultiBufferSource.BufferSource crumblingBufferSource;
	private final OutlineBufferSource outlineBufferSource;

	public RenderBuffers(int i) {
		this.sectionBufferPool = SectionBufferBuilderPool.allocate(i);
		SequencedMap<RenderType, ByteBufferBuilder> sequencedMap = Util.make(new Object2ObjectLinkedOpenHashMap<>(), object2ObjectLinkedOpenHashMap -> {
			object2ObjectLinkedOpenHashMap.put(Sheets.solidBlockSheet(), this.fixedBufferPack.buffer(ChunkSectionLayer.SOLID));
			object2ObjectLinkedOpenHashMap.put(Sheets.cutoutBlockSheet(), this.fixedBufferPack.buffer(ChunkSectionLayer.CUTOUT));
			object2ObjectLinkedOpenHashMap.put(Sheets.translucentItemSheet(), this.fixedBufferPack.buffer(ChunkSectionLayer.TRANSLUCENT));
			put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)object2ObjectLinkedOpenHashMap, Sheets.translucentBlockItemSheet());
			put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)object2ObjectLinkedOpenHashMap, Sheets.shieldSheet());
			put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)object2ObjectLinkedOpenHashMap, Sheets.bedSheet());
			put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)object2ObjectLinkedOpenHashMap, Sheets.shulkerBoxSheet());
			put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)object2ObjectLinkedOpenHashMap, Sheets.signSheet());
			put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)object2ObjectLinkedOpenHashMap, Sheets.hangingSignSheet());
			object2ObjectLinkedOpenHashMap.put(Sheets.chestSheet(), new ByteBufferBuilder(786432));
			put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)object2ObjectLinkedOpenHashMap, RenderTypes.armorEntityGlint());
			put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)object2ObjectLinkedOpenHashMap, RenderTypes.glint());
			put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)object2ObjectLinkedOpenHashMap, RenderTypes.glintTranslucent());
			put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)object2ObjectLinkedOpenHashMap, RenderTypes.entityGlint());
			put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)object2ObjectLinkedOpenHashMap, RenderTypes.waterMask());
		});
		this.bufferSource = MultiBufferSource.immediateWithBuffers(sequencedMap, new ByteBufferBuilder(786432));
		this.outlineBufferSource = new OutlineBufferSource();
		SequencedMap<RenderType, ByteBufferBuilder> sequencedMap2 = Util.make(
			new Object2ObjectLinkedOpenHashMap<>(),
			object2ObjectLinkedOpenHashMap -> ModelBakery.DESTROY_TYPES
				.forEach(renderType -> put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)object2ObjectLinkedOpenHashMap, renderType))
		);
		this.crumblingBufferSource = MultiBufferSource.immediateWithBuffers(sequencedMap2, new ByteBufferBuilder(0));
	}

	private static void put(Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder> object2ObjectLinkedOpenHashMap, RenderType renderType) {
		object2ObjectLinkedOpenHashMap.put(renderType, new ByteBufferBuilder(renderType.bufferSize()));
	}

	public SectionBufferBuilderPack fixedBufferPack() {
		return this.fixedBufferPack;
	}

	public SectionBufferBuilderPool sectionBufferPool() {
		return this.sectionBufferPool;
	}

	public MultiBufferSource.BufferSource bufferSource() {
		return this.bufferSource;
	}

	public MultiBufferSource.BufferSource crumblingBufferSource() {
		return this.crumblingBufferSource;
	}

	public OutlineBufferSource outlineBufferSource() {
		return this.outlineBufferSource;
	}
}
