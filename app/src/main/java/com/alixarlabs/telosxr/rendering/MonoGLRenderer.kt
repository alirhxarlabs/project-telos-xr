package com.alixarlabs.telosxr.rendering

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES renderer for 2D (mono) video display
 *
 * Displays the full side-by-side video frame without splitting
 * Used when stereo mode is OFF - shows raw SBS video
 */
class MonoGLRenderer(
    private val onSurfaceReady: (Surface) -> Unit
) : GLSurfaceView.Renderer {

    private val tag = "MonoGLRenderer"

    // OpenGL texture IDs
    private var externalTextureId = 0  // OES texture for MediaCodec output

    // Shader program
    private var shaderProgram = 0

    // SurfaceTexture for receiving video frames from MediaCodec
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null

    // Vertex buffers
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer

    // Viewport dimensions
    private var viewportWidth = 0
    private var viewportHeight = 0

    // Frame counter for logging
    private var frameCount = 0

    // Simple vertex shader (no multiview)
    private val vertexShaderCode = """
        #version 300 es

        in vec4 aPosition;
        in vec2 aTexCoord;
        out vec2 vTexCoord;

        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    // Simple fragment shader - displays full texture without splitting
    private val fragmentShaderCode = """
        #version 300 es
        #extension GL_OES_EGL_image_external_essl3 : require
        precision mediump float;

        in vec2 vTexCoord;
        out vec4 fragColor;

        uniform samplerExternalOES uTexture;

        void main() {
            // Display full SBS frame without splitting
            fragColor = texture(uTexture, vTexCoord);
        }
    """.trimIndent()

    // Full-screen quad vertices
    private val vertices = floatArrayOf(
        -1.0f, -1.0f,  // Bottom left
         1.0f, -1.0f,  // Bottom right
        -1.0f,  1.0f,  // Top left
         1.0f,  1.0f   // Top right
    )

    // Texture coordinates
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

        val GL_TEXTURE_EXTERNAL_OES = 0x8D65
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, externalTextureId)
        GLES20.glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        // Create SurfaceTexture
        surfaceTexture = SurfaceTexture(externalTextureId).apply {
            setOnFrameAvailableListener { texture ->
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

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
        texCoordBuffer.position(0)

        Log.d(tag, "OpenGL setup complete (2D mode), external texture ID: $externalTextureId")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(tag, "=== 2D VIEWPORT ===")
        Log.i(tag, "  Viewport size: ${width}x${height}")
        Log.i(tag, "  Video input: 1920x1080 SBS (full frame)")
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        frameCount++
        if (frameCount == 1 || frameCount % 60 == 0) {
            Log.d(tag, "onDrawFrame (2D mode): frame #$frameCount")
        }

        // Update texture with latest frame
        surfaceTexture?.updateTexImage()

        // Clear screen
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Use shader program
        GLES20.glUseProgram(shaderProgram)

        // Get attribute/uniform locations
        val positionHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "aTexCoord")
        val textureHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexture")

        // Bind texture
        val GL_TEXTURE_EXTERNAL_OES = 0x8D65
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, externalTextureId)
        GLES20.glUniform1i(textureHandle, 0)

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        // Set vertex data
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        texCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        // Draw full frame
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

        Log.d(tag, "Shader program created successfully (2D mode)")
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
