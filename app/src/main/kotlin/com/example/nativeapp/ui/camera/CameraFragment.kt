package com.example.nativeapp.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.nativeapp.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment(R.layout.fragment_camera) {

    private lateinit var previewView: PreviewView
    private lateinit var resultText: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var labeler: ImageLabeler
    private var lastAnalyzedAt = 0L

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else resultText.setText(R.string.camera_permission_denied)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previewView = view.findViewById(R.id.preview_view)
        resultText = view.findViewById(R.id.text_result)
        cameraExecutor = Executors.newSingleThreadExecutor()
        labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build()
        )

        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, ::analyze) }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
            } catch (e: Exception) {
                resultText.setText(R.string.camera_unavailable)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun analyze(proxy: androidx.camera.core.ImageProxy) {
        val mediaImage = proxy.image
        val now = SystemClock.elapsedRealtime()
        if (mediaImage == null || now - lastAnalyzedAt < ANALYZE_INTERVAL_MS) {
            proxy.close()
            return
        }
        lastAnalyzedAt = now

        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        labeler.process(image)
            .addOnSuccessListener { labels ->
                val text = if (labels.isEmpty()) {
                    getString(R.string.camera_no_objects)
                } else {
                    labels.take(5).joinToString("\n") {
                        "${it.text} (${(it.confidence * 100).toInt()}%)"
                    }
                }
                view?.findViewById<TextView>(R.id.text_result)?.text = text
            }
            .addOnCompleteListener { proxy.close() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        labeler.close()
    }

    private companion object {
        const val ANALYZE_INTERVAL_MS = 500L
    }
}
