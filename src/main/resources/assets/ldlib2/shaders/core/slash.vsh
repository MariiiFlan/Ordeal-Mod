#version 330 core

#moj_import <fog.glsl>
#moj_import <photon:particle.glsl>

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    ParticleData data = getParticleData();

    vec4  viewPos4  = ModelViewMat * vec4(data.Position, 1.0);

    vertexDistance  = fog_distance(viewPos4.xyz, FogShape);
    texCoord0 = data.UV;
    vertexColor = data.Color;

    gl_Position     = ProjMat * viewPos4;
}
