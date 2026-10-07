package net.minecraft.client.renderer.item.properties.conditional;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface ConditionalItemModelProperty extends ItemModelPropertyTest {
	MapCodec<? extends ConditionalItemModelProperty> type();
}
