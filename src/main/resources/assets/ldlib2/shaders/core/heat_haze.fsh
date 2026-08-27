#version 330 core

uniform vec2 ScreenSize;
uniform float GameTime;
uniform float DistortionStrength;
uniform float HeatSpeed;
uniform vec4 ColorHDR;
uniform float FresnelPower;

uniform sampler2D NoiseTexture;
uniform sampler2D SamplerSceneColor;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;
in vec3 vViewPos;
in vec3 vViewNormal;

out vec4 fragColor;

// Fresnel effect to achieve that its over there! thing?
float FresnelEffect(vec3 Normal, vec3 ViewDir, float power) {
    float cosTheta = clamp(dot(normalize(Normal), normalize(ViewDir)), 0.0, 1.0);
    return pow(1.0 - cosTheta, power);
}

void main() {
    // Screen UV calc for scene sampling
    vec2 screenUV = gl_FragCoord.xy / ScreenSize;
    
    float time = GameTime * HeatSpeed;
    
    // get noise distortion from texture
    vec2 noiseUV1 = texCoord0 * 2.0 + vec2(time * 0.1, time * 0.15);
    vec2 noiseUV2 = texCoord0 * 3.0 - vec2(time * 0.08, time * 0.12);
    
    float noise1 = texture(NoiseTexture, noiseUV1).r;
    float noise2 = texture(NoiseTexture, noiseUV2).g;
    
    // Create heat wave distortion for more quality
    vec2 distortion = vec2(
        (noise1 - 0.5) * 0.02,
        (noise2 - 0.5) * 0.02
    );
    
    // Scale by distortion-strength
    distortion *= DistortionStrength * vertexColor.a;
    
    // Sample scene with distortion
    vec2 distortedUV = screenUV + distortion;
    vec4 sceneColor = texture(SamplerSceneColor, distortedUV);
    
    // Fresnel for edge visibility
    float fresnel = FresnelEffect(vViewNormal, normalize(-vViewPos), FresnelPower);
    
    // MIX
    vec3 finalColor = sceneColor.rgb + (ColorHDR.rgb * ColorHDR.a * fresnel);
    
    fragColor = vec4(finalColor, 1.0);
}
