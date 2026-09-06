#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

out vec2 texCoord0;
out vec4 vertexColor;
out float phase;

void main() {
    vec2 p = UV0 * 2.0 - 1.0;
    float r = length(p);
    float t = GameTime * 680.0;
    vec3 warped = Position;
    warped.xy += p * sin(t + r * 18.0) * 0.024;
    warped.xy += vec2(-p.y, p.x) * sin(t * 1.4 + r * 9.0) * 0.018;
    warped.z += sin(t * 0.9 + p.x * 12.0 - p.y * 10.0) * 0.035 * smoothstep(0.18, 1.0, r);

    gl_Position = ProjMat * ModelViewMat * vec4(warped, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    phase = 0.5 + 0.5 * sin(t * 1.23);
}
