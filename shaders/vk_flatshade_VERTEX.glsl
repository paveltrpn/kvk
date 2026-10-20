#version 450

layout(location = 0) in vec3 pos;
layout(location = 1) in vec3 nrm;

layout(push_constant) uniform PushConstants {
    mat4 view_matrix;
    mat4 model_matrix;
    vec4 color;
} pc;

layout(location = 0) out vec3 newColor;
layout(location = 1) out vec3 vLighting;

void main()
{
    vec3 lightpos = vec3(1.0, 1.0, 0.0);
    vec3 lightcolor = vec3(1.0, 1.0, 1.0);
    vec3 ambientLight = vec3(0.3, 0.3, 0.3);

    vec3 directionalVector = normalize(lightpos);

    mat4 itModel = transpose(inverse(pc.model_matrix));
    vec4 updatedNrm = itModel * vec4(nrm, 0.0);

    float directional = max(dot(normalize(updatedNrm).xyz, directionalVector), 0.0);

    vLighting = ambientLight + (lightcolor * directional);

    gl_Position = pc.view_matrix * pc.model_matrix * vec4(pos, 1.0);

    newColor = pc.color.rgb;
}
