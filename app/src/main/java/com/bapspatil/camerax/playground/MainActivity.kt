/*
 * Copyright 2019 Bapusaheb Patil
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bapspatil.camerax.playground

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.view.Surface
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), LifecycleOwner {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (areAllPermissionsGranted()) {
            texture_view.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                this, PERMISSIONS, CAMERA_REQUEST_PERMISSION_CODE
            )
        }

        texture_view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }
    }

    private fun startCamera() {
        // Setup the image preview
        val preview = setupPreview()
        // Setup the image capture
        val imageCapture = setupImageCapture()
        // Setup the image analysis
        val analyzerUseCase = setupImageAnalysis()
        // Bind camera to the lifecycle of the Activity
        CameraX.bindToLifecycle(this, preview, imageCapture, analyzerUseCase)
    }

    private fun setupPreview(): Preview {
        val metrics = DisplayMetrics().also { texture_view.display.getRealMetrics(it) }
        val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)

        val previewConfig = PreviewConfig.Builder().apply {
            setLensFacing(CameraX.LensFacing.BACK)
            setTargetAspectRatioCustom(screenAspectRatio)
        }.build()

        val preview = Preview(previewConfig)

        preview.setOnPreviewOutputUpdateListener {
            val parent = texture_view.parent as ViewGroup
            parent.removeView(texture_view)
            parent.addView(texture_view, 0)
            texture_view.surfaceTexture = it.surfaceTexture
            updateTransform()
        }
        return preview
    }

    private fun setupImageCapture(): ImageCapture {
        val metrics = DisplayMetrics().also { texture_view.display.getRealMetrics(it) }
        val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)

        val imageCaptureConfig = ImageCaptureConfig.Builder()
            .apply {
                setLensFacing(CameraX.LensFacing.BACK)
                setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
                setTargetAspectRatioCustom(screenAspectRatio)
            }.build()

        val imageCapture = ImageCapture(imageCaptureConfig)
        btn_capture.setOnClickListener {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "${System.currentTimeMillis()}_CameraXPlayground.jpg"
            )
            imageCapture.takePicture(file,
                executor,
                object : ImageCapture.OnImageSavedListener {
                    override fun onError(
                        imageCaptureError: ImageCapture.ImageCaptureError,
                        message: String,
                        cause: Throwable?
                    ) {
                        val msg = "Photo capture failed: $message"
                        runOnUiThread {
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        }
                        Log.e("CameraXApp", msg)
                    }

                    override fun onImageSaved(file: File) {
                        val msg = "Photo capture succeeded: ${file.absolutePath}"
                        runOnUiThread {
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        }
                        Log.d("CameraXApp", msg)
                    }
                })
        }
        return imageCapture
    }

    private fun setupImageAnalysis(): ImageAnalysis {
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
        }.build()

        return ImageAnalysis(analyzerConfig).apply {
            setAnalyzer(executor, RedColorAnalyzer())
        }
    }

    private fun updateTransform() {
        val matrix = Matrix()

        val centerX = texture_view.width / 2f
        val centerY = texture_view.height / 2f

        val rotationDegrees = when (texture_view.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        texture_view.setTransform(matrix)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == CAMERA_REQUEST_PERMISSION_CODE) {
            if (areAllPermissionsGranted()) {
                texture_view.post { startCamera() }
            } else {
                Toast.makeText(this, "Permissions not granted!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun areAllPermissionsGranted() = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val CAMERA_REQUEST_PERMISSION_CODE = 13
        private val PERMISSIONS =
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}
