package net.mcreator.ordeal;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class Fx {

	public static String DEFAULT_ARGS = "0 0 0 0 0 0 1 1 1 0 false true none";

	private Fx() {}

	public static void at(Entity e, String fx) {
		at(e, fx, DEFAULT_ARGS);
	}

	public static void at(Entity e, String fx, String args) {
		if (e == null || fx == null || fx.isEmpty()) return;
		if (!(e.level() instanceof ServerLevel sl) || sl.getServer() == null) return;
		sl.getServer().getCommands().performPrefixedCommand(
				new CommandSourceStack(CommandSource.NULL, e.position(), e.getRotationVector(), sl, 4,
						e.getName().getString(), e.getDisplayName(), sl.getServer(), e),
				"photon fx " + fx + " entity @s " + args);
	}

	public static void world(ServerLevel sl, Vec3 at, String fx) {
		if (sl == null || at == null || fx == null || fx.isEmpty() || sl.getServer() == null) return;
		sl.getServer().getCommands().performPrefixedCommand(
				new CommandSourceStack(CommandSource.NULL, at, Vec2.ZERO, sl, 4,
						"ordeal", Component.literal("ordeal"), sl.getServer(), null),
				"photon fx " + fx + " block ^ ^ ^");
	}
}