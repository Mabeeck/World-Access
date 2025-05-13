package mabeeck.worldaccess;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.FileInputStream;
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
	public static final int WritePermissionLevel = 4;
	public static final int ReadPermissionLevel = 4;

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


	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

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
									Path path = FabricLoader.getInstance().getGameDir();
									Properties properties = new Properties();
									properties.load(new FileInputStream(new File(path.resolve("server.properties").toUri())));
									path = path.resolve(Paths.get(properties.getProperty("level-name")));
									Stream<Path> files = Files.walk(path.resolve("datapacks"));
									ServerPlayNetworking.send(context.getSource().getPlayerOrThrow(), new WorldAccess.ManagementPacket(1, "*"));
									for (Path el : files.toList()) {
										if (Files.isDirectory(el)) {
											ServerPlayNetworking.send(context.getSource().getPlayerOrThrow(), new WorldAccess.ManagementPacket(2, path.relativize(el).toString()));
										} else {
											ServerPlayNetworking.send(context.getSource().getPlayerOrThrow(), new WorldAccess.FilePacket(path.relativize(el).toString(), Files.readAllBytes(el), false));
										}
										WorldAccess.LOGGER.info(el.toString());
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
								Path folder = path.resolve(payload.info()).normalize().toAbsolutePath();
								if (folder.startsWith(path)) {
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
					LOGGER.warn("Player with UUID {}({}) sent write command despite missing permissions.", context.player().getUuidAsString(), context.player().getName().toString());
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
						Path file = path.resolve(payload.file()).normalize().toAbsolutePath();
						try {
							if (file.startsWith(path)) {
								if (payload.append()) {
									Files.write(file, payload.data(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
								} else {
									Files.write(file, payload.data());
								}
							} else {
								WorldAccess.LOGGER.error("Player with UUID {}({}) sent write instruction for out of bounds file: {}\nWrite requests are constrained to {}", context.player().getUuidAsString(), context.player().getName(), file, path);
							}
						} catch (IOException e) {
							WorldAccess.LOGGER.error(e.getMessage());
						}
					} catch (Exception e) {
						LOGGER.error(e.getMessage());
					}
				} else {
					LOGGER.warn("Player with UUID {}({}) sent write command despite missing permissions.", context.player().getUuidAsString(), context.player().getName().toString());
				}
			});
		});
	}
}
