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
import java.util.Properties;
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
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public record FilePacket(String file, byte[] data, boolean append) implements CustomPayload {
		public static final CustomPayload.Id<FilePacket> ID = new CustomPayload.Id<>(WorldAccess.FILE_CHANNEL);
		public static final PacketCodec<RegistryByteBuf, FilePacket> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, FilePacket::file,
				PacketCodecs.BYTE_ARRAY, FilePacket::data,
				PacketCodecs.BOOL, FilePacket::append,
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
		PayloadTypeRegistry.playC2S().register(FilePacket.ID, FilePacket.CODEC);
		PayloadTypeRegistry.playS2C().register(ManagementPacket.ID, ManagementPacket.CODEC);

		LOGGER.warn("THIS IS AN ALPHA VERSION! WRITE ACCESS CHECK NOT IMPLEMENTED YET!");
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			if (environment.dedicated) {
			dispatcher.register(CommandManager.literal("worldaccess")
					.requires(source -> source.isExecutedByPlayer()&&source.hasPermissionLevel(3))
					.then(CommandManager.literal("pull")
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
								return 1;
							})));
			}
		});
	}
}
