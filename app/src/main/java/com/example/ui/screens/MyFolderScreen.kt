package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.foundation.layout.PaddingValues
import com.example.ui.components.SwitchFilePickerDialog
import java.io.File
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.MyFolderFileEntity
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRed
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.viewmodel.ConversionProgress

@Composable
fun MyFolderScreen(
    files: List<MyFolderFileEntity>,
    conversionState: ConversionProgress,
    onRefresh: () -> Unit,
    onConvertToSup: (MyFolderFileEntity) -> Unit,
    onConvertToCartridge: (MyFolderFileEntity) -> Unit,
    onCreateSampleFile: (String, String, Int) -> Unit,
    onImportLocalFiles: (List<File>) -> Unit = {},
    onDirectLaunchFile: (File) -> Unit = {},
    onScanStorage: (() -> Unit)? = null
) {
    var selectedFileForAction by remember { mutableStateOf<MyFolderFileEntity?>(null) }
    var showAddSampleDialog by remember { mutableStateOf(false) }
    var showFilePicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "My Folder",
                        tint = NeonGreen,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "MY FOLDER",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "SWTC NOOS External File Management Layer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onScanStorage != null) {
                        Button(
                            onClick = onScanStorage,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp).testTag("my_folder_scan_storage_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan ROMs", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Button(
                        onClick = { showFilePicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp).testTag("my_folder_browse_storage_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Browse", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("refresh_folder_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonBlue)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Conversion Progress Banner if active
            AnimatedVisibility(visible = conversionState.isConverting) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(NeonRed, NeonBlue, NeonGreen))
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "PROCESSING FILE: ${conversionState.targetFileName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonYellow,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = conversionState.statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { conversionState.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = NeonGreen,
                            trackColor = SurfaceBorder,
                        )
                    }
                }
            }

            // File List or Empty State
            if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "My Folder is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add legal Nintendo Switch games (.nsp, .xci), homebrew (.nro), or .sup files into My Folder to manage and convert them into Virtual Cartridges.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showFilePicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Browse Storage (.nsp / .nro)", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showAddSampleDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sample File", color = Color.Black)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(files, key = { it.id }) { file ->
                        FileItemCard(
                            file = file,
                            onFileClick = { selectedFileForAction = file }
                        )
                    }
                }
            }
        }

        // FAB to add sample files or import
        FloatingActionButton(
            onClick = { showFilePicker = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_file_fab"),
            containerColor = NeonRed,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = "Browse Files")
        }

        // File Action Dialog (OPEN, CONVERT TO .SUP, CONVERT TO CARTRIDGE)
        selectedFileForAction?.let { file ->
            FileActionDialog(
                file = file,
                onDismiss = { selectedFileForAction = null },
                onConvertToSup = {
                    selectedFileForAction = null
                    onConvertToSup(file)
                },
                onConvertToCartridge = {
                    selectedFileForAction = null
                    onConvertToCartridge(file)
                }
            )
        }

        // Add Sample File Dialog
        if (showAddSampleDialog) {
            AddSampleFileDialog(
                onDismiss = { showAddSampleDialog = false },
                onCreate = { name, format, sizeMb ->
                    showAddSampleDialog = false
                    onCreateSampleFile(name, format, sizeMb)
                }
            )
        }

        // Switch File Picker Modal
        if (showFilePicker) {
            SwitchFilePickerDialog(
                onDismiss = { showFilePicker = false },
                onImportFilesToMyFolder = { files ->
                    showFilePicker = false
                    onImportLocalFiles(files)
                },
                onDirectLaunch = { file ->
                    showFilePicker = false
                    onDirectLaunchFile(file)
                }
            )
        }
    }
}

@Composable
private fun FileItemCard(
    file: MyFolderFileEntity,
    onFileClick: () -> Unit
) {
    val (icon, iconColor) = when (file.fileType) {
        "GAME" -> Icons.Default.Games to NeonRed
        "HOMEBREW" -> Icons.Default.Code to NeonBlue
        "SUP_CONTAINER" -> Icons.Default.Archive to NeonYellow
        "BIOS_KEY" -> Icons.Default.Key to NeonGreen
        "FIRMWARE" -> Icons.Default.Build to NeonBlue
        else -> Icons.Default.VideogameAsset to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onFileClick() }
            .testTag("file_item_${file.id}"),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceVariantDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = file.fileType,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = iconColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = file.extension.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = iconColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatFileSize(file.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onFileClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FileActionDialog(
    file: MyFolderFileEntity,
    onDismiss: () -> Unit,
    onConvertToSup: () -> Unit,
    onConvertToCartridge: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Column {
                Text(
                    text = "FILE ACTION",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonBlue,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceVariantDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Location: ${file.path}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Size: ${formatFileSize(file.sizeBytes)} • Type: ${file.fileType}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action 1: OPEN
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("action_open_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("OPEN FILE", color = Color.White)
                }

                // Action 2: CONVERT TO .SUP
                Button(
                    onClick = onConvertToSup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("action_convert_sup_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonYellow, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CONVERT TO .SUP", fontWeight = FontWeight.Bold)
                }

                // Action 3: CONVERT TO CARTRIDGE
                Button(
                    onClick = onConvertToCartridge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("action_convert_cartridge_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Games, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CONVERT TO CARTRIDGE", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}

@Composable
private fun AddSampleFileDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf("The_Legend_of_Zelda_TOTK") }
    var selectedFormat by remember { mutableStateOf("nsp") }
    var sizeMb by remember { mutableStateOf("16384") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text(
                text = "CREATE FILE IN MY FOLDER",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("File Name (without extension)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("File Format:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("nsp", "xci", "nro", "sup").forEach { fmt ->
                        val isSelected = selectedFormat == fmt
                        if (isSelected) {
                            Button(
                                onClick = { selectedFormat = fmt },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = Color.Black),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(fmt.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { selectedFormat = fmt },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(fmt.uppercase(), fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = sizeMb,
                    onValueChange = { sizeMb = it },
                    label = { Text("Virtual File Size (MB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val size = sizeMb.toIntOrNull() ?: 1024
                    onCreate(name, selectedFormat, size)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
            ) {
                Text("CREATE IN MY FOLDER", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}

private fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    val gb = mb / 1024.0
    return if (gb >= 1.0) {
        "%.2f GB".format(gb)
    } else {
        "%.1f MB".format(mb)
    }
}
