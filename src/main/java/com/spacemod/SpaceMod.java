package com.spacemod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

public class SpaceMod implements ModInitializer {
	public static final String MOD_ID = "spacemod";

	/** Altura (Y) en el Overworld a partir de la cual pasas al espacio. Cambiala si quieres. */
	public static final int SPACE_Y = 330;

	/** Altura a la que apareces en el espacio (no hay piso). */
	public static final int SPACE_SPAWN_Y = 200;

	/** Si caes por debajo de esta altura en el espacio, vuelves a la Tierra. */
	public static final int RETURN_Y = 20;

	public static final RegistryKey<World> SPACE =
			RegistryKey.of(RegistryKeys.WORLD, new Identifier(MOD_ID, "space"));

	/** Punto de llegada en el espacio (los planetas estan muy lejos de aqui). */
	public static final BlockPos SPACE_ORIGIN = new BlockPos(0, SPACE_SPAWN_Y, 0);

	/**
	 * La Tierra (Minecraft) que se ve a lo lejos en el espacio. Es virtual: la dibuja el cliente
	 * y el servidor solo detecta cuando llegas a su superficie.
	 */
	public static final Vec3d EARTH_CENTER = new Vec3d(0, 200, 3200);
	public static final double EARTH_RADIUS = 900;

	/** Altura a la que reapareces sobre la Tierra al entrar desde el espacio (menor que SPACE_Y). */
	public static final int REENTRY_Y = 300;

	private final Planets planets = new Planets();

	/** Donde estaba cada jugador en la Tierra al salir, para devolverlo ahi. */
	private final Map<UUID, BlockPos> launchPoints = new HashMap<>();

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(this::tick);
	}

	private void tick(MinecraftServer server) {
		ServerWorld space = server.getWorld(SPACE);
		ServerWorld overworld = server.getOverworld();
		if (space == null) {
			return;
		}

		// Construye los planetas poco a poco (una rebanada por tick, solo la primera vez).
		planets.step(space);

		if (server.getTicks() % 5 != 0) {
			return;
		}

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.isSpectator()) {
				continue;
			}
			RegistryKey<World> dim = player.getWorld().getRegistryKey();

			if (dim == World.OVERWORLD && player.getY() >= SPACE_Y) {
				goToSpace(player, space);
			} else if (dim == SPACE) {
				if (player.getPos().squaredDistanceTo(EARTH_CENTER) <= EARTH_RADIUS * EARTH_RADIUS) {
					enterEarth(player, overworld);
				} else if (player.getY() < RETURN_Y) {
					returnToEarth(player, overworld);
				} else {
					applySpaceEffects(player);
				}
			}
		}
	}

	private void goToSpace(ServerPlayerEntity player, ServerWorld space) {
		launchPoints.put(player.getUuid(), player.getBlockPos());

		// Sin piso: en el espacio tienes que volar o caeras de vuelta a la Tierra.
		player.teleport(space, SPACE_ORIGIN.getX() + 0.5, SPACE_ORIGIN.getY(), SPACE_ORIGIN.getZ() + 0.5,
				player.getYaw(), player.getPitch());
		player.setVelocity(Vec3d.ZERO);
		player.velocityModified = true;
		player.fallDistance = 0;
	}

	private void returnToEarth(ServerPlayerEntity player, ServerWorld overworld) {
		BlockPos launch = launchPoints.remove(player.getUuid());
		if (launch == null) {
			launch = overworld.getSpawnPos();
		}
		int x = launch.getX();
		int z = launch.getZ();
		int y = overworld.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) + 1;

		player.teleport(overworld, x + 0.5, y, z + 0.5, player.getYaw(), player.getPitch());
		player.setVelocity(Vec3d.ZERO);
		player.velocityModified = true;
		player.fallDistance = 0;
		player.setAir(player.getMaxAir());
	}

	/** Llegaste a la Tierra: reentras sobre tu punto de despegue, cayendo despacio. */
	private void enterEarth(ServerPlayerEntity player, ServerWorld overworld) {
		BlockPos launch = launchPoints.remove(player.getUuid());
		if (launch == null) {
			launch = overworld.getSpawnPos();
		}
		player.teleport(overworld, launch.getX() + 0.5, REENTRY_Y, launch.getZ() + 0.5,
				player.getYaw(), player.getPitch());
		player.setVelocity(Vec3d.ZERO);
		player.velocityModified = true;
		player.fallDistance = 0;
		player.setAir(player.getMaxAir());
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 600, 0, true, false, true));
	}

	private void applySpaceEffects(ServerPlayerEntity player) {
		// Gravedad baja.
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 30, 0, true, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 30, 3, true, false, false));
		player.fallDistance = 0;

		// Sin aire: necesitas un casco puesto (cualquier casco) para respirar.
		if (player.isCreative()) {
			return;
		}
		ItemStack helmet = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD);
		if (!helmet.isEmpty()) {
			player.setAir(player.getMaxAir());
			return;
		}
		int air = player.getAir() - 10;
		if (air <= -20) {
			player.setAir(0);
			player.damage(player.getDamageSources().drown(), 2.0F);
		} else {
			player.setAir(air);
		}
	}
}
