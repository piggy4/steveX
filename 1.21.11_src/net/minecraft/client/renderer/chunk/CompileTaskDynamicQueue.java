package net.minecraft.client.renderer.chunk;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.ListIterator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CompileTaskDynamicQueue {
	private static final int MAX_RECOMPILE_QUOTA = 2;
	private int recompileQuota = 2;
	private final List<SectionRenderDispatcher.RenderSection.CompileTask> tasks = new ObjectArrayList<>();

	public synchronized void add(SectionRenderDispatcher.RenderSection.CompileTask compileTask) {
		this.tasks.add(compileTask);
	}

	public synchronized SectionRenderDispatcher.RenderSection.@Nullable CompileTask poll(Vec3 vec3) {
		int i = -1;
		int j = -1;
		double d = Double.MAX_VALUE;
		double e = Double.MAX_VALUE;
		ListIterator<SectionRenderDispatcher.RenderSection.CompileTask> listIterator = this.tasks.listIterator();

		while (listIterator.hasNext()) {
			int k = listIterator.nextIndex();
			SectionRenderDispatcher.RenderSection.CompileTask compileTask = listIterator.next();
			if (compileTask.isCancelled.get()) {
				listIterator.remove();
			} else {
				double f = compileTask.getRenderOrigin().distToCenterSqr(vec3);
				if (!compileTask.isRecompile() && f < d) {
					d = f;
					i = k;
				}

				if (compileTask.isRecompile() && f < e) {
					e = f;
					j = k;
				}
			}
		}

		boolean bl = j >= 0;
		boolean bl2 = i >= 0;
		if (!bl || bl2 && (this.recompileQuota <= 0 || !(e < d))) {
			this.recompileQuota = 2;
			return this.removeTaskByIndex(i);
		} else {
			this.recompileQuota--;
			return this.removeTaskByIndex(j);
		}
	}

	public int size() {
		return this.tasks.size();
	}

	private SectionRenderDispatcher.RenderSection.@Nullable CompileTask removeTaskByIndex(int i) {
		return i >= 0 ? this.tasks.remove(i) : null;
	}

	public synchronized void clear() {
		for (SectionRenderDispatcher.RenderSection.CompileTask compileTask : this.tasks) {
			compileTask.cancel();
		}

		this.tasks.clear();
	}
}
