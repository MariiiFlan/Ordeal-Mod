#version 330 core

uniform vec2 ScreenSize;
uniform vec4 ColorHDR;
uniform float Refraction;
uniform float GameTime;

uniform sampler2D IceTexture;
uniform sampler2D SamplerSceneColor;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 screenUV = gl_FragCoord.xy / ScreenSize;
    vec2 iceUV = screenUV * texture(IceTexture, texCoord0).r * (sin(GameTime * 2000) + 1) * vertexColor.a;
    vec2 finalUV = mix(screenUV, iceUV + screenUV, Refraction);

    vec4 screenColor = texture(SamplerSceneColor, finalUV);

    fragColor = vec4((screenColor.rgb + ColorHDR.rgb * ColorHDR.a * vertexColor.a), 1.);
}