package net.mcreator.ordeal;

public final class OrdealAnimNoise {

	private OrdealAnimNoise() {}

	private static final String[] NAMES = { "Off", "Low", "Med", "High" };
	private static final float[] STRENGTH = { 0f, 0.5f, 1.0f, 1.8f };

	/** Degrees of sway at strength 1.0. */
	public static float R_AMP = 0.8f;
	/** Model pixels of drift at strength 1.0. */
	public static float T_AMP = 0.06f;

	public static int levels() { return NAMES.length; }

	public static String name(int level) { return NAMES[Math.floorMod(level, NAMES.length)]; }

	public static int next(int level) { return Math.floorMod(level + 1, NAMES.length); }

	private static float wave(double t, float freq, float phase) {
		return (float) Math.sin(t * freq + phase);
	}

	/** Smooth deterministic pseudo-noise in roughly [-1, 1]. */
	public static float noise(double t, int seed) {
		float p = seed * 1.7f;
		return wave(t, 1.3f, p) * 0.60f
				+ wave(t, 2.7f, p * 1.9f + 1.1f) * 0.30f
				+ wave(t, 5.1f, p * 0.7f + 2.3f) * 0.10f;
	}

	/** Sway a bone. boneSeed keeps each limb on its own wave so they never march in step. */
	public static void apply(OrdealAnimData.Pose pose, int boneSeed, int level) {
		apply(pose, boneSeed, level, 1f);
	}

	public static void apply(OrdealAnimData.Pose pose, int boneSeed, int level, float scale) {
		if (pose == null || level <= 0 || scale <= 0f) return;
		if (level >= STRENGTH.length) level = STRENGTH.length - 1;
		double t = System.nanoTime() * 1.0e-9;
		float s = STRENGTH[level] * scale;
		int b = boneSeed * 10;
		pose.rx += noise(t, b) * R_AMP * s;
		pose.ry += noise(t, b + 1) * R_AMP * s;
		pose.rz += noise(t, b + 2) * R_AMP * s;
		pose.x += noise(t, b + 3) * T_AMP * s;
		pose.y += noise(t, b + 4) * T_AMP * s;
		pose.z += noise(t, b + 5) * T_AMP * s;
	}

	/** Stable seed per bone name so a bone's wobble is the same every time it plays. */
	public static int seed(String bone) {
		int h = 0;
		for (int i = 0; i < bone.length(); i++) h = h * 31 + bone.charAt(i);
		return Math.floorMod(h, 97);
	}
}