package com.example;

import com.example.memworld.MemoryCellReporter;
import com.example.memworld.MemoryWorldManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class StevexTestClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// 启动后自动创建 / 进入记忆世界
		ClientTickEvents.END_CLIENT_TICK.register(MemoryWorldManager::onClientTick);
		// v2.37（§7.13）：客户端 tick 烘焙排队中的方块几何（读客户端烘焙模型 + sprite alpha 掩码）。
		// 模型系统只在客户端线程可用，而 MemoryCellReporter.tick 跑在服务器 tick 上——故几何按
		// "服务器侧登记请求 → 客户端侧批量烘焙"两段完成，未就绪的格本轮不进几何段（欠删，安全方向）。
		ClientTickEvents.END_CLIENT_TICK.register(mc -> MemoryCellReporter.tickClient());
	}
}
