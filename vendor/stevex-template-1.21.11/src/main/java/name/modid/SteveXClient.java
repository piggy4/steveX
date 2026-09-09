package name.modid;

import name.modid.vision.ContainerMemoryTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class SteveXClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AgentWebSocketServer.launch();
        // 真实世界客户端首次启动：语言/音量/字幕/画质默认设置（标记文件守卫，仅一次）
        ClientTickEvents.END_CLIENT_TICK.register(ClientDefaults::onClientTick);
        // v2.30（§5.2.3）：容器内容记忆采集侧会话化状态机（屏幕转移绑定 / 菜单缓存 / 兜底提交）
        ClientTickEvents.END_CLIENT_TICK.register(ContainerMemoryTracker::onClientTick);
    }
}
