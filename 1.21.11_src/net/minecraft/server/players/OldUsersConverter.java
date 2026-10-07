package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.yggdrasil.ProfileNotFoundException;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.notifications.EmptyNotificationService;
import net.minecraft.util.StringUtil;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class OldUsersConverter {
	static final Logger LOGGER = LogUtils.getLogger();
	public static final File OLD_IPBANLIST = new File("banned-ips.txt");
	public static final File OLD_USERBANLIST = new File("banned-players.txt");
	public static final File OLD_OPLIST = new File("ops.txt");
	public static final File OLD_WHITELIST = new File("white-list.txt");

	static List<String> readOldListFormat(File file, Map<String, String[]> map) throws IOException {
		List<String> list = Files.readLines(file, StandardCharsets.UTF_8);

		for (String string : list) {
			string = string.trim();
			if (!string.startsWith("#") && !string.isEmpty()) {
				String[] strings = string.split("\\|");
				map.put(strings[0].toLowerCase(Locale.ROOT), strings);
			}
		}

		return list;
	}

	private static void lookupPlayers(MinecraftServer minecraftServer, Collection<String> collection, ProfileLookupCallback profileLookupCallback) {
		String[] strings = collection.stream().filter(stringx -> !StringUtil.isNullOrEmpty(stringx)).toArray(String[]::new);
		if (minecraftServer.usesAuthentication()) {
			minecraftServer.services().profileRepository().findProfilesByNames(strings, profileLookupCallback);
		} else {
			for (String string : strings) {
				profileLookupCallback.onProfileLookupSucceeded(string, UUIDUtil.createOfflinePlayerUUID(string));
			}
		}
	}

	public static boolean convertUserBanlist(MinecraftServer minecraftServer) {
		final UserBanList userBanList = new UserBanList(PlayerList.USERBANLIST_FILE, new EmptyNotificationService());
		if (OLD_USERBANLIST.exists() && OLD_USERBANLIST.isFile()) {
			if (userBanList.getFile().exists()) {
				try {
					userBanList.load();
				} catch (IOException iOException) {
					LOGGER.warn("Could not load existing file {}", userBanList.getFile().getName(), iOException);
				}
			}

			try {
				final Map<String, String[]> map = Maps.newHashMap();
				readOldListFormat(OLD_USERBANLIST, map);
				ProfileLookupCallback profileLookupCallback = new ProfileLookupCallback() {
					@Override
					public void onProfileLookupSucceeded(String string, UUID uUID) {
						NameAndId nameAndId = new NameAndId(uUID, string);
						minecraftServer.services().nameToIdCache().add(nameAndId);
						String[] strings = map.get(nameAndId.name().toLowerCase(Locale.ROOT));
						if (strings == null) {
							OldUsersConverter.LOGGER.warn("Could not convert user banlist entry for {}", nameAndId.name());
							throw new OldUsersConverter.ConversionError("Profile not in the conversionlist");
						}

						Date date = strings.length > 1 ? OldUsersConverter.parseDate(strings[1], null) : null;
						String string2 = strings.length > 2 ? strings[2] : null;
						Date date2 = strings.length > 3 ? OldUsersConverter.parseDate(strings[3], null) : null;
						String string3 = strings.length > 4 ? strings[4] : null;
						userBanList.add(new UserBanListEntry(nameAndId, date, string2, date2, string3));
					}

					@Override
					public void onProfileLookupFailed(String string, Exception exception) {
						OldUsersConverter.LOGGER.warn("Could not lookup user banlist entry for {}", string, exception);
						if (!(exception instanceof ProfileNotFoundException)) {
							throw new OldUsersConverter.ConversionError("Could not request user " + string + " from backend systems", exception);
						}
					}
				};
				lookupPlayers(minecraftServer, map.keySet(), profileLookupCallback);
				userBanList.save();
				renameOldFile(OLD_USERBANLIST);
				return true;
			} catch (IOException iOException) {
				LOGGER.warn("Could not read old user banlist to convert it!", iOException);
				return false;
			} catch (OldUsersConverter.ConversionError conversionError) {
				LOGGER.error("Conversion failed, please try again later", conversionError);
				return false;
			}
		} else {
			return true;
		}
	}

	public static boolean convertIpBanlist(MinecraftServer minecraftServer) {
		IpBanList ipBanList = new IpBanList(PlayerList.IPBANLIST_FILE, new EmptyNotificationService());
		if (OLD_IPBANLIST.exists() && OLD_IPBANLIST.isFile()) {
			if (ipBanList.getFile().exists()) {
				try {
					ipBanList.load();
				} catch (IOException iOException) {
					LOGGER.warn("Could not load existing file {}", ipBanList.getFile().getName(), iOException);
				}
			}

			try {
				Map<String, String[]> map = Maps.newHashMap();
				readOldListFormat(OLD_IPBANLIST, map);

				for (String string : map.keySet()) {
					String[] strings = map.get(string);
					Date date = strings.length > 1 ? parseDate(strings[1], null) : null;
					String string2 = strings.length > 2 ? strings[2] : null;
					Date date2 = strings.length > 3 ? parseDate(strings[3], null) : null;
					String string3 = strings.length > 4 ? strings[4] : null;
					ipBanList.add(new IpBanListEntry(string, date, string2, date2, string3));
				}

				ipBanList.save();
				renameOldFile(OLD_IPBANLIST);
				return true;
			} catch (IOException iOException) {
				LOGGER.warn("Could not parse old ip banlist to convert it!", iOException);
				return false;
			}
		} else {
			return true;
		}
	}

	public static boolean convertOpsList(MinecraftServer minecraftServer) {
		final ServerOpList serverOpList = new ServerOpList(PlayerList.OPLIST_FILE, new EmptyNotificationService());
		if (OLD_OPLIST.exists() && OLD_OPLIST.isFile()) {
			if (serverOpList.getFile().exists()) {
				try {
					serverOpList.load();
				} catch (IOException iOException) {
					LOGGER.warn("Could not load existing file {}", serverOpList.getFile().getName(), iOException);
				}
			}

			try {
				List<String> list = Files.readLines(OLD_OPLIST, StandardCharsets.UTF_8);
				ProfileLookupCallback profileLookupCallback = new ProfileLookupCallback() {
					@Override
					public void onProfileLookupSucceeded(String string, UUID uUID) {
						NameAndId nameAndId = new NameAndId(uUID, string);
						minecraftServer.services().nameToIdCache().add(nameAndId);
						serverOpList.add(new ServerOpListEntry(nameAndId, minecraftServer.operatorUserPermissions(), false));
					}

					@Override
					public void onProfileLookupFailed(String string, Exception exception) {
						OldUsersConverter.LOGGER.warn("Could not lookup oplist entry for {}", string, exception);
						if (!(exception instanceof ProfileNotFoundException)) {
							throw new OldUsersConverter.ConversionError("Could not request user " + string + " from backend systems", exception);
						}
					}
				};
				lookupPlayers(minecraftServer, list, profileLookupCallback);
				serverOpList.save();
				renameOldFile(OLD_OPLIST);
				return true;
			} catch (IOException iOException) {
				LOGGER.warn("Could not read old oplist to convert it!", iOException);
				return false;
			} catch (OldUsersConverter.ConversionError conversionError) {
				LOGGER.error("Conversion failed, please try again later", conversionError);
				return false;
			}
		} else {
			return true;
		}
	}

	public static boolean convertWhiteList(MinecraftServer minecraftServer) {
		final UserWhiteList userWhiteList = new UserWhiteList(PlayerList.WHITELIST_FILE, new EmptyNotificationService());
		if (OLD_WHITELIST.exists() && OLD_WHITELIST.isFile()) {
			if (userWhiteList.getFile().exists()) {
				try {
					userWhiteList.load();
				} catch (IOException iOException) {
					LOGGER.warn("Could not load existing file {}", userWhiteList.getFile().getName(), iOException);
				}
			}

			try {
				List<String> list = Files.readLines(OLD_WHITELIST, StandardCharsets.UTF_8);
				ProfileLookupCallback profileLookupCallback = new ProfileLookupCallback() {
					@Override
					public void onProfileLookupSucceeded(String string, UUID uUID) {
						NameAndId nameAndId = new NameAndId(uUID, string);
						minecraftServer.services().nameToIdCache().add(nameAndId);
						userWhiteList.add(new UserWhiteListEntry(nameAndId));
					}

					@Override
					public void onProfileLookupFailed(String string, Exception exception) {
						OldUsersConverter.LOGGER.warn("Could not lookup user whitelist entry for {}", string, exception);
						if (!(exception instanceof ProfileNotFoundException)) {
							throw new OldUsersConverter.ConversionError("Could not request user " + string + " from backend systems", exception);
						}
					}
				};
				lookupPlayers(minecraftServer, list, profileLookupCallback);
				userWhiteList.save();
				renameOldFile(OLD_WHITELIST);
				return true;
			} catch (IOException iOException) {
				LOGGER.warn("Could not read old whitelist to convert it!", iOException);
				return false;
			} catch (OldUsersConverter.ConversionError conversionError) {
				LOGGER.error("Conversion failed, please try again later", conversionError);
				return false;
			}
		} else {
			return true;
		}
	}

	public static @Nullable UUID convertMobOwnerIfNecessary(MinecraftServer minecraftServer, String string) {
		if (!StringUtil.isNullOrEmpty(string) && string.length() <= 16) {
			Optional<UUID> optional = minecraftServer.services().nameToIdCache().get(string).map(NameAndId::id);
			if (optional.isPresent()) {
				return optional.get();
			} else if (!minecraftServer.isSingleplayer() && minecraftServer.usesAuthentication()) {
				final List<NameAndId> list = new ArrayList<>();
				ProfileLookupCallback profileLookupCallback = new ProfileLookupCallback() {
					@Override
					public void onProfileLookupSucceeded(String string, UUID uUID) {
						NameAndId nameAndId = new NameAndId(uUID, string);
						minecraftServer.services().nameToIdCache().add(nameAndId);
						list.add(nameAndId);
					}

					@Override
					public void onProfileLookupFailed(String string, Exception exception) {
						OldUsersConverter.LOGGER.warn("Could not lookup user whitelist entry for {}", string, exception);
					}
				};
				lookupPlayers(minecraftServer, Lists.newArrayList(string), profileLookupCallback);
				return !list.isEmpty() ? list.getFirst().id() : null;
			} else {
				return UUIDUtil.createOfflinePlayerUUID(string);
			}
		} else {
			try {
				return UUID.fromString(string);
			} catch (IllegalArgumentException illegalArgumentException) {
				return null;
			}
		}
	}

	public static boolean convertPlayers(DedicatedServer dedicatedServer) {
		final File file = getWorldPlayersDirectory(dedicatedServer);
		final File file2 = new File(file.getParentFile(), "playerdata");
		final File file3 = new File(file.getParentFile(), "unknownplayers");
		if (file.exists() && file.isDirectory()) {
			File[] files = file.listFiles();
			List<String> list = Lists.newArrayList();

			for (File file4 : files) {
				String string = file4.getName();
				if (string.toLowerCase(Locale.ROOT).endsWith(".dat")) {
					String string2 = string.substring(0, string.length() - ".dat".length());
					if (!string2.isEmpty()) {
						list.add(string2);
					}
				}
			}

			try {
				final String[] strings = list.toArray(new String[list.size()]);
				ProfileLookupCallback profileLookupCallback = new ProfileLookupCallback() {
					@Override
					public void onProfileLookupSucceeded(String string, UUID uUID) {
						NameAndId nameAndId = new NameAndId(uUID, string);
						dedicatedServer.services().nameToIdCache().add(nameAndId);
						this.movePlayerFile(file2, this.getFileNameForProfile(string), uUID.toString());
					}

					@Override
					public void onProfileLookupFailed(String string, Exception exception) {
						OldUsersConverter.LOGGER.warn("Could not lookup user uuid for {}", string, exception);
						if (exception instanceof ProfileNotFoundException) {
							String string2 = this.getFileNameForProfile(string);
							this.movePlayerFile(file3, string2, string2);
						} else {
							throw new OldUsersConverter.ConversionError("Could not request user " + string + " from backend systems", exception);
						}
					}

					private void movePlayerFile(File file, String string, String string2) {
						File file2x = new File(file, string + ".dat");
						File file3x = new File(file, string2 + ".dat");
						OldUsersConverter.ensureDirectoryExists(file);
						if (!file2x.renameTo(file3x)) {
							throw new OldUsersConverter.ConversionError("Could not convert file for " + string);
						}
					}

					private String getFileNameForProfile(String string) {
						String string2 = null;

						for (String string3 : strings) {
							if (string3 != null && string3.equalsIgnoreCase(string)) {
								string2 = string3;
								break;
							}
						}

						if (string2 == null) {
							throw new OldUsersConverter.ConversionError("Could not find the filename for " + string + " anymore");
						} else {
							return string2;
						}
					}
				};
				lookupPlayers(dedicatedServer, Lists.newArrayList(strings), profileLookupCallback);
				return true;
			} catch (OldUsersConverter.ConversionError conversionError) {
				LOGGER.error("Conversion failed, please try again later", conversionError);
				return false;
			}
		} else {
			return true;
		}
	}

	static void ensureDirectoryExists(File file) {
		if (file.exists()) {
			if (!file.isDirectory()) {
				throw new OldUsersConverter.ConversionError("Can't create directory " + file.getName() + " in world save directory.");
			}
		} else {
			if (!file.mkdirs()) {
				throw new OldUsersConverter.ConversionError("Can't create directory " + file.getName() + " in world save directory.");
			}
		}
	}

	public static boolean serverReadyAfterUserconversion(MinecraftServer minecraftServer) {
		boolean bl = areOldUserlistsRemoved();
		return bl && areOldPlayersConverted(minecraftServer);
	}

	private static boolean areOldUserlistsRemoved() {
		boolean bl = false;
		if (OLD_USERBANLIST.exists() && OLD_USERBANLIST.isFile()) {
			bl = true;
		}

		boolean bl2 = false;
		if (OLD_IPBANLIST.exists() && OLD_IPBANLIST.isFile()) {
			bl2 = true;
		}

		boolean bl3 = false;
		if (OLD_OPLIST.exists() && OLD_OPLIST.isFile()) {
			bl3 = true;
		}

		boolean bl4 = false;
		if (OLD_WHITELIST.exists() && OLD_WHITELIST.isFile()) {
			bl4 = true;
		}

		if (!bl && !bl2 && !bl3 && !bl4) {
			return true;
		}

		LOGGER.warn("**** FAILED TO START THE SERVER AFTER ACCOUNT CONVERSION!");
		LOGGER.warn("** please remove the following files and restart the server:");
		if (bl) {
			LOGGER.warn("* {}", OLD_USERBANLIST.getName());
		}

		if (bl2) {
			LOGGER.warn("* {}", OLD_IPBANLIST.getName());
		}

		if (bl3) {
			LOGGER.warn("* {}", OLD_OPLIST.getName());
		}

		if (bl4) {
			LOGGER.warn("* {}", OLD_WHITELIST.getName());
		}

		return false;
	}

	private static boolean areOldPlayersConverted(MinecraftServer minecraftServer) {
		File file = getWorldPlayersDirectory(minecraftServer);
		if (!file.exists() || !file.isDirectory() || file.list().length <= 0 && file.delete()) {
			return true;
		}

		LOGGER.warn("**** DETECTED OLD PLAYER DIRECTORY IN THE WORLD SAVE");
		LOGGER.warn("**** THIS USUALLY HAPPENS WHEN THE AUTOMATIC CONVERSION FAILED IN SOME WAY");
		LOGGER.warn("** please restart the server and if the problem persists, remove the directory '{}'", file.getPath());
		return false;
	}

	private static File getWorldPlayersDirectory(MinecraftServer minecraftServer) {
		return minecraftServer.getWorldPath(LevelResource.PLAYER_OLD_DATA_DIR).toFile();
	}

	private static void renameOldFile(File file) {
		File file2 = new File(file.getName() + ".converted");
		file.renameTo(file2);
	}

	static Date parseDate(String string, Date date) {
		Date date2;
		try {
			date2 = BanListEntry.DATE_FORMAT.parse(string);
		} catch (ParseException parseException) {
			date2 = date;
		}

		return date2;
	}

	static class ConversionError extends RuntimeException {
		ConversionError(String string, Throwable throwable) {
			super(string, throwable);
		}

		ConversionError(String string) {
			super(string);
		}
	}
}
