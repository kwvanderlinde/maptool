/*
 * General MapTool shader.
 *
 * The shader blends three textures: a source; a destination; and a clip. Currently assumes all
 * three textures are the same size (e.g., the size of the main framebuffer) and so uses the same
 * texture coordinates to index each one.
 */

const int BLEND_MODE_SRC_OVER = 1;
const int BLEND_MODE_STRAIGHT_SRC_OVER = 2;
const int BLEND_MODE_BRIGHTEN = 3;
const int BLEND_MODE_SRC_ONLY = 4;

/** The front texture. Must be named u_texture since GDX's PolygonSpriteBatch hardcodes that name. */
uniform sampler2D u_texture; // == src
/** The clip texture. The blending result is multiplied with the alpha of the clip */
uniform sampler2D u_clip;
/** The back texture. */
uniform sampler2D u_dst;
/** Selects which blend mode to use. Must be one of the `BLEND_MODE_*` constants above. */
uniform int u_blendMode;
/** Additional alpha to apply for layer opacity. */
uniform float u_opacity;

varying vec4 v_color;
varying vec2 v_texCoords;
varying vec2 v_screenUv;

void main()
{
    // src illuminates dst
    vec4 src = texture2D(u_texture, v_texCoords);
    vec4 dst = texture2D(u_dst    , v_texCoords);
    vec4 clip = texture2D(u_clip  , v_screenUv);

    src *= v_color;

    if (u_blendMode == BLEND_MODE_STRAIGHT_SRC_OVER) {
        // This is the only case where colors are not stored in premultiplied alpha.
        src.a *= clip.a;
        src.a *= u_opacity;
    }
    else {
        src *= clip.a;
        src *= u_opacity;
    }

    switch (u_blendMode) {
        default:
        case BLEND_MODE_SRC_OVER:
            gl_FragColor = vec4(src.rgb + dst.rgb * (1. - src.a), src.a + dst.a * (1. - src.a));
            break;
        case BLEND_MODE_STRAIGHT_SRC_OVER:
            gl_FragColor.a = src.a + dst.a * (1. - src.a);
            gl_FragColor.rgb = (src.rgb * src.a + dst.rgb * dst.a * (1. - src.a)) / gl_FragColor.a;
            break;
        case BLEND_MODE_BRIGHTEN:
            gl_FragColor = vec4(dst.rgb + src.rgb * min(dst.rgb, vec3(1) - dst.rgb), dst.a);
            break;
        case BLEND_MODE_SRC_ONLY:
            gl_FragColor = src;
            break;
    }
}
