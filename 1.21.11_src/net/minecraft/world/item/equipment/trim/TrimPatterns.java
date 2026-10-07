package net.minecraft.world.item.equipment.trim;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;

public class TrimPatterns {
	public static final ResourceKey<TrimPattern> SENTRY = registryKey("sentry");
	public static final ResourceKey<TrimPattern> DUNE = registryKey("dune");
	public static final ResourceKey<TrimPattern> COAST = registryKey("coast");
	public static final ResourceKey<TrimPattern> WILD = registryKey("wild");
	public static final ResourceKey<TrimPattern> WARD = registryKey("ward");
	public static final ResourceKey<TrimPattern> EYE = registryKey("eye");
	public static final ResourceKey<TrimPattern> VEX = registryKey("vex");
	public static final ResourceKey<TrimPattern> TIDE = registryKey("tide");
	public static final ResourceKey<TrimPattern> SNOUT = registryKey("snout");
	public static final ResourceKey<TrimPattern> RIB = registryKey("rib");
	public static final ResourceKey<TrimPattern> SPIRE = registryKey("spire");
	public static final ResourceKey<TrimPattern> WAYFINDER = registryKey("wayfinder");
	public static final ResourceKey<TrimPattern> SHAPER = registryKey("shaper");
	public static final ResourceKey<TrimPattern> SILENCE = registryKey("silence");
	public static final ResourceKey<TrimPattern> RAISER = registryKey("raiser");
	public static final ResourceKey<TrimPattern> HOST = registryKey("host");
	public static final ResourceKey<TrimPattern> FLOW = registryKey("flow");
	public static final ResourceKey<TrimPattern> BOLT = registryKey("bolt");

	public static void bootstrap(BootstrapContext<TrimPattern> bootstrapContext) {
		register(bootstrapContext, SENTRY);
		register(bootstrapContext, DUNE);
		register(bootstrapContext, COAST);
		register(bootstrapContext, WILD);
		register(bootstrapContext, WARD);
		register(bootstrapContext, EYE);
		register(bootstrapContext, VEX);
		register(bootstrapContext, TIDE);
		register(bootstrapContext, SNOUT);
		register(bootstrapContext, RIB);
		register(bootstrapContext, SPIRE);
		register(bootstrapContext, WAYFINDER);
		register(bootstrapContext, SHAPER);
		register(bootstrapContext, SILENCE);
		register(bootstrapContext, RAISER);
		register(bootstrapContext, HOST);
		register(bootstrapContext, FLOW);
		register(bootstrapContext, BOLT);
	}

	public static void register(BootstrapContext<TrimPattern> bootstrapContext, ResourceKey<TrimPattern> resourceKey) {
		TrimPattern trimPattern = new TrimPattern(
			defaultAssetId(resourceKey), Component.translatable(Util.makeDescriptionId("trim_pattern", resourceKey.identifier())), false
		);
		bootstrapContext.register(resourceKey, trimPattern);
	}

	private static ResourceKey<TrimPattern> registryKey(String string) {
		return ResourceKey.create(Registries.TRIM_PATTERN, Identifier.withDefaultNamespace(string));
	}

	public static Identifier defaultAssetId(ResourceKey<TrimPattern> resourceKey) {
		return resourceKey.identifier();
	}
}
