#version 330 core

#define PARTICLE_MODEL_INSTANCE

#moj_import <fog.glsl>
#moj_import <photon:particle.glsl>

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;
out vec3 vViewPos;
out vec3 vViewNormal;

void main() {
    ParticleData data = getParticleData();

    vec4 viewPos4 = ModelViewMat * vec4(data.Position, 1.0);
    vViewPos = viewPos4.xyz;
    vViewNormal = normalize(mat3(ModelViewMat) * data.Normal);

    vertexDistance = fog_distance(vViewPos, FogShape);
    texCoord0 = data.UV;
    vertexColor = data.Color * texelFetch(Sampler2, data.LightUV / 16, 0);

    gl_Position = ProjMat * viewPos4;
}
