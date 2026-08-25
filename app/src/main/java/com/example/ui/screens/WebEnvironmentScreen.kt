package com.example.ui.screens

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.entity.BootConfigEntity
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.viewmodel.ConversionProgress

data class WebBookmark(
    val title: String,
    val url: String
)

@Composable
fun WebEnvironmentScreen(
    bootConfig: BootConfigEntity?,
    conversionState: ConversionProgress = ConversionProgress(),
    onDownloadRequested: (url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) -> Unit = { _, _, _, _ -> }
) {
    val dnsText = if (bootConfig?.dnsMode == "GOOGLE_DNS") "Google DNS (8.8.8.8)" else "Custom DNS (${bootConfig?.customDns ?: "8.8.8.8"})"

    var currentUrl by remember { mutableStateOf("https://switchbrew.org/wiki/Main_Page") }
    var inputUrl by remember { mutableStateOf("https://switchbrew.org/wiki/Main_Page") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val bookmarks = remember {
        listOf(
            WebBookmark("SwitchBrew Wiki", "https://switchbrew.org/wiki/Main_Page"),
            WebBookmark("Homebrew Hub", "https://hb-app.store"),
            WebBookmark("devkitPro Docs", "https://devkitpro.org"),
            WebBookmark("Libnx Github", "https://github.com/switchbrew/libnx"),
            WebBookmark("GBAtemp Homebrew", "https://gbatemp.net")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(listOf(SurfaceVariantDark, SurfaceDark))
                )
                .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Web Environment",
                        tint = NeonBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "WEB ENVIRONMENT & BROWSER",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "DNS Active: $dnsText • Downloads -> Virtual Storage (MyFolder)",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonGreen
                        )
                    }
                }
            }
        }

        // Download Progress Card
        AnimatedVisibility(visible = conversionState.isConverting) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = conversionState.statusText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { conversionState.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = NeonGreen,
                        trackColor = Color.DarkGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bookmark Pills
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(bookmarks) { b ->
                AssistChip(
                    onClick = {
                        inputUrl = b.url
                        currentUrl = b.url
                        webViewInstance?.loadUrl(b.url)
                    },
                    label = { Text(b.title, fontSize = 11.sp, color = Color.White) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = NeonBlue,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceDark),
                    border = AssistChipDefaults.assistChipBorder(borderColor = SurfaceBorder, enabled = true)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // URL Address Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputUrl,
                onValueChange = { inputUrl = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("url_input_field"),
                singleLine = true,
                placeholder = { Text("https://...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    val formatted = if (!inputUrl.startsWith("http://") && !inputUrl.startsWith("https://")) {
                        "https://$inputUrl"
                    } else inputUrl
                    currentUrl = formatted
                    webViewInstance?.loadUrl(formatted)
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(NeonBlue)
                    .testTag("go_url_button")
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go", tint = Color.Black)
            }

            Spacer(modifier = Modifier.width(6.dp))

            Button(
                onClick = {
                    val formatted = if (!inputUrl.startsWith("http://") && !inputUrl.startsWith("https://")) {
                        "https://$inputUrl"
                    } else inputUrl
                    onDownloadRequested(formatted, null, null, null)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("download_direct_button")
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download Direct", tint = Color.Black, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // WebView Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true

                        setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                            onDownloadRequested(url, userAgent, contentDisposition, mimetype)
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                val lowerUrl = url.lowercase()
                                if (lowerUrl.endsWith(".nsp") || lowerUrl.endsWith(".xci") ||
                                    lowerUrl.endsWith(".keys") || lowerUrl.endsWith(".zip") ||
                                    lowerUrl.endsWith(".nro") || lowerUrl.endsWith(".nso") ||
                                    lowerUrl.endsWith(".bin")) {
                                    onDownloadRequested(url, null, null, null)
                                    return true
                                }
                                return false
                            }
                        }

                        loadUrl(currentUrl)
                        webViewInstance = this
                    }
                },
                update = { webView ->
                    webViewInstance = webView
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

