package net.minecraft.client.multiplayer;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.Util;
import net.minecraft.util.thread.ConsecutiveExecutor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ServerList {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ConsecutiveExecutor IO_EXECUTOR = new ConsecutiveExecutor(Util.backgroundExecutor(), "server-list-io");
	private static final int MAX_HIDDEN_SERVERS = 16;
	private final Minecraft minecraft;
	private final List<ServerData> serverList = Lists.newArrayList();
	private final List<ServerData> hiddenServerList = Lists.newArrayList();

	public ServerList(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	public void load() {
		try {
			this.serverList.clear();
			this.hiddenServerList.clear();
			CompoundTag compoundTag = NbtIo.read(this.minecraft.gameDirectory.toPath().resolve("servers.dat"));
			if (compoundTag == null) {
				return;
			}

			compoundTag.getListOrEmpty("servers").compoundStream().forEach(compoundTagx -> {
				ServerData serverData = ServerData.read(compoundTagx);
				if (compoundTagx.getBooleanOr("hidden", false)) {
					this.hiddenServerList.add(serverData);
				} else {
					this.serverList.add(serverData);
				}
			});
		} catch (Exception exception) {
			LOGGER.error("Couldn't load server list", exception);
		}
	}

	public void save() {
		try {
			ListTag listTag = new ListTag();

			for (ServerData serverData : this.serverList) {
				CompoundTag compoundTag = serverData.write();
				compoundTag.putBoolean("hidden", false);
				listTag.add(compoundTag);
			}

			for (ServerData serverData : this.hiddenServerList) {
				CompoundTag compoundTag = serverData.write();
				compoundTag.putBoolean("hidden", true);
				listTag.add(compoundTag);
			}

			CompoundTag compoundTag2 = new CompoundTag();
			compoundTag2.put("servers", listTag);
			Path path = this.minecraft.gameDirectory.toPath();
			Path path2 = Files.createTempFile(path, "servers", ".dat");
			NbtIo.write(compoundTag2, path2);
			Path path3 = path.resolve("servers.dat_old");
			Path path4 = path.resolve("servers.dat");
			Util.safeReplaceFile(path4, path2, path3);
		} catch (Exception exception) {
			LOGGER.error("Couldn't save server list", exception);
		}
	}

	public ServerData get(int i) {
		return this.serverList.get(i);
	}

	public @Nullable ServerData get(String string) {
		for (ServerData serverData : this.serverList) {
			if (serverData.ip.equals(string)) {
				return serverData;
			}
		}

		for (ServerData serverData : this.hiddenServerList) {
			if (serverData.ip.equals(string)) {
				return serverData;
			}
		}

		return null;
	}

	public @Nullable ServerData unhide(String string) {
		for (int i = 0; i < this.hiddenServerList.size(); i++) {
			ServerData serverData = this.hiddenServerList.get(i);
			if (serverData.ip.equals(string)) {
				this.hiddenServerList.remove(i);
				this.serverList.add(serverData);
				return serverData;
			}
		}

		return null;
	}

	public void remove(ServerData serverData) {
		if (!this.serverList.remove(serverData)) {
			this.hiddenServerList.remove(serverData);
		}
	}

	public void add(ServerData serverData, boolean bl) {
		if (bl) {
			this.hiddenServerList.add(0, serverData);

			while (this.hiddenServerList.size() > 16) {
				this.hiddenServerList.remove(this.hiddenServerList.size() - 1);
			}
		} else {
			this.serverList.add(serverData);
		}
	}

	public int size() {
		return this.serverList.size();
	}

	public void swap(int i, int j) {
		ServerData serverData = this.get(i);
		this.serverList.set(i, this.get(j));
		this.serverList.set(j, serverData);
		this.save();
	}

	public void replace(int i, ServerData serverData) {
		this.serverList.set(i, serverData);
	}

	private static boolean set(ServerData serverData, List<ServerData> list) {
		for (int i = 0; i < list.size(); i++) {
			ServerData serverData2 = list.get(i);
			if (Objects.equals(serverData2.name, serverData.name) && serverData2.ip.equals(serverData.ip)) {
				list.set(i, serverData);
				return true;
			}
		}

		return false;
	}

	public static void saveSingleServer(ServerData serverData) {
		IO_EXECUTOR.schedule(() -> {
			ServerList serverList = new ServerList(Minecraft.getInstance());
			serverList.load();
			if (!set(serverData, serverList.serverList)) {
				set(serverData, serverList.hiddenServerList);
			}

			serverList.save();
		});
	}
}
