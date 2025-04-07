package mabeeck.worldaccess;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.util.Objects;
import java.util.stream.Stream;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

public class WorldAccessClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		ClientPlayNetworking.registerGlobalReceiver(WorldAccess.ManagementPacket.ID, (payload, context) -> {
			context.client().execute(() -> {
				Path path = FabricLoader.getInstance().getGameDir().resolve("fetched_worlds").resolve(MinecraftClient.getInstance().getCurrentServerEntry().address).toAbsolutePath();
				if (!Files.isDirectory(path)) {
					new File(path.toUri()).mkdirs();
				}
				if (payload.command() == 1) {
                    try {
						Stream<Path> paths;
						if (Objects.equals(payload.info(), "*")) {
							paths = Files.list(path);
						} else {
                            WorldAccess.LOGGER.error("Undefined delete instruction received: "+payload.info());
							return;
						}
						for (Path el : paths.toList()) {
							WorldAccess.LOGGER.info(el.toString());
						}
                    } catch (IOException e) {
						WorldAccess.LOGGER.error(e.getMessage());
                    }
				} else if (payload.command() == 2) {
					try {
						Path folder = path.resolve(payload.info()).normalize();
						if (folder.startsWith(path)) {
							new File(folder.toUri()).mkdirs();
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
				Path path = FabricLoader.getInstance().getGameDir().resolve("fetched_worlds").resolve(MinecraftClient.getInstance().getCurrentServerEntry().address).toAbsolutePath();
				if (!Files.isDirectory(path)) {
					new File(path.toUri()).mkdirs();
				}
				Path file = path.resolve(payload.file()).normalize();
				try {
					if (file.startsWith(path)) {
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
	}
}