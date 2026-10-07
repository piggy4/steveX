package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;

public class FallenTreeConfiguration implements FeatureConfiguration {
	public static final Codec<FallenTreeConfiguration> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				BlockStateProvider.CODEC.fieldOf("trunk_provider").forGetter(fallenTreeConfiguration -> fallenTreeConfiguration.trunkProvider),
				IntProvider.codec(0, 16).fieldOf("log_length").forGetter(fallenTreeConfiguration -> fallenTreeConfiguration.logLength),
				TreeDecorator.CODEC.listOf().fieldOf("stump_decorators").forGetter(fallenTreeConfiguration -> fallenTreeConfiguration.stumpDecorators),
				TreeDecorator.CODEC.listOf().fieldOf("log_decorators").forGetter(fallenTreeConfiguration -> fallenTreeConfiguration.logDecorators)
			)
			.apply(instance, FallenTreeConfiguration::new)
	);
	public final BlockStateProvider trunkProvider;
	public final IntProvider logLength;
	public final List<TreeDecorator> stumpDecorators;
	public final List<TreeDecorator> logDecorators;

	protected FallenTreeConfiguration(BlockStateProvider blockStateProvider, IntProvider intProvider, List<TreeDecorator> list, List<TreeDecorator> list2) {
		this.trunkProvider = blockStateProvider;
		this.logLength = intProvider;
		this.stumpDecorators = list;
		this.logDecorators = list2;
	}

	public static class FallenTreeConfigurationBuilder {
		private final BlockStateProvider trunkProvider;
		private final IntProvider logLength;
		private List<TreeDecorator> stumpDecorators = new ArrayList<>();
		private List<TreeDecorator> logDecorators = new ArrayList<>();

		public FallenTreeConfigurationBuilder(BlockStateProvider blockStateProvider, IntProvider intProvider) {
			this.trunkProvider = blockStateProvider;
			this.logLength = intProvider;
		}

		public FallenTreeConfiguration.FallenTreeConfigurationBuilder stumpDecorators(List<TreeDecorator> list) {
			this.stumpDecorators = list;
			return this;
		}

		public FallenTreeConfiguration.FallenTreeConfigurationBuilder logDecorators(List<TreeDecorator> list) {
			this.logDecorators = list;
			return this;
		}

		public FallenTreeConfiguration build() {
			return new FallenTreeConfiguration(this.trunkProvider, this.logLength, this.stumpDecorators, this.logDecorators);
		}
	}
}
