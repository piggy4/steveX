package net.minecraft.server.network.config;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ClientboundCodeOfConductPacket;
import net.minecraft.server.network.ConfigurationTask;

public class ServerCodeOfConductConfigurationTask implements ConfigurationTask {
	public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type("server_code_of_conduct");
	private final Supplier<String> codeOfConduct;

	public ServerCodeOfConductConfigurationTask(Supplier<String> supplier) {
		this.codeOfConduct = supplier;
	}

	@Override
	public void start(Consumer<Packet<?>> consumer) {
		consumer.accept(new ClientboundCodeOfConductPacket(this.codeOfConduct.get()));
	}

	@Override
	public ConfigurationTask.Type type() {
		return TYPE;
	}
}
