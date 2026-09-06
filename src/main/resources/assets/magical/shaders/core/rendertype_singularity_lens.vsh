#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

out vec2 texCoord0;
out vec4 vertexColor;
out float gravityPulse;

void main() {
    vec2 centered = UV0 * 2.0 - 1.0;
    float r2 = dot(centered, centered);
    float swirl = 0.10 / (r2 + 0.20);
    float pulse = sin(GameTime * 980.0 + r2 * 9.0) * 0.035;
    vec3 warped = Position;
    warped.xy += vec2(-centered.y, centered.x) * (swirl + pulse);
    warped.xy += centered * (0.045 / (r2 + 0.16));

    gl_Position = ProjMat * ModelViewMat * vec4(warped, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    gravityPulse = 0.5 + 0.5 * sin(GameTime * 1200.0);
}
