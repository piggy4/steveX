package name.modid.vision;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

/**
 * v2.47（累积观测文件，见 docs/累积观测文件设计方案.md §4.5 决策 D）：两个 union store 共用的
 * <b>限频 + 后台写</b>机制。
 *
 * <p><b>为什么需要它</b>：union 是累积集合，规模会到 10 万格量级，NBT 编码 + gzip 在渲染线程要几百 ms
 * （§7.2）——每次采集同步写盘会把这段成本直接打进渲染帧。决策 D（用户 2026-10-07）选「浅拷贝 + 后台写」
 * 叠加「最短 5 秒限频」：限频压掉高频采集的写盘次数，浅拷贝把渲染线程的成本降到一次 map 拷贝。
 *
 * <p><b>职责边界：本类不持有 union 数据。</b> 浅拷贝<b>必须由渲染线程做</b>——union 的 map 只在渲染线程
 * 变更，后台线程直接遍历它不只是 ConcurrentModificationException 的问题，更是内存可见性问题。故各 store
 * 在判定 dirty 时自行 publish 一份"此后不再变更"的快照，本类只回答「何时」把快照落盘。
 *
 * <p><b>为什么两个文件共用一个调度器</b>：这两个文件是给 agent 一起读的（同一份世界模型的两半），分开
 * 调度会让 agent 读到 terrain@T 与 entities@T+3s 的错位组合。共用之后两者在同一次 fire 内落盘。
 *
 * <p><b>关停兜底</b>：写线程是 daemon（不阻止 JVM 退出），但 daemon 同时意味着"已 publish、未落盘"的
 * 最后一笔会随进程消失——而 union 丢掉一条<b>删除</b>记录会留下永久幽灵：该格此后不再进记忆端镜像
 * （镜像里已无它）→ 永不重判 → 那条陈旧记录再没有第二次机会被删掉。故注册 shutdown hook，退出前再
 * fire 一次。
 *
 * <p>本类只服务采集端：记忆端 {@code com.example.memworld} 是另一个 mod，它的 {@code MemoryCellReporter}
 * 在服务器 tick 上同步写盘（那里的成本结构不同），两者不共享代码。
 */
final class UnionSaveScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 决策 D：最短写盘间隔（ms）。同一窗口内到达的多次变更合并成一次写。 */
    static final long MIN_INTERVAL_MS = 5000L;

    /**
     * 单线程 daemon：写盘任务串行执行（两个 store 也串行，故同一时刻只有一个文件在编码）。
     * 单线程而非线程池——写盘是 IO 密集且频率上限由 {@link #MIN_INTERVAL_MS} 压住，多线程只会
     * 让两个文件的重命名顺序变得不可预测。
     */
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread t = new Thread(r, "stevex-union-save");
        t.setDaemon(true);
        return t;
    });

    /** 各 store 的落盘回调（构造时注册）。CopyOnWrite：注册发生在构造期，fire 在运行期读。 */
    private final List<Runnable> writers = new CopyOnWriteArrayList<>();

    /** 上次真正 fire 的墙钟（ms）；{@link #requestSave} 据此算延迟。 */
    private long lastRunAt;
    /** 已排定一次 fire（未到点或正在跑）→ 后续请求只更新快照、不再排定（天然合并）。 */
    private boolean scheduled;

    UnionSaveScheduler() {
        // 退出前把"已 publish、未落盘"的快照补写一次（见类 javadoc 的"关停兜底"）。
        Runtime.getRuntime().addShutdownHook(new Thread(this::fire, "stevex-union-save-flush"));
    }

    /** 注册一个落盘回调。回调内部须自行取快照并容忍"无快照可写"（= 已被更早的一次 fire 写掉）。 */
    void registerWriter(final Runnable writer) {
        writers.add(writer);
    }

    /**
     * 请求一次落盘（渲染线程调用，须先 publish 快照）。已排定则只更新快照、不重复排定。
     *
     * <p>延迟 = 距上次 fire 满 {@link #MIN_INTERVAL_MS} 的剩余时间：久未写盘（常态是首次采集）立即写，
     * 紧接前一次写盘则延到窗口末尾——注意这是<b>后沿触发</b>，不是"丢弃本次变更"：排定的 fire 在运行时
     * 才取快照，届时拿到的是最新的那一份。
     */
    synchronized void requestSave() {
        if (scheduled) return;
        scheduled = true;
        final long delay = Math.max(0L, MIN_INTERVAL_MS - (System.currentTimeMillis() - lastRunAt));
        executor.schedule(this::fire, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * 取各 store 的最新快照落盘。由调度线程或 shutdown hook 执行（两者可能并发——各 store 的
     * {@code flush} 自带互斥，见其实现）。
     */
    private void fire() {
        synchronized (this) {
            scheduled = false;
            lastRunAt = System.currentTimeMillis();
        }
        for (Runnable writer : writers) {
            try {
                writer.run();
            } catch (Throwable t) {
                // 单个 store 写失败不得连累另一个：terrain_union 与 entities_union 是两份独立文件，
                // 一个写不进去（磁盘满 / 权限）不构成放弃另一个的理由。
                LOGGER.warn("[Vision] Union save failed: {}", t.toString());
            }
        }
    }

    /**
     * 两个 union 文件共用的原子写原语：先写同目录 {@code .tmp}，再改名覆盖。
     *
     * <p>照抄记忆端 {@code MemoryCellReporter.writeAtomic}（{@code MemoryCellReporter.java:433-533}）的
     * 做法——<b>照抄而非调用</b>：那是另一个 mod 的类，不在本 mod 的 classpath 上。两者共同的理由是
     * "读方按 mtime 轮询"：非原子写会让读方有机会读到半个文件（gzip 头在、尾不在），而原子改名让
     * "文件存在"始终蕴含"文件完整"。
     *
     * <p>{@code ATOMIC_MOVE} 尽力而为，失败回退 {@code REPLACE_EXISTING}（同目录 rename 通常原子）。
     */
    static void writeAtomic(final CompoundTag root, final Path target) throws IOException {
        final Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        NbtIo.writeCompressed(root, tmp);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailure) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
