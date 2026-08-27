package net.mcreator.ordeal;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Ordeal Animator — /ordealadmin animator [name]
 *
 * Registers a second "ordealadmin" root; Brigadier merges it into the
 * existing /ordealadmin tree, so the existing command file is untouched.
 * The existing root keeps its own permission requirement; "animator"
 * additionally requires permission level 2 itself.
 *
 * The editor is a client screen, so this only opens in singleplayer /
 * LAN-host (integrated server). On a dedicated server it tells you so.
 * No client classes are referenced directly — resolved via Class.forName
 * inside a Dist.CLIENT guard, same pattern as IIC.
 */
@EventBusSubscriber
public class OrdealAnimatorCommand {

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(
				Commands.literal("ordealadmin")
						// brigadier keeps the first-registered root's requirement when the
						// two ordealadmin trees merge, so the gate lives on both roots
						.requires(src -> src.hasPermission(2)
								&& net.mcreator.ordeal.core.OrdealCommands.devOnly(src))
						.then(Commands.literal("anim")
								.then(Commands.literal("play")
										.then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.players())
												.then(Commands.argument("name", StringArgumentType.word()).suggests(CLIPS)
														.executes(ctx -> play(ctx, 1, -1))
														.then(Commands.literal("smooth")
																.executes(ctx -> play(ctx, 1, -1))
																.then(Commands.argument("ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
																		.executes(ctx -> play(ctx, 1, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ticks")))))
														.then(Commands.argument("loops", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1))
																.executes(ctx -> play(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "loops"), -1))
																.then(Commands.literal("smooth")
																		.executes(ctx -> play(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "loops"), -1))
																		.then(Commands.argument("ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
																				.executes(ctx -> play(ctx,
																						com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "loops"),
																						com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ticks")))))))))
								.then(Commands.literal("combo")
										.then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.players())
												.then(Commands.argument("names", StringArgumentType.greedyString()).suggests(CLIPS)
														.executes(OrdealAnimatorCommand::combo))))
								.then(Commands.literal("stop")
										.executes(ctx -> stop(ctx.getSource().getPlayerOrException()))
										.then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.players())
												.executes(OrdealAnimatorCommand::stopMany)))
								.then(Commands.literal("list").executes(OrdealAnimatorCommand::list))
								.then(Commands.literal("reload").executes(OrdealAnimatorCommand::reload)))
						.then(Commands.literal("animator")
								.requires(src -> src.hasPermission(2)
										&& net.mcreator.ordeal.core.OrdealCommands.devOnly(src))
								.executes(ctx -> open(ctx.getSource().getPlayerOrException(), ""))
								.then(Commands.argument("name", StringArgumentType.word())
										.executes(ctx -> open(ctx.getSource().getPlayerOrException(),
												StringArgumentType.getString(ctx, "name"))))));
	}

	/** Tab-completes with every clip the animator can see. */
	private static final com.mojang.brigadier.suggestion.SuggestionProvider<net.minecraft.commands.CommandSourceStack> CLIPS =
			(ctx, builder) -> {
				for (String n : OrdealAnimStore.list())
					builder.suggest(n);
				return builder.buildFuture();
			};

	// ---- play ---------------------------------------------------------------

	private static int play(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
			int loops, int blend) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return fire(ctx, net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "target"),
				StringArgumentType.getString(ctx, "name"), loops, blend);
	}

	private static int fire(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
			java.util.Collection<ServerPlayer> targets, String name, int loops, int blend) {
		if (!OrdealAnimStore.exists(name)) {
			ctx.getSource().sendFailure(Component.literal("no animation called " + name));
			return 0;
		}
		for (ServerPlayer sp : targets)
			OrdealAnim.play(sp, name, loops, blend);
		int n = targets.size();
		ctx.getSource().sendSuccess(() -> Component.literal("Playing '" + name + "' on " + n + " player(s)"
				+ (loops < 0 ? " (looping)" : loops > 1 ? " x" + loops : "")
				+ (blend >= 0 ? " smooth " + blend : "")), true);
		return n;
	}

	/** Clips back to back, cross-faded. Names split on spaces or commas. */
	private static int combo(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		String joined = StringArgumentType.getString(ctx, "names").trim().replaceAll("[\\s,]+", ",");
		var targets = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "target");
		for (ServerPlayer sp : targets)
			OrdealAnim.combo(sp, joined.split(","));
		int n = targets.size();
		ctx.getSource().sendSuccess(() -> Component.literal(
				"Playing combo [" + joined + "] on " + n + " player(s)"), true);
		return n;
	}

	// ---- stop / list / reload ----------------------------------------------

	private static int stop(ServerPlayer target) {
		OrdealAnim.stop(target);
		return 1;
	}

	private static int stopMany(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var targets = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "target");
		for (ServerPlayer sp : targets) OrdealAnim.stop(sp);
		int n = targets.size();
		ctx.getSource().sendSuccess(() -> Component.literal("Stopped anim on " + n + " player(s)"), true);
		return n;
	}

	private static int list(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
		java.util.List<String> all = OrdealAnimStore.list();
		if (all.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("No animations saved yet"), false);
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal(
				all.size() + " animation(s): " + String.join(", ", all)), false);
		return all.size();
	}

	/** Drop the clip cache so something you just saved plays without a restart. */
	private static int reload(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
		OrdealAnimPlayback.invalidate();
		ctx.getSource().sendSuccess(() -> Component.literal("Animation cache cleared"), true);
		return 1;
	}

	private static int open(ServerPlayer player, String name) {
		if (FMLEnvironment.dist != Dist.CLIENT) {
			player.displayClientMessage(
					Component.literal("§cThe animator is a client-side editor — use it in singleplayer/dev."), false);
			return 0;
		}
		try {
			Class.forName("net.mcreator.ordeal.OrdealAnimatorClient")
					.getMethod("open", String.class)
					.invoke(null, name);
			return 1;
		} catch (Throwable t) {
			player.displayClientMessage(
					Component.literal("§cAnimator failed to open: " + t.getClass().getSimpleName()), false);
			return 0;
		}
	}
}