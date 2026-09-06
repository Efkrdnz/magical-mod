#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

out vec2 texCoord0;
out vec4 vertexColor;
out float wavePulse;

void main() {
    vec2 p = UV0 * 2.0 - 1.0;
    float r = length(p);
    float t = GameTime * 1050.0;
    vec3 warped = Position;
    warped.xy += normalize(p + vec2(0.0001)) * sin(r * 18.0 - t * 2.3) * 0.055 * smoothstep(0.08, 1.0, r);
    warped.z += sin(t + r * 22.0) * 0.035;

    gl_Position = ProjMat * ModelViewMat * vec4(warped, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    wavePulse = 0.5 + 0.5 * sin(t);
}
