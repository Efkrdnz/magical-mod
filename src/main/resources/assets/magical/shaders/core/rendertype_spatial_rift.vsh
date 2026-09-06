#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

out vec2 texCoord0;
out vec4 vertexColor;
out float riftPulse;

void main() {
    vec2 p = UV0 * 2.0 - 1.0;
    float blade = exp(-abs(p.x) * 5.0);
    float wave = sin(GameTime * 850.0 + p.y * 22.0) * 0.055 * blade;
    vec3 warped = Position;
    warped.x += wave;
    warped.z += sin(GameTime * 640.0 + p.y * 12.0) * 0.025 * blade;

    gl_Position = ProjMat * ModelViewMat * vec4(warped, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    riftPulse = 0.5 + 0.5 * sin(GameTime * 1100.0);
}
