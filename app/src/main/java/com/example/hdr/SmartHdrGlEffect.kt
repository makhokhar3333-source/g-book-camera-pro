package com.example.hdr

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Real-time GPU OpenGL ES 2.0 Smart HDR effect compatible with both
 * Media3 ExoPlayer (live preview) and Media3 Transformer (fast GPU export).
 */
@UnstableApi
class SmartHdrGlEffect(
    private val intensityProvider: () -> Float,
    private val compareOriginalProvider: () -> Boolean = { false }
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return SmartHdrShaderProgram(
            useHdr = useHdr,
            intensityProvider = intensityProvider,
            compareOriginalProvider = compareOriginalProvider
        )
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean {
        // Keep active during live preview so dynamic slider/compare updates work smoothly
        return false
    }
}

@UnstableApi
private class SmartHdrShaderProgram(
    useHdr: Boolean,
    private val intensityProvider: () -> Float,
    private val compareOriginalProvider: () -> Boolean
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram: GlProgram? = null
    private var inputWidth = 1
    private var inputHeight = 1

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform float uIntensity;
            uniform float uCompareOriginal;
            uniform vec2 uTexelSize;
            varying vec2 vTexSamplingCoord;

            float calcLuma(vec3 c) {
                return dot(c, vec3(0.2126, 0.7152, 0.0722));
            }

            vec3 filmicTone(vec3 v, float s) {
                vec3 clamped = clamp(v, 0.0, 1.25);
                vec3 comp = clamped / (1.0 + 0.14 * s * clamped);
                vec3 norm = clamp(comp * (1.0 + 0.14 * s), 0.0, 1.0);
                vec3 sCurve = norm * norm * (3.0 - 2.0 * norm);
                return clamp(mix(norm, sCurve, 0.16 * s), 0.0, 1.0);
            }

            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                if (uCompareOriginal > 0.5 || uIntensity <= 0.005) {
                    gl_FragColor = src;
                    return;
                }

                float s = clamp(uIntensity, 0.0, 1.0);
                vec3 rgb = src.rgb;
                float l = calcLuma(rgb);

                // 48MP-style crisp luminance clarity (5-tap GPU unsharp detail)
                float lUp = calcLuma(texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, uTexelSize.y)).rgb);
                float lDown = calcLuma(texture2D(uTexSampler, vTexSamplingCoord - vec2(0.0, uTexelSize.y)).rgb);
                float lLeft = calcLuma(texture2D(uTexSampler, vTexSamplingCoord - vec2(uTexelSize.x, 0.0)).rgb);
                float lRight = calcLuma(texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexelSize.x, 0.0)).rgb);
                float localAvg = (l + lUp + lDown + lLeft + lRight) * 0.2;
                float highPass = clamp(l - localAvg, -0.16, 0.16) * (0.38 * s);

                // f/1.48 shadow lift with true-black anchor
                float blackAnchor = smoothstep(0.012, 0.085, l);
                float shadowWeight = exp(-l * 4.3) * blackAnchor;
                float shadowScale = 1.0 + (0.56 * s) * shadowWeight;

                // f/4.0 highlight & sky recovery
                float highWeight = smoothstep(0.56, 0.98, l);
                float highScale = 1.0 - (0.27 * s) * highWeight * (1.0 - 0.35 * (1.0 - l));

                vec3 toned = rgb * (shadowScale * highScale) + vec3(highPass);
                toned = filmicTone(toned, s);

                // Natural golden studio skin warmth
                float isSkin = step(toned.g, toned.r) * step(toned.b * 0.84, toned.g);
                float rgDelta = clamp((toned.r - toned.g) / 0.35, 0.0, 1.0);
                float midMask = smoothstep(0.14, 0.45, l) * (1.0 - smoothstep(0.78, 0.96, l));
                float skinWeight = isSkin * rgDelta * midMask;
                float warmth = s * (0.018 + 0.042 * skinWeight);
                toned.r = clamp(toned.r + warmth, 0.0, 1.0);
                toned.g = clamp(toned.g + warmth * 0.56, 0.0, 1.0);
                toned.b = clamp(toned.b - warmth * 0.22, 0.0, 1.0);

                // Perceptual vibrance
                float newL = calcLuma(toned);
                float maxCh = max(toned.r, max(toned.g, toned.b));
                float minCh = min(toned.r, min(toned.g, toned.b));
                float sat = clamp(maxCh - minCh, 0.0, 1.0);
                float vibBoost = 1.0 + (0.24 * s) * (1.0 - sat) * (1.0 - 0.55 * skinWeight);
                vec3 finalRgb = clamp(vec3(newL) + (toned - vec3(newL)) * vibBoost, 0.0, 1.0);

                gl_FragColor = vec4(finalRgb, src.a);
            }
        """
    }

    init {
        try {
            val program = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            program.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            glProgram = program
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        this.inputWidth = inputWidth.coerceAtLeast(1)
        this.inputHeight = inputHeight.coerceAtLeast(1)
        return Size(this.inputWidth, this.inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatUniform("uIntensity", (intensityProvider() / 100f).coerceIn(0f, 1f))
            program.setFloatUniform(
                "uCompareOriginal",
                if (compareOriginalProvider()) 1f else 0f
            )
            program.setFloatsUniform(
                "uTexelSize",
                floatArrayOf(1f / inputWidth.toFloat(), 1f / inputHeight.toFloat())
            )
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram?.delete()
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }
}
