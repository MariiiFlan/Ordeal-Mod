#version 150

#moj_import <fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float power;
uniform vec4 HDRColor;

uniform float Dist;
uniform vec2 ScreenSize;
uniform mat4 U_InverseProjectionMatrix;
uniform sampler2D SamplerSceneDepth;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;
in vec3 ViewPos;
in vec3 ViewNormal;

out vec4 fragColor;

// ------------------------------------------------------------
// Fresnel effect (Schlick-like) utility
//  ‣  Normal   : surface normal   (any space, MUST be normalized later)
//  ‣  ViewDir  : direction TO camera (same space as Normal)
//  ‣  power    : controls edge fall-off
//  ←  return   : Fresnel term (0‥1)
// ------------------------------------------------------------
float FresnelEffect(vec3 Normal, vec3 ViewDir, float power)
{
    float cosTheta = clamp(dot(normalize(Normal), normalize(ViewDir)), 0.0, 1.0);
    return pow(1.0 - cosTheta, power);
}

void main() {
    vec3 ViewDir = -normalize(ViewPos);
    float fresnelValue = FresnelEffect(ViewNormal, ViewDir, power);
    vec4 color = linear_fog(vertexColor * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
    color.xyz *= HDRColor.rgb * HDRColor.a;

    vec2 screenUV = gl_FragCoord.xy / ScreenSize;
    float depth = texture(SamplerSceneDepth, screenUV).r;
    vec3 ndc;
    ndc.xy = screenUV.xy * 2.0 - 1.0;
    ndc.z = depth * 2.0 - 1.0;
    vec4 clipSpacePos = vec4(ndc, 1.0);
    vec4 viewSpacePos = U_InverseProjectionMatrix * clipSpacePos;
    viewSpacePos /= viewSpacePos.w;

    float sceneViewDepth = length(viewSpacePos.xyz);
    float viewDepth = length(ViewPos);
    float subDist = sceneViewDepth - viewDepth - Dist;
    float a = clamp(fresnelValue + smoothstep(0, 1, 1 - subDist), 0, 1);

    fragColor = vec4(color.xyz, a);
}
