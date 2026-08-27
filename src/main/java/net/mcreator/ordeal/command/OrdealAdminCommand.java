package net.mcreator.ordeal.command;

import org.checkerframework.checker.units.qual.s;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.commands.Commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;

@EventBusSubscriber
public class OrdealAdminCommand {
	@SubscribeEvent
	public static void registerCommand(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("ordealadmin").requires(s -> s.hasPermission(4)).then(Commands.literal("stats")
				.then(Commands.literal("set").then(Commands.literal("xp").then(Commands.argument("name", DoubleArgumentType.doubleArg(0, 99999)))).then(Commands.literal("sp").then(Commands.argument("name", DoubleArgumentType.doubleArg(0, 99999))))
						.then(Commands.literal("talentSP").then(Commands.argument("name", DoubleArgumentType.doubleArg(0, 99999)))).then(Commands.literal("strength").then(Commands.argument("name", DoubleArgumentType.doubleArg(0, 100))))
						.then(Commands.literal("durability").then(Commands.argument("name", DoubleArgumentType.doubleArg(0, 100)))).then(Commands.literal("agility").then(Commands.argument("name", DoubleArgumentType.doubleArg(0, 100))))
						.then(Commands.literal("health").then(Commands.argument("name", DoubleArgumentType.doubleArg(0, 100)))).then(Commands.literal("chi").then(Commands.argument("name", DoubleArgumentType.doubleArg(0, 100))))
						.then(Commands.literal("chi_control").then(Commands.argument("name", DoubleArgumentType.doubleArg(0, 100)))).then(Commands.literal("chi_limit").then(Commands.argument("name", DoubleArgumentType.doubleArg(0, 100))))
						.then(Commands.literal("chi_limit").then(Commands.argument("name", DoubleArgumentType.doubleArg(0, 100)))).then(Commands.literal("perception").then(Commands.argument("name", DoubleArgumentType.doubleArg(0, 100))))))
				.then(Commands.literal("talents")
						.then(Commands.literal("set").then(Commands.literal("talent1").then(Commands.argument("name", StringArgumentType.word()))).then(Commands.literal("talent2").then(Commands.argument("name", StringArgumentType.word()))))));
	}

}