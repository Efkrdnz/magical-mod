#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

out vec2 texCoord0;
out vec4 vertexColor;
out float dragPulse;

void main() {
    vec2 p = UV0 * 2.0 - 1.0;
    float r2 = dot(p, p);
    float t = GameTime * 930.0;
    vec3 warped = Position;
    warped.xy += vec2(-p.y, p.x) * (0.12 / (r2 + 0.18));
    warped.xy += p * sin(t + r2 * 16.0) * 0.025;

    gl_Position = ProjMat * ModelViewMat * vec4(warped, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    dragPulse = 0.5 + 0.5 * sin(t * 1.12);
}
