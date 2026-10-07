package net.minecraft.client.animation;

import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;

@Environment(EnvType.CLIENT)
public record AnimationDefinition(float lengthInSeconds, boolean looping, Map<String, List<AnimationChannel>> boneAnimations) {
	public KeyframeAnimation bake(ModelPart modelPart) {
		return KeyframeAnimation.bake(modelPart, this);
	}

	@Environment(EnvType.CLIENT)
	public static class Builder {
		private final float length;
		private final Map<String, List<AnimationChannel>> animationByBone = Maps.newHashMap();
		private boolean looping;

		public static AnimationDefinition.Builder withLength(float f) {
			return new AnimationDefinition.Builder(f);
		}

		private Builder(float f) {
			this.length = f;
		}

		public AnimationDefinition.Builder looping() {
			this.looping = true;
			return this;
		}

		public AnimationDefinition.Builder addAnimation(String string, AnimationChannel animationChannel) {
			this.animationByBone.computeIfAbsent(string, stringx -> new ArrayList<>()).add(animationChannel);
			return this;
		}

		public AnimationDefinition build() {
			return new AnimationDefinition(this.length, this.looping, this.animationByBone);
		}
	}
}
