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
out vec3 shellNormal;
out vec3 viewDir;
flat out ivec4 magicA;
flat out vec2 magicB;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    decodeMagicVertex(UV2, magicA, magicB);

    // Position arrives camera-relative - the entity renderer's pose is already translated to the
    // eye - so the vector from this vertex back to the viewer is simply its own negation. There is
    // no camera uniform in a core shader and none is needed.
    viewDir = -Position;

    // The one thing the vertex format cannot carry is a normal: it is position, colour, UV0 and
    // two packed integers, and every bit of the integers is spoken for. So the shell rebuilds its
    // own normal out of the coordinates the mesh wrote into UV0 - bearing across, height up. The
    // dome is never rotated, only moved and scaled evenly, so a local normal is a world normal.
    float phi = UV0.x * MAGIC_TWO_PI;
    float ny = UV0.y * 2.0 - 1.0;
    float s = sqrt(max(0.0, 1.0 - ny * ny));
    shellNormal = vec3(sin(phi) * s, ny, -cos(phi) * s);
}
