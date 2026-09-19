package name.modid;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import name.modid.vision.EffectSampler;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SteveX implements ModInitializer {
	public static final String MOD_ID = "stevex";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// v2.42（药水效果数据源，见 docs/实体属性观测面设计方案.md §12）：效果不在客户端同步链路上，
		// 但单机/自己是局域网主机时服务端权威数据就在同一个 JVM 里 —— 每 tick 采样进不可变信箱，
		// 采集帧（渲染线程）只读。两个事件均为 fabric-api 既有事件，无需新增 mixin。
		ServerTickEvents.END_SERVER_TICK.register(EffectSampler::sampleAll);
		// 必须清空：否则"玩完单机 → 退出 → 连服务器"会读到上一个世界的陈旧数据，
		// 把"未知"伪装成"已知"（§12.3）。
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> EffectSampler.reset());

		LOGGER.info("Hello Fabric world!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
