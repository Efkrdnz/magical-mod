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
    // Swirl the quad itself so the lens bends space (the singularity_lens idiom), scaled by paramB.
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    vec2 p = UV0 * 2.0 - 1.0;
    float r2 = dot(p, p);
    float strength = float(a.z) / 8.0;
    float t = magicTime(GameTime, 100.0) + b.y * 20.0;
    float swirl = 0.10 / (r2 + 0.20) * strength;
    vec2 tang = vec2(-p.y, p.x) * swirl * (0.5 + 0.5 * sin(t * 0.3));
    view.xy += tang * 0.15;
    gl_Position = ProjMat * view;
    texCoord0 = UV0;
    vertexColor = Color;
    magicA = a;
    magicB = b;
}
