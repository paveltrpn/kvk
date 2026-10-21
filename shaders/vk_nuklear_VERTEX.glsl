#version 450

layout(location = 0) in vec2 pos;
layout(location = 1) in vec2 uv;
layout(location = 2) in vec4 col;

layout(push_constant) uniform PushConstants {
    vec2 scale;
    vec2 translate;
} pc;

layout(location = 0) out vec2 fragUv;
layout(location = 1) out vec4 fragColor;

void main()
{
    gl_Position = vec4(pos * pc.scale + pc.translate, 0.0, 1.0);
    fragUv = uv;
    fragColor = col;
}
