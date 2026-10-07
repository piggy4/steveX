package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class WolfRenderState extends LivingEntityRenderState {
	private static final Identifier DEFAULT_TEXTURE = Identifier.withDefaultNamespace("textures/entity/wolf/wolf.png");
	public boolean isAngry;
	public boolean isSitting;
	public float tailAngle = (float) (Math.PI / 5);
	public float headRollAngle;
	public float shakeAnim;
	public float wetShade = 1.0F;
	public Identifier texture = DEFAULT_TEXTURE;
	public @Nullable DyeColor collarColor;
	public ItemStack bodyArmorItem = ItemStack.EMPTY;

	public float getBodyRollAngle(float f) {
		float g = (this.shakeAnim + f) / 1.8F;
		if (g < 0.0F) {
			g = 0.0F;
		} else if (g > 1.0F) {
			g = 1.0F;
		}

		return Mth.sin(g * (float) Math.PI) * Mth.sin(g * (float) Math.PI * 11.0F) * 0.15F * (float) Math.PI;
	}
}
