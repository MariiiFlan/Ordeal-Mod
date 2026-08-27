package net.mcreator.ordeal.core;

import net.minecraft.world.entity.Entity;

/** Server-side key state. Use these in procedure Java-code blocks. */
public class OrdealInput {

	public static boolean attack(Entity e)      { return flag(e, "attack"); }
	public static boolean ability1(Entity e)    { return flag(e, "ability1"); }
	public static boolean ability2(Entity e)    { return flag(e, "ability2"); }
	public static boolean ability3(Entity e)    { return flag(e, "ability3"); }
	public static boolean ability4(Entity e)    { return flag(e, "ability4"); }
	public static boolean ability5(Entity e)    { return flag(e, "ability5"); }
	public static boolean combatMode(Entity e)  { return flag(e, "combatMode"); }
	public static boolean forward(Entity e)     { return flag(e, "forward"); }
	public static boolean back(Entity e)        { return flag(e, "back"); }
	public static boolean left(Entity e)        { return flag(e, "left"); }
	public static boolean right(Entity e)       { return flag(e, "right"); }
	public static boolean jump(Entity e)        { return flag(e, "jump"); }
	public static boolean sneak(Entity e)       { return flag(e, "sneak"); }
	public static boolean sprint(Entity e)      { return flag(e, "sprint"); }

	private static boolean flag(Entity e, String key) {
		return e != null && e.getPersistentData().getBoolean("ordeal_" + key);
	}
}