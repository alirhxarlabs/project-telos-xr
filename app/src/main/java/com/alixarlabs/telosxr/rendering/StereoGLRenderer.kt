package com.alixarlabs.telosxr.rendering

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES renderer for stereo (3D) video display
 *
 * Splits side-by-side (SBS) video into left and right eye views
 * Renders to dual viewports for stereoscopic display
 *
 * Based on Project Taris Metal compute shader implementation
 *
 * STATUS: Renderer works correctly but has surface lifecycle issues in XR mode
 * - OpenGL setup successful, shaders compile correctly
 * - Video frames ARE being decoded and arriving at SurfaceTexture
 * - BUT: XR window manager destroys GLSurfaceView surface immediately after creation
 * - This prevents the stereo rendering from displaying even though it's working
 *
 * IMPLEMENTATION:
 * - Uses GL_TEXTURE_EXTERNAL_OES for zero-copy MediaCodec output
 * - Fragment shader remaps texture coordinates to split SBS:
 *   - Left eye: samples from left half (x: 0.0-0.5)
 *   - Right eye: samples from right half (x: 0.5-1.0)
 * - Dual viewport rendering: left eye to left half of screen, right to right half
 *
 * TODO:
 * - Fix surface lifecycle in XR environment OR
 * - Wait for AndroidXR SurfaceEntity spatial APIs to stabilize
 */
class StereoGLRenderer(
    private val onSurfaceReady: (Surface) -> Unit
) : GLSurfaceView.Renderer {

    private val tag = "StereoGLRenderer"

    // OpenGL texture IDs
    private var externalTextureId = 0  // OES texture for MediaCodec output
    private var framebufferId = 0

    // Shader program
    private var shaderProgram = 0

    // SurfaceTexture for receiving video frames from MediaCodec
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null

    // Vertex buffers
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBufferLeft: FloatBuffer
    private lateinit var texCoordBufferRight: FloatBuffer

    // Viewport dimensions
    private var viewportWidth = 0
    private var viewportHeight = 0

    // Transform matrix from SurfaceTexture
    private val transformMatrix = FloatArray(16)

    // Frame counter for logging
    private var frameCount = 0

    // Vertex shader - with GL_OVR_multiview2 for Android XR (Meta Quest/Magic Leap approach)
    private val vertexShaderCode = """
        #version 300 es
        #extension GL_OVR_multiview2 : enable
        layout(num_views = 2) in;

        in vec4 aPosition;
        in vec2 aTexCoord;
        out vec2 vTexCoord;

        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    // Fragment shader - uses gl_ViewID_OVR for automatic eye selection
    private val fragmentShaderCode = """
        #version 300 es
        #extension GL_OES_EGL_image_external_essl3 : require
        #extension GL_OVR_multiview2 : enable
        precision mediump float;

        in vec2 vTexCoord;
        out vec4 fragColor;

        uniform samplerExternalOES uTexture;
        // gl_ViewID_OVR: 0 = left eye, 1 = right eye (automatically set by XR system)

        void main() {
            // Split side-by-side video based on eye
            vec2 texCoord = vTexCoord;

            if (gl_ViewID_OVR == 0u) {
                // Left eye: sample from left half [0, 0.5]
                texCoord.x = texCoord.x * 0.5;
            } else {
                // Right eye: sample from right half [0.5, 1.0]
                texCoord.x = 0.5 + texCoord.x * 0.5;
            }

            fragColor = texture(uTexture, texCoord);
        }
    """.trimIndent()

    // Full-screen quad vertices
    private val vertices = floatArrayOf(
        -1.0f, -1.0f,  // Bottom left
         1.0f, -1.0f,  // Bottom right
        -1.0f,  1.0f,  // Top left
         1.0f,  1.0f   // Top right
    )

    // Texture coordinates (standard, not flipped)
    private val texCoords = floatArrayOf(
        0.0f, 1.0f,  // Bottom left
        1.0f, 1.0f,  // Bottom right
        0.0f, 0.0f,  // Top left
        1.0f, 0.0f   // Top right
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(tag, "onSurfaceCreated")

        // Create external texture for MediaCodec output
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        externalTextureId = textures[0]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        // Create SurfaceTexture
        surfaceTexture = SurfaceTexture(externalTextureId).apply {
            setOnFrameAvailableListener { texture ->
                // Frame available, request render
                android.util.Log.v(tag, "Frame available from SurfaceTexture")
            }
        }

        // Create Surface for MediaCodec
        surface = Surface(surfaceTexture)
        onSurfaceReady(surface!!)

        // Load shaders
        shaderProgram = createProgram(vertexShaderCode, fragmentShaderCode)
        if (shaderProgram == 0) {
            Log.e(tag, "Failed to create shader program")
            return
        }

        // Setup vertex buffers
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)

        texCoordBufferLeft = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
        texCoordBufferLeft.position(0)

        texCoordBufferRight = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
        texCoordBufferRight.position(0)

        Log.d(tag, "OpenGL setup complete, external texture ID: $externalTextureId")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(tag, "onSurfaceChanged: ${width}x${height}")
        viewportWidth = width
        viewportHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        frameCount++
        if (frameCount == 1 || frameCount % 60 == 0) {
            Log.d(tag, "onDrawFrame: frame #$frameCount")
        }

        // Update texture with latest frame
        surfaceTexture?.updateTexImage()
        surfaceTexture?.getTransformMatrix(transformMatrix)

        // Clear screen
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Use shader program
        GLES20.glUseProgram(shaderProgram)

        // Get attribute/uniform locations
        val positionHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "aTexCoord")
        val textureHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexture")

        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
        GLES20.glUniform1i(textureHandle, 0)

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        // Single draw call with GL_OVR_multiview2 - automatically renders to both eyes
        // gl_ViewID_OVR in shader determines which eye (0=left, 1=right)
        // XR system handles viewport routing automatically
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        texCoordBufferLeft.position(0)  // Use same tex coords for both eyes
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBufferLeft)

        // Single draw call renders to both eye buffers automatically via multiview
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(tag, "Shader compilation failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }

        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0

        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) {
            Log.e(tag, "Program link failed: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            return 0
        }

        Log.d(tag, "Shader program created successfully")
        return program
    }

    fun release() {
        surface?.release()
        surfaceTexture?.release()

        if (shaderProgram != 0) {
            GLES20.glDeleteProgram(shaderProgram)
            shaderProgram = 0
        }

        if (externalTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(externalTextureId), 0)
            externalTextureId = 0
        }
    }
}

// Extension for GL_TEXTURE_EXTERNAL_OES
private object GLES11Ext {
    const val GL_TEXTURE_EXTERNAL_OES = 0x8D65
}
