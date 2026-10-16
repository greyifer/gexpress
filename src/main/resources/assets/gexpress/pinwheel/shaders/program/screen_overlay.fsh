uniform sampler2D DiffuseSampler;
uniform int Mode;
uniform float Strength;
uniform float Time;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    if (Mode == 2) {
        
        vec2 offset = vec2(sin(uv.y * 22.0 + Time * 0.16), cos(uv.x * 20.0 + Time * 0.13));
        uv = clamp(uv + offset * 0.012, vec2(0.001), vec2(0.999));
    }
    vec4 source = texture(DiffuseSampler, uv);
    vec3 color = source.rgb;
    float strength = smoothstep(0.0, 1.0, clamp(Strength, 0.0, 1.0));
    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    vec2 centered = texCoord - vec2(0.5);
    float vignette = smoothstep(0.28, 0.76, length(centered));

    if (Mode == 0) {
        vec3 monochrome = vec3(luminance) * vec3(0.94, 0.96, 1.0);
        monochrome *= 1.0 - vignette * 0.12;
        color = mix(color, monochrome, strength);
    } else if (Mode == 1) {
        vec3 rose = color * vec3(1.20, 0.72, 0.96);
        rose += vec3(0.15, 0.008, 0.075) * (0.42 + luminance);
        rose = mix(rose, vec3(luminance * 1.24, luminance * 0.58, luminance * 0.92), 0.30);
        rose *= 1.0 - vignette * 0.06;
        color = mix(color, rose, strength);
    }

    fragColor = vec4(max(color, vec3(0.0)), source.a);
}
