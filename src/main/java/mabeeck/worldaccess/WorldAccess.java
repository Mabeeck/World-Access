package mabeeck.worldaccess;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class WorldAccess implements ModInitializer {
	public static final String MOD_ID = "world-access";
	public static final Identifier FILE_CHANNEL = Identifier.of(MOD_ID, "file");
	public static final Identifier MANAGEMENT_CHANNEL = Identifier.of(MOD_ID, "management");
	public static int WritePermissionLevel;
	public static int ReadPermissionLevel;
	private static boolean StrictFilter;
	private static boolean FilterAllowExtensionless;
	private static Properties properties = new Properties();
	public static final String KEY_FILTER_EXTENSIONS = "filterFileExtensions";
	public static final String KEY_FILTER_HEADERS = "filterHeaders";
	public static final String KEY_FILTER_STRICT = "filterMode";
	public static final String KEY_PERMISSIONLEVEL_WRITE = "writePermissionLevel";
	public static final String KEY_PERMISSIONLEVEL_READ = "readPermissionLevel";
	public static final String KEY_ALLOW_EXTENSIONLESS = "allowExtensionless";
	public static final int MAX_PACKET_SIZE = 4096;


	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public record FilePacket(String file, byte[] data, boolean append) implements CustomPayload {
		public static final CustomPayload.Id<FilePacket> ID = new CustomPayload.Id<>(WorldAccess.FILE_CHANNEL);
		public static final PacketCodec<RegistryByteBuf, FilePacket> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, FilePacket::file,
				PacketCodecs.BYTE_ARRAY, FilePacket::data,
				PacketCodecs.BOOLEAN, FilePacket::append,
				FilePacket::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record ManagementPacket(int command, String info) implements CustomPayload {
		public static final CustomPayload.Id<ManagementPacket> ID = new CustomPayload.Id<>(WorldAccess.MANAGEMENT_CHANNEL);
		public static final PacketCodec<RegistryByteBuf, ManagementPacket> CODEC = PacketCodec.tuple(
				PacketCodecs.INTEGER, ManagementPacket::command,
				PacketCodecs.STRING, ManagementPacket::info,
				ManagementPacket::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}


	public static boolean filter(String file, byte[] data) {
		/*if (data.length<2) {
			LOGGER.warn("data.length<2: {} {}",file,Hex.encodeHex(data));
		}*/

		// Filter by filename
		String[] extensions = properties.getProperty(KEY_FILTER_EXTENSIONS).replace(" ","").split(",");
		String ext = file.split("\\.")[file.split("\\.").length - 1];
		if (!(file.split("\\.").length == 1&&FilterAllowExtensionless)) {
			boolean matched = false;
			for (String i : extensions) {
				if (Objects.equals(i, ext)) {
					if (StrictFilter) {
						matched = true;
						break;
					} else {
						LOGGER.warn("Filtered: Blacklisted file extension");
						return false;
					}
				}
			}
			if (StrictFilter&&!matched) {
				LOGGER.warn("Filtered: File extension not in whitelist");
				return false;
			}
		}

		// Filter by header
		String[] blacklisted_headers = properties.getProperty(KEY_FILTER_HEADERS).replace(" ","").split(",");

		for (String el : blacklisted_headers) {
			boolean flagged = true;
			try {
				byte[] header = org.apache.commons.codec.binary.Hex.decodeHex(el);
				if (header.length <= data.length) {
					for (int i = 0; i < header.length; i++) {
						if (header[i] != data[i]) {
							flagged = false;
							break;
						}
					}
				} else {
					// If the file is smaller than the header it is to be compared with then it is not a match
					flagged=false;
				}
			} catch (org.apache.commons.codec.DecoderException e) {
                LOGGER.error("Invalid file header: {}\nSkipping this filter.",el);
				flagged = false;
			}
			if (flagged) {
				LOGGER.warn("Filtered: Blacklisted header: "+el);
				return false;
			}
		}
		return true;
	}


	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// Load config
		try {
			if (!new File("./config").isDirectory()) {
				LOGGER.info("Cannot find config folder. Creating one.");
				new File("config").mkdir();
			}
			properties.load(new FileInputStream("config/WorldAccess.properties"));
		} catch (IOException e) {
			// If no config exists create one
			LOGGER.info("Couldn't find config file. Resorting to defaults.");
			properties.setProperty(KEY_FILTER_STRICT, "whitelist");
			properties.setProperty(KEY_FILTER_EXTENSIONS, "txt, md, mcfunction, mcmeta, json, nbt, ogg"+
					// Git related files
					", gitattributes, gitignore");
			properties.setProperty(KEY_FILTER_HEADERS,
					// ISO FiLes
					"4344303031, 4552020000, 4349534F, 4441410000000000, " +
							// Executables
							"23407E5E, 43723234, 4D5A900003, 504B0304, 7F454C46, " +
							// Java Bytecode
							"CAFEBABE, 4A4152435300");
			properties.setProperty(KEY_PERMISSIONLEVEL_WRITE, "4");
			properties.setProperty(KEY_PERMISSIONLEVEL_READ, "4");
			properties.setProperty(KEY_ALLOW_EXTENSIONLESS, "true");
			try {
				properties.store(new FileOutputStream("config/WorldAccess.properties"), "WorldAccess config");
			} catch (IOException e2) {
				LOGGER.error("Could not create config file!", e2);
			}
		}

		// Set settings from config and throw errors if the config is incorrectly written
		if (Objects.equals(properties.getProperty(KEY_FILTER_STRICT).toUpperCase(),"WHITELIST")||Objects.equals(properties.getProperty(KEY_FILTER_STRICT).toUpperCase(),"BLACKLIST")) {
			StrictFilter = Objects.equals(properties.getProperty(KEY_FILTER_STRICT).toUpperCase(),"WHITELIST");
		} else {
			LOGGER.error(KEY_FILTER_STRICT+" was not set correctly. Must be either whitelist or blacklist.\n" +
					"WorldAccess will not load until this is resolved.");
			return;
		}
		if (Objects.equals(properties.getProperty(KEY_PERMISSIONLEVEL_READ),"0")||Objects.equals(properties.getProperty(KEY_PERMISSIONLEVEL_READ),"1")||Objects.equals(properties.getProperty(KEY_PERMISSIONLEVEL_READ),"2")||Objects.equals(properties.getProperty(KEY_PERMISSIONLEVEL_READ),"3")||Objects.equals(properties.getProperty(KEY_PERMISSIONLEVEL_READ),"4")) {
			ReadPermissionLevel = Integer.parseInt(properties.getProperty(KEY_PERMISSIONLEVEL_READ));
		} else {
			LOGGER.error(KEY_PERMISSIONLEVEL_READ+" must be 0|1|2|3|4.\nWorldAccess will not load until this is resolved.");
			return;
		}
		if (Objects.equals(properties.getProperty(KEY_PERMISSIONLEVEL_WRITE),"0")||Objects.equals(properties.getProperty(KEY_PERMISSIONLEVEL_WRITE),"1")||Objects.equals(properties.getProperty(KEY_PERMISSIONLEVEL_WRITE),"2")||Objects.equals(properties.getProperty(KEY_PERMISSIONLEVEL_WRITE),"3")||Objects.equals(properties.getProperty(KEY_PERMISSIONLEVEL_WRITE),"4")) {
			WritePermissionLevel = Integer.parseInt(properties.getProperty(KEY_PERMISSIONLEVEL_WRITE));
		} else {
			LOGGER.error(KEY_PERMISSIONLEVEL_WRITE+" must be 0|1|2|3|4.\nWorldAccess will not load until this is resolved.");
			return;
		}
		if (Objects.equals(properties.getProperty(KEY_ALLOW_EXTENSIONLESS).toLowerCase(),"true")||Objects.equals(properties.getProperty(KEY_ALLOW_EXTENSIONLESS).toLowerCase(),"false")) {
			FilterAllowExtensionless = Objects.equals(properties.getProperty(KEY_ALLOW_EXTENSIONLESS).toLowerCase(),"true");
		} else {
			LOGGER.error(KEY_ALLOW_EXTENSIONLESS+" must be false|true.\nWorldAccess will not load until this is resolved.");
			return;
		}

		PayloadTypeRegistry.playS2C().register(FilePacket.ID, FilePacket.CODEC);
		PayloadTypeRegistry.playS2C().register(ManagementPacket.ID, ManagementPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(FilePacket.ID, FilePacket.CODEC);
		PayloadTypeRegistry.playC2S().register(ManagementPacket.ID, ManagementPacket.CODEC);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			if (environment.dedicated) {
			dispatcher.register(CommandManager.literal("worldaccess-pull")
					.requires(source -> source.isExecutedByPlayer()&&source.hasPermissionLevel(ReadPermissionLevel))
							.executes(context -> {
								context.getSource().sendFeedback(() -> Text.literal("Pulling everything!"), true);
								ServerPlayNetworking.send(context.getSource().getPlayerOrThrow(), new ManagementPacket(1, "*"));
								try {
									// Get world folder
									Path path = FabricLoader.getInstance().getGameDir();
									Properties properties = new Properties();
									properties.load(new FileInputStream(new File(path.resolve("server.properties").toUri())));
									path = path.resolve(Paths.get(properties.getProperty("level-name")));

									Stream<Path> files = Files.walk(path.resolve("datapacks")).filter(it -> !it.getFileName().toString().equals(".git"));
									// Delete everything in the client directory for this server
									ServerPlayNetworking.send(context.getSource().getPlayerOrThrow(), new WorldAccess.ManagementPacket(1, "*"));
									// Send the directories and files to the client
									for (Path el : files.toList()) {
										if (Files.isDirectory(el)) {
											ServerPlayNetworking.send(context.getSource().getPlayerOrThrow(), new WorldAccess.ManagementPacket(2, path.relativize(el).toString()));
										} else {
											ServerPlayNetworking.send(context.getSource().getPlayerOrThrow(), new WorldAccess.FilePacket(path.relativize(el).toString(), Files.readAllBytes(el), false));
										}
										WorldAccess.LOGGER.debug(el.toString());
									}
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
								return 0;
							}));
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(WorldAccess.ManagementPacket.ID, (payload, context) -> {
			context.server().execute(() -> {
				if (context.player().hasPermissionLevel(WritePermissionLevel)) {
					try {
						Path path = FabricLoader.getInstance().getGameDir();
						Properties properties = new Properties();
						properties.load(new FileInputStream(new File(path.resolve("server.properties").toUri())));
						path = path.resolve(Paths.get(properties.getProperty("level-name"))).normalize().toAbsolutePath();
						if (payload.command() == 1) {
							try {
								if (!Objects.equals(payload.info(), "datapack *")) {
									WorldAccess.LOGGER.error(context.player().getName() + " sent undefined delete instruction: " + payload.info());
									return;
								}
								FileUtils.deleteDirectory(path.resolve("datapacks").toFile());
							} catch (IOException e) {
								WorldAccess.LOGGER.error(e.getMessage());
							}
						} else if (payload.command() == 2) {
							try {
								Path folder = path.resolve(payload.info().replace("\\","/")).normalize().toAbsolutePath();
								if (folder.startsWith(path.resolve("datapacks"))) {
									folder.toFile().mkdirs();
								} else {
									WorldAccess.LOGGER.error(context.player().getName() + " sent out of bounds directory creation command: " + folder);
								}
							} catch (SecurityException e) {
								WorldAccess.LOGGER.error(e.getMessage());
							}
						}
					} catch (Exception e) {
						LOGGER.error(e.getMessage());
					}
				} else {
					LOGGER.info("Player with UUID {}({}) sent write command despite missing permissions.", context.player().getUuidAsString(), context.player().getName().getString());
				}
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(WorldAccess.FilePacket.ID, (payload, context) -> {
			context.server().execute(() -> {
				if (context.player().hasPermissionLevel(WritePermissionLevel)) {
					try {
						Path path = FabricLoader.getInstance().getGameDir();
						Properties properties = new Properties();
						properties.load(new FileInputStream(new File(path.resolve("server.properties").toUri())));
						path = path.resolve(Paths.get(properties.getProperty("level-name"))).toAbsolutePath().normalize();
						if (!Files.isDirectory(path)) {
							new File(path.toUri()).mkdirs();
						}
						Path file = path.resolve(payload.file().replace("\\","/")).normalize().toAbsolutePath();
						try {
							if (file.startsWith(path.resolve("datapacks"))) {
								// Append mode bypasses filters as headers have already been filtered with the first packet
								if (filter(file.getFileName().toString(), payload.data())||payload.append) {
									if (payload.append()) {
										if (Files.exists(file)&&Files.size(file)>WorldAccess.MAX_PACKET_SIZE-1) {
											Files.write(file, payload.data(), StandardOpenOption.WRITE, StandardOpenOption.APPEND);
										} else {
											// Possible attempt to bypass header filters
											LOGGER.warn("Player with UUID {}({}) sent write instruction for filtered(non-standard-behaviour) file: {}", context.player().getUuidAsString(), context.player().getName().toString(), file);
										}
									} else {
										Files.write(file, payload.data(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
									}
								} else {
									WorldAccess.LOGGER.warn("Player with UUID {}({}) sent write instruction for filtered file: {}", context.player().getUuidAsString(), context.player().getName().toString(), file);
								}
							} else {
								WorldAccess.LOGGER.error("Player with UUID {}({}) sent write instruction for out of bounds file: {}\nWrite requests are constrained to {}", context.player().getUuidAsString(), context.player().getName().toString(), file, path);
							}
						} catch (IOException e) {
							WorldAccess.LOGGER.error("IOException while writing file: {}",e.getMessage());
						}
					} catch (Exception e) {
						LOGGER.error(e.getMessage());
					}
				} else {
					LOGGER.info("Player with UUID {}({}) sent write command despite missing permissions.", context.player().getUuidAsString(), context.player().getName().toString());
				}
			});
		});
		LOGGER.info("WorldAccess loaded!");
	}
}
