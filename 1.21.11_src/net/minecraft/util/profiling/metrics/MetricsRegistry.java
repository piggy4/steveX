package net.minecraft.util.profiling.metrics;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public class MetricsRegistry {
	public static final MetricsRegistry INSTANCE = new MetricsRegistry();
	private final WeakHashMap<ProfilerMeasured, Void> measuredInstances = new WeakHashMap<>();

	private MetricsRegistry() {
	}

	public void add(ProfilerMeasured profilerMeasured) {
		this.measuredInstances.put(profilerMeasured, null);
	}

	public List<MetricSampler> getRegisteredSamplers() {
		Map<String, List<MetricSampler>> map = this.measuredInstances
			.keySet()
			.stream()
			.flatMap(profilerMeasured -> profilerMeasured.profiledMetrics().stream())
			.collect(Collectors.groupingBy(MetricSampler::getName));
		return aggregateDuplicates(map);
	}

	private static List<MetricSampler> aggregateDuplicates(Map<String, List<MetricSampler>> map) {
		return map.entrySet().stream().map(entry -> {
			String string = entry.getKey();
			List<MetricSampler> list = entry.getValue();
			return list.size() > 1 ? new MetricsRegistry.AggregatedMetricSampler(string, list) : list.get(0);
		}).collect(Collectors.toList());
	}

	static class AggregatedMetricSampler extends MetricSampler {
		private final List<MetricSampler> delegates;

		AggregatedMetricSampler(String string, List<MetricSampler> list) {
			super(string, list.get(0).getCategory(), () -> averageValueFromDelegates(list), () -> beforeTick(list), thresholdTest(list));
			this.delegates = list;
		}

		private static MetricSampler.ThresholdTest thresholdTest(List<MetricSampler> list) {
			return d -> list.stream().anyMatch(metricSampler -> metricSampler.thresholdTest != null ? metricSampler.thresholdTest.test(d) : false);
		}

		private static void beforeTick(List<MetricSampler> list) {
			for (MetricSampler metricSampler : list) {
				metricSampler.onStartTick();
			}
		}

		private static double averageValueFromDelegates(List<MetricSampler> list) {
			double d = 0.0;

			for (MetricSampler metricSampler : list) {
				d += metricSampler.getSampler().getAsDouble();
			}

			return d / list.size();
		}

		@Override
		public boolean equals(@Nullable Object object) {
			if (this == object) {
				return true;
			}

			if (object == null || this.getClass() != object.getClass()) {
				return false;
			}

			if (!super.equals(object)) {
				return false;
			}

			MetricsRegistry.AggregatedMetricSampler aggregatedMetricSampler = (MetricsRegistry.AggregatedMetricSampler)object;
			return this.delegates.equals(aggregatedMetricSampler.delegates);
		}

		@Override
		public int hashCode() {
			return Objects.hash(super.hashCode(), this.delegates);
		}
	}
}
