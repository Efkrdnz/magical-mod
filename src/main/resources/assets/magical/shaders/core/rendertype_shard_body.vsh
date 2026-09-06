#version 150

#moj_import <magical:magic_common.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out vec4 vertexColor;
out vec3 viewPos;
flat out ivec4 magicA;
flat out vec2 magicB;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    viewPos = view.xyz;
    texCoord0 = UV0;
    vertexColor = Color;
    decodeMagicVertex(UV2, magicA, magicB);
}
