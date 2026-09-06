#version 150

#moj_import <magical:magic_common.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

out vec2 texCoord0;
out vec4 vertexColor;
flat out ivec4 magicA;
flat out vec2 magicB;

void main() {
    ivec4 a;
    vec2 b;
    decodeMagicVertex(UV2, a, b);
    vec3 pos = Position;
    // BLADE kind wobbles the sheet like the old spatial rift
    if (a.x == 2) {
        vec2 p = UV0 * 2.0 - 1.0;
        float blade = exp(-abs(p.x) * 5.0);
        float t = magicTime(GameTime, 140.0) + b.y * 20.0;
        pos.x += sin(t * 0.6 + p.y * 22.0) * 0.055 * blade;
        pos.z += sin(t * 0.45 + p.y * 12.0) * 0.025 * blade;
    }
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    magicA = a;
    magicB = b;
}
