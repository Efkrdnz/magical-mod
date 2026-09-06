#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

out vec2 texCoord0;
out vec4 vertexColor;
out float plasmaPulse;

void main() {
    vec2 p = UV0 * 2.0 - 1.0;
    float r = length(p);
    float t = GameTime * 1450.0;
    float flame = sin(t + p.x * 24.0) * sin(t * 0.61 - p.y * 29.0);
    vec3 warped = Position;
    warped.xy += normalize(p + vec2(0.0001)) * flame * 0.045 * smoothstep(0.04, 1.0, r);
    warped.xy += vec2(-p.y, p.x) * sin(t * 0.7 + r * 18.0) * 0.018;

    gl_Position = ProjMat * ModelViewMat * vec4(warped, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    plasmaPulse = 0.5 + 0.5 * sin(t * 0.93);
}
