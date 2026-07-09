package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ChannelViewModel

@Composable
fun AppUpdateDialog(
    viewModel: ChannelViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsStateWithLifecycle()
    val updateErrorMessage by viewModel.updateErrorMessage.collectAsStateWithLifecycle()
    val updateWarningMessage by viewModel.updateWarningMessage.collectAsStateWithLifecycle()
    val downloadedFile by viewModel.downloadedFile.collectAsStateWithLifecycle()

    if (!showUpdateDialog || updateInfo == null) return

    val info = updateInfo!!
    val currentVersionName = com.example.BuildConfig.VERSION_NAME

    AlertDialog(
        onDismissRequest = {
            if (updateDownloadProgress == null) {
                viewModel.dismissUpdateDialog()
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFF2B2930),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Update Available!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Current: v$currentVersionName",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "New: v${info.versionName}",
                        fontSize = 12.sp,
                        color = Color(0xFFD0BCFF),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (info.releaseNotes.isNotBlank()) {
                    Text(
                        text = "What's New:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = info.releaseNotes,
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                updateWarningMessage?.let { warningMsg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFB300).copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Verification Warning:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300)
                            )
                            Text(
                                text = warningMsg,
                                fontSize = 11.sp,
                                color = Color.LightGray,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                updateErrorMessage?.let { errorMsg ->
                    Text(
                        text = errorMsg,
                        fontSize = 11.sp,
                        color = Color(0xFFE53935),
                        fontWeight = FontWeight.Medium,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                updateDownloadProgress?.let { progress ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Downloading...",
                                fontSize = 11.sp,
                                color = Color.LightGray,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = Color(0xFFD0BCFF),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            color = Color(0xFFD0BCFF),
                            trackColor = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (downloadedFile != null) {
                        viewModel.installDownloadedApk(context.applicationContext)
                    } else {
                        viewModel.startAppUpdateDownload(context.applicationContext)
                    }
                },
                enabled = updateDownloadProgress == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (updateWarningMessage != null) Color(0xFFFFB300) else Color(0xFFD0BCFF),
                    contentColor = if (updateWarningMessage != null) Color.Black else Color(0xFF381E72)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = when {
                        updateDownloadProgress != null -> "Downloading"
                        updateWarningMessage != null -> "Install Anyway"
                        downloadedFile != null -> "Install Now"
                        else -> "Update Now"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            if (updateDownloadProgress == null) {
                TextButton(
                    onClick = { viewModel.dismissUpdateDialog() }
                ) {
                    Text(
                        text = "Later",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            }
        }
    )
}
