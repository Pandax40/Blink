package com.pandina.blink.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkScreen(viewModel: BlinkViewModel = viewModel(), onBackClick: () -> Unit) {
    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("") },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            })
    }, bottomBar = {
        Button(
            onClick = { viewModel.blink() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 8.dp)
                .padding(WindowInsets.navigationBars.asPaddingValues())
        ) {
            Text(
                text = "Blink Again",
                fontStyle = MaterialTheme.typography.bodyMedium.fontStyle,
                fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
            )
        }
    }) { innerPadding ->
        Column(
            modifier = Modifier
                .consumeWindowInsets(innerPadding)
                .padding(innerPadding)
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp))
            ) {
                val videoTrackState = viewModel.remoteVideoCall.collectAsStateWithLifecycle()
                val eglBaseContext = viewModel.rootEglBase.eglBaseContext
                videoTrackState.value?.let { videoTrack ->
                    CameraView(videoTrack, eglBaseContext)
                } ?: run {
                    Text("Waiting Users", modifier = Modifier.align(Alignment.Center))
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp))
            ) {
                val videoTrackState = viewModel.localVideoTrack.collectAsState(null)
                val eglBaseContext = viewModel.rootEglBase.eglBaseContext
                videoTrackState.value?.let { videoTrack ->
                    CameraView(videoTrack, eglBaseContext)
                } ?: run {
                    Text("No video track available", modifier = Modifier.align(Alignment.Center))
                }

            }
        }
    }
}

@Composable
fun CameraView(videoTrack: VideoTrack, eglBaseContext: EglBase.Context) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Crear el SurfaceViewRenderer una sola vez
    val surfaceViewRenderer = remember(videoTrack) {
        org.webrtc.SurfaceViewRenderer(context).apply {
            init(eglBaseContext, object : RendererCommon.RendererEvents {
                override fun onFirstFrameRendered() {
                    // Lógica para el primer frame renderizado
                }

                override fun onFrameResolutionChanged(
                    videoWidth: Int, videoHeight: Int, rotation: Int
                ) {
                    // Lógica para el cambio de resolución del frame
                }
            })
            // Opcional: Ajustar la vista espejo si es la cámara frontal
            setMirror(true)
        }
    }

    // Efecto para agregar y remover el sink del videoTrack
    DisposableEffect(videoTrack) {
        videoTrack.addSink(surfaceViewRenderer)
        onDispose {
            videoTrack.removeSink(surfaceViewRenderer)
            surfaceViewRenderer.release()
        }
    }

    // Incorporar el SurfaceViewRenderer dentro de Compose
    AndroidView(
        factory = { surfaceViewRenderer },
        modifier = Modifier.fillMaxSize()
    )
}


@Preview
@Composable
private fun BlinkScreenPreview() {
    BlinkScreen { }
}