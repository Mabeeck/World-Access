package mabeeck.worldaccess;

import java.io.File;
import org.apache.commons.io.FileUtils;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class WorldAccessClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		ClientPlayNetworking.registerGlobalReceiver(WorldAccess.ManagementPacket.ID, (payload, context) -> {
			context.client().execute(() -> {
				Path path = FabricLoader.getInstance().getGameDir().resolve("fetched_worlds").resolve(MinecraftClient.getInstance().getCurrentServerEntry().address).toAbsolutePath().normalize();
				if (!Files.isDirectory(path)) {
					new File(path.toUri()).mkdirs();
				}
				if (payload.command() == 1) {
                    try {
						if (!Objects.equals(payload.info(), "*")) {
							WorldAccess.LOGGER.error("Undefined delete instruction received: "+payload.info());
							return;
						}
						FileUtils.deleteDirectory(path.toFile());
                    } catch (IOException e) {
						WorldAccess.LOGGER.error(e.getMessage());
                    }
				} else if (payload.command() == 2) {
					try {
						Path folder = path.resolve(payload.info()).normalize();
						if (folder.startsWith(path)) {
							folder.toFile().mkdirs();
						} else {
							WorldAccess.LOGGER.error("Received out of bounds directory creation command: "+folder);
						}
					} catch (SecurityException e) {
						WorldAccess.LOGGER.error(e.getMessage());
					}
				}
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(WorldAccess.FilePacket.ID, (payload, context) -> {
			context.client().execute(() -> {
				Path path = FabricLoader.getInstance().getGameDir().resolve("fetched_worlds").resolve(MinecraftClient.getInstance().getCurrentServerEntry().address).toAbsolutePath().normalize();
				if (!Files.isDirectory(path)) {
					new File(path.toUri()).mkdirs();
				}
				Path file = path.resolve(payload.file()).normalize();
				try {
					if (file.startsWith(path)&&WorldAccess.filter(file.getFileName().toString(), payload.data())) {
						if (payload.append()) {
							Files.write(file, payload.data(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
						} else {
							Files.write(file, payload.data(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
						}
					} else {
						WorldAccess.LOGGER.error("Received write instruction for out of bounds file: " + file);
					}
				} catch (IOException e) {
					WorldAccess.LOGGER.error(e.getMessage());
				}
			});
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			if (MinecraftClient.getInstance().getCurrentServerEntry() != null) {
				if (!MinecraftClient.getInstance().getCurrentServerEntry().isLocal()) {
					dispatcher.register(ClientCommandManager.literal("worldaccess-push")
							.executes(context -> {
								context.getSource().sendFeedback(Text.literal("Pushing everything!"));
								try {
									Path path = FabricLoader.getInstance().getGameDir().resolve("fetched_worlds").resolve(MinecraftClient.getInstance().getCurrentServerEntry().address).toAbsolutePath().normalize();
									Stream<Path> files = Files.walk(path.resolve("datapacks")).filter(it -> !it.getFileName().toString().equals(".git"));
									ClientPlayNetworking.send(new WorldAccess.ManagementPacket(1, "datapack *"));
									for (Path el : files.toList()) {
										if (Files.isDirectory(el)) {
											ClientPlayNetworking.send(new WorldAccess.ManagementPacket(2, path.relativize(el).toString()));
										} else {
											if (Files.size(el)>WorldAccess.MAX_PACKET_SIZE) {
												byte[] bytes = Files.readAllBytes(el);
												for (int i=WorldAccess.MAX_PACKET_SIZE;i<bytes.length;i+=WorldAccess.MAX_PACKET_SIZE) {
													ClientPlayNetworking.send(new WorldAccess.FilePacket(path.relativize(el).toString(), Arrays.copyOfRange(bytes, i-WorldAccess.MAX_PACKET_SIZE, i), i!=WorldAccess.MAX_PACKET_SIZE));
												}
											} else {
												ClientPlayNetworking.send(new WorldAccess.FilePacket(path.relativize(el).toString(), Files.readAllBytes(el), false));
											}
										}
										WorldAccess.LOGGER.debug(el.toString());
									}
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
								return 0;
							}));
				}
			}
		});
	}
}
