package com.example.ui.components

import android.content.Context
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.emulator.SwitchRomHeaderParser
import com.example.emulator.SwitchRomMetadata
import com.example.storage.VirtualStorageManager
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRed
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun getUriDisplayName(context: Context, uri: Uri): String? {
    var name: String? = null
    try {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) name = cursor.getString(idx)
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { p ->
                val cut = p.lastIndexOf('/')
                if (cut != -1) p.substring(cut + 1) else p
            }
        }
    } catch (e: Exception) {
        // Ignore
    }
    return name
}

/**
 * Filter categories for Switch game files.
 */
enum class SwitchFileFilter(val label: String, val extensions: Set<String>) {
    ALL_SWITCH("All Switch Files", setOf("nsp", "nro", "xci", "sup", "zip", "7z", "nsz", "xcz")),
    NSP_GAMES(".NSP Games", setOf("nsp", "nsz")),
    NRO_HOMEBREW(".NRO Homebrew", setOf("nro")),
    XCI_ROMS(".XCI Dumps", setOf("xci", "xcz")),
    ZIP_ARCHIVES(".ZIP / .7Z Archives", setOf("zip", "7z")),
    SUP_CONTAINER(".SUP Containers", setOf("sup")),
    ALL_FILES("All Files (*.*)", emptySet())
}

enum class FileSortOption(val label: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE_DESC("Size (Largest)"),
    SIZE_ASC("Size (Smallest)"),
    DATE_DESC("Date (Newest)")
}

data class StorageShortcut(
    val title: String,
    val path: String,
    val icon: ImageVector,
    val accentColor: Color
)

/**
 * Full featured Switch File Picker Modal Dialog / Sheet.
 * Allows users to browse local directories for Switch game files (.nsp, .nro, .xci, .sup),
 * inspect ROM headers, multi-select files, and import or launch them directly.
 */
@Composable
fun SwitchFilePickerDialog(
    initialDirectory: File? = null,
    onDismiss: () -> Unit,
    onFileSelected: ((File) -> Unit)? = null,
    onImportFilesToLibrary: ((List<File>) -> Unit)? = null,
    onImportFilesToMyFolder: ((List<File>) -> Unit)? = null,
    onDirectLaunch: ((File) -> Unit)? = null
) {
    val context = LocalContext.current

    // Default directories & shortcuts
    val shortcuts = remember(context) {
        val list = mutableListOf<StorageShortcut>()
        
        // 1. My Folder (Virtual Storage)
        val myFolder = context.getExternalFilesDir("MyFolder") ?: File(context.filesDir, "MyFolder")
        if (myFolder.exists()) {
            list.add(StorageShortcut("My Folder", myFolder.absolutePath, Icons.Default.Folder, NeonGreen))
        }

        // 2. App Internal Files
        list.add(StorageShortcut("App Data", context.filesDir.absolutePath, Icons.Default.Storage, NeonBlue))

        // 3. Primary External Storage
        val extStorage = Environment.getExternalStorageDirectory()
        if (extStorage != null && extStorage.exists()) {
            list.add(StorageShortcut("Device Storage", extStorage.absolutePath, Icons.Default.SdCard, NeonRed))
        }

        // 4. Downloads folder
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloads != null && downloads.exists()) {
            list.add(StorageShortcut("Downloads", downloads.absolutePath, Icons.Default.Download, NeonYellow))
        }

        // 5. Documents folder
        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (documents != null && documents.exists()) {
            list.add(StorageShortcut("Documents", documents.absolutePath, Icons.Default.Archive, NeonBlue))
        }

        list
    }

    // Determine initial active directory
    val defaultDir = remember {
        initialDirectory?.takeIf { it.exists() && it.isDirectory }
            ?: (context.getExternalFilesDir("MyFolder") ?: context.filesDir)
    }

    var currentDirectory by remember { mutableStateOf(defaultDir) }
    var selectedFilter by remember { mutableStateOf(SwitchFileFilter.ALL_SWITCH) }
    var sortOption by remember { mutableStateOf(FileSortOption.NAME_ASC) }
    var searchQuery by remember { mutableStateOf("") }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedFiles = remember { mutableStateListOf<File>() }
    var inspectedFileMetadata by remember { mutableStateOf<SwitchRomMetadata?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // System SAF multi-file launcher
    val systemPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                val copiedFiles = mutableListOf<File>()
                for (uri in uris) {
                    try {
                        val fileName = getUriDisplayName(context, uri) ?: "game_${System.currentTimeMillis()}"
                        val targetDir = File(VirtualStorageManager(context).getStorageDir(), "Games").apply { mkdirs() }
                        val targetFile = File(targetDir, fileName)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (targetFile.exists() && targetFile.length() > 0) {
                            copiedFiles.add(targetFile)
                        }
                    } catch (e: Exception) {
                        // Log error
                    }
                }
                withContext(Dispatchers.Main) {
                    if (copiedFiles.isNotEmpty()) {
                        if (onImportFilesToLibrary != null) {
                            onImportFilesToLibrary(copiedFiles)
                        } else if (onImportFilesToMyFolder != null) {
                            onImportFilesToMyFolder(copiedFiles)
                        } else if (onFileSelected != null) {
                            onFileSelected(copiedFiles.first())
                        }
                    }
                    onDismiss()
                }
            }
        }
    }

    // System SAF folder launcher
    val systemFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                    val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
                    val copiedFiles = mutableListOf<File>()
                    val targetDir = File(VirtualStorageManager(context).getStorageDir(), "Games").apply { mkdirs() }

                    context.contentResolver.query(
                        childrenUri,
                        arrayOf(
                            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
                        ),
                        null, null, null
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        while (cursor.moveToNext()) {
                            val docId = if (idCol != -1) cursor.getString(idCol) else null
                            val name = if (nameCol != -1) cursor.getString(nameCol) else null
                            if (docId != null && name != null) {
                                val ext = name.substringAfterLast('.', "").lowercase()
                                if (ext in setOf("nsp", "xci", "nro", "sup", "zip", "7z", "nsz", "xcz")) {
                                    val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                                    val targetFile = File(targetDir, name)
                                    context.contentResolver.openInputStream(docUri)?.use { input ->
                                        targetFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    if (targetFile.exists() && targetFile.length() > 0) {
                                        copiedFiles.add(targetFile)
                                    }
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (copiedFiles.isNotEmpty()) {
                            onImportFilesToLibrary?.invoke(copiedFiles)
                            onImportFilesToMyFolder?.invoke(copiedFiles)
                        }
                        onDismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { onDismiss() }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .testTag("switch_file_picker_dialog"),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.verticalGradient(listOf(NeonRed, NeonBlue, NeonGreen))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(listOf(NeonRed, NeonBlue))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Games,
                                    contentDescription = "Switch File Picker",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "SWITCH FILE BROWSER",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = Color.White
                                )
                                Text(
                                    text = "Filter and Import .NSP, .NRO, .XCI ROMs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NeonGreen
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Multi-Select Toggle
                            IconButton(
                                onClick = {
                                    isMultiSelectMode = !isMultiSelectMode
                                    if (!isMultiSelectMode) selectedFiles.clear()
                                },
                                modifier = Modifier.testTag("toggle_multiselect_button")
                            ) {
                                Icon(
                                    imageVector = if (isMultiSelectMode) Icons.Default.CheckCircle else Icons.Default.SelectAll,
                                    contentDescription = "Multi-Select",
                                    tint = if (isMultiSelectMode) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Close Button
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("close_file_picker_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Storage Root Shortcuts Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        shortcuts.forEach { shortcut ->
                            val isCurrentRoot = currentDirectory.absolutePath.startsWith(shortcut.path)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrentRoot) shortcut.accentColor.copy(alpha = 0.2f) else SurfaceVariantDark,
                                border = if (isCurrentRoot) ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.horizontalGradient(listOf(shortcut.accentColor, NeonBlue))
                                ) else null,
                                modifier = Modifier.clickable {
                                    val target = File(shortcut.path)
                                    if (target.exists() && target.isDirectory) {
                                        currentDirectory = target
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = shortcut.icon,
                                        contentDescription = shortcut.title,
                                        tint = shortcut.accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = shortcut.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrentRoot) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Breadcrumb Path Navigation Row
                    PathBreadcrumbBar(
                        currentDirectory = currentDirectory,
                        onNavigateTo = { dir -> currentDirectory = dir },
                        onNavigateUp = {
                            val parent = currentDirectory.parentFile
                            if (parent != null && parent.exists() && parent.canRead()) {
                                currentDirectory = parent
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search & Filter Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search files & folders...", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonBlue,
                                unfocusedBorderColor = SurfaceBorder,
                                focusedContainerColor = SurfaceVariantDark,
                                unfocusedContainerColor = SurfaceVariantDark
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("file_search_input")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Sort Menu
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceVariantDark)
                                    .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                                    .testTag("sort_files_button")
                            ) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort", tint = NeonYellow)
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(SurfaceDark)
                            ) {
                                FileSortOption.values().forEach { opt ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = opt.label,
                                                color = if (sortOption == opt) NeonGreen else Color.White,
                                                fontWeight = if (sortOption == opt) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            sortOption = opt
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Extension Filter Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SwitchFileFilter.values().forEach { filter ->
                            val isSelected = selectedFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = filter },
                                label = {
                                    Text(
                                        text = filter.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (filter) {
                                        SwitchFileFilter.NSP_GAMES -> NeonRed
                                        SwitchFileFilter.NRO_HOMEBREW -> NeonBlue
                                        SwitchFileFilter.XCI_ROMS -> NeonYellow
                                        SwitchFileFilter.ZIP_ARCHIVES -> NeonYellow
                                        SwitchFileFilter.SUP_CONTAINER -> NeonGreen
                                        else -> NeonBlue
                                    },
                                    selectedLabelColor = Color.Black,
                                    containerColor = SurfaceVariantDark,
                                    labelColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = SurfaceBorder,
                                    selectedBorderColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // File & Directory List Area
                    val (folders, files) = remember(currentDirectory, selectedFilter, sortOption, searchQuery) {
                        loadAndFilterDirectoryContents(
                            directory = currentDirectory,
                            filter = selectedFilter,
                            sortOption = sortOption,
                            searchQuery = searchQuery
                        )
                    }

                    if (folders.isEmpty() && files.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.VideogameAsset,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No matching Switch files found in this directory",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Filtered by: ${selectedFilter.label}\nSelect 'My Folder' or 'All Files (*.*)' or tap parent folder above.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Subdirectories
                            items(folders, key = { "dir_${it.absolutePath}" }) { dir ->
                                DirectoryItemCard(
                                    directory = dir,
                                    onClick = {
                                        if (dir.canRead()) {
                                            currentDirectory = dir
                                        }
                                    }
                                )
                            }

                            // Files
                            items(files, key = { "file_${it.absolutePath}" }) { file ->
                                val isSelected = selectedFiles.contains(file)
                                FileItemRow(
                                    file = file,
                                    isMultiSelectMode = isMultiSelectMode,
                                    isSelected = isSelected,
                                    onToggleSelect = {
                                        if (isSelected) selectedFiles.remove(file) else selectedFiles.add(file)
                                    },
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            if (isSelected) selectedFiles.remove(file) else selectedFiles.add(file)
                                        } else {
                                            onFileSelected?.invoke(file)
                                        }
                                    },
                                    onInspect = {
                                        inspectedFileMetadata = SwitchRomHeaderParser.parseRomFile(file)
                                    },
                                    onImportToLibrary = {
                                        onImportFilesToLibrary?.invoke(listOf(file))
                                    },
                                    onDirectLaunch = {
                                        onDirectLaunch?.invoke(file)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Action Bar (Multi-select actions or System SAF shortcut)
                    if (isMultiSelectMode) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(NeonGreen, NeonBlue))
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${selectedFiles.size} Files Selected",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonGreen
                                    )
                                    Text(
                                        text = "Total Size: ${formatBytes(selectedFiles.sumOf { it.length() })}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (selectedFiles.isNotEmpty()) {
                                        if (onImportFilesToLibrary != null) {
                                            Button(
                                                onClick = {
                                                    onImportFilesToLibrary(selectedFiles.toList())
                                                    onDismiss()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = NeonGreen,
                                                    contentColor = Color.Black
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.testTag("import_selected_games_button")
                                            ) {
                                                Icon(Icons.Default.Games, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Import (${selectedFiles.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }

                                        if (onImportFilesToMyFolder != null) {
                                            Button(
                                                onClick = {
                                                    onImportFilesToMyFolder(selectedFiles.toList())
                                                    onDismiss()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = NeonBlue,
                                                    contentColor = Color.Black
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Add to Folder", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                // Select all Switch files in current folder
                                                selectedFiles.clear()
                                                selectedFiles.addAll(files)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Select All", color = NeonBlue, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Quick Helper Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${files.size} game file(s) found in current directory",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            systemFolderPickerLauncher.launch(null)
                                        } catch (e: Exception) {
                                            // Fallback
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pick Folder", fontSize = 11.sp, color = Color.White)
                                }

                                OutlinedButton(
                                    onClick = {
                                        systemPickerLauncher.launch(arrayOf("*/*"))
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pick Files (SAF)", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Header Metadata Inspection Modal
        inspectedFileMetadata?.let { meta ->
            RomMetadataInspectionDialog(
                metadata = meta,
                onDismiss = { inspectedFileMetadata = null },
                onDirectLaunch = {
                    inspectedFileMetadata = null
                    onDirectLaunch?.invoke(File(meta.filePath))
                },
                onImportToLibrary = {
                    inspectedFileMetadata = null
                    onImportFilesToLibrary?.invoke(listOf(File(meta.filePath)))
                }
            )
        }
    }
}

/**
 * Interactive Path Breadcrumb Bar.
 */
@Composable
private fun PathBreadcrumbBar(
    currentDirectory: File,
    onNavigateTo: (File) -> Unit,
    onNavigateUp: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceVariantDark,
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(listOf(SurfaceBorder, NeonBlue.copy(alpha = 0.5f)))
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateUp,
                enabled = currentDirectory.parentFile != null,
                modifier = Modifier.size(32.dp).testTag("navigate_up_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "Up Directory",
                    tint = if (currentDirectory.parentFile != null) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Breadcrumb path parts
            val pathSegments = remember(currentDirectory) {
                val segments = mutableListOf<File>()
                var curr: File? = currentDirectory
                while (curr != null) {
                    segments.add(0, curr)
                    curr = curr.parentFile
                }
                segments
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pathSegments.forEachIndexed { index, seg ->
                    val isLast = index == pathSegments.size - 1
                    val displayName = if (seg.name.isEmpty()) "/" else seg.name

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isLast) NeonBlue.copy(alpha = 0.2f) else Color.Transparent,
                        modifier = Modifier.clickable { onNavigateTo(seg) }
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                            color = if (isLast) NeonBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    if (!isLast) {
                        Text(
                            text = " / ",
                            style = MaterialTheme.typography.labelSmall,
                            color = SurfaceBorder
                        )
                    }
                }
            }
        }
    }
}

/**
 * Directory Folder Card
 */
@Composable
private fun DirectoryItemCard(
    directory: File,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("directory_item_${directory.name}"),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(SurfaceBorder, SurfaceVariantDark))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonYellow.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Folder",
                    tint = NeonYellow,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = directory.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val childCount = directory.listFiles()?.size ?: 0
                Text(
                    text = "$childCount items",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ArrowBack, // Rotated or arrow icon
                contentDescription = "Enter",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * File Item Row with Switch ROM badge, size, inspection & action buttons.
 */
@Composable
private fun FileItemRow(
    file: File,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onInspect: () -> Unit,
    onImportToLibrary: () -> Unit,
    onDirectLaunch: () -> Unit
) {
    val ext = file.extension.lowercase()
    val (badgeText, badgeColor, icon) = when (ext) {
        "nsp", "nsz" -> Triple("NSP GAME", NeonRed, Icons.Default.Games)
        "nro" -> Triple("NRO HOMEBREW", NeonBlue, Icons.Default.Code)
        "xci", "xcz" -> Triple("XCI CARTRIDGE", NeonYellow, Icons.Default.VideogameAsset)
        "zip" -> Triple("ZIP ARCHIVE", NeonYellow, Icons.Default.Archive)
        "7z" -> Triple("7Z ARCHIVE", NeonYellow, Icons.Default.Archive)
        "sup" -> Triple("SUP PACKAGE", NeonGreen, Icons.Default.Archive)
        "keys", "dat" -> Triple("KEYS FILE", NeonGreen, Icons.Default.Info)
        else -> Triple(ext.uppercase(), MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.VideogameAsset)
    }

    val isExecutable = ext in setOf("nsp", "nro", "xci", "sup", "zip", "7z", "nsz", "xcz")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("file_item_${file.name}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SurfaceVariantDark else SurfaceDark
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = if (isSelected) Brush.horizontalGradient(listOf(NeonGreen, NeonBlue))
            else Brush.horizontalGradient(listOf(SurfaceBorder, badgeColor.copy(alpha = 0.3f)))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = NeonGreen,
                        checkmarkColor = Color.Black,
                        uncheckedColor = SurfaceBorder
                    ),
                    modifier = Modifier.padding(end = 6.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = badgeText,
                        tint = badgeColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

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
                        color = badgeColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatBytes(file.length()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Inspect Header Icon Button
            IconButton(
                onClick = onInspect,
                modifier = Modifier.size(32.dp).testTag("inspect_header_${file.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Inspect Header",
                    tint = NeonBlue,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Fast Import or Play Action
            if (isExecutable) {
                Button(
                    onClick = onImportToLibrary,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = badgeColor.copy(alpha = 0.2f)),
                    modifier = Modifier.height(30.dp).testTag("import_file_${file.name}")
                ) {
                    Icon(Icons.Default.Games, contentDescription = null, tint = badgeColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * ROM Header Inspection Dialog
 */
@Composable
private fun RomMetadataInspectionDialog(
    metadata: SwitchRomMetadata,
    onDismiss: () -> Unit,
    onDirectLaunch: () -> Unit,
    onImportToLibrary: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ROM HEADER METADATA",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceVariantDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = metadata.titleName.ifEmpty { metadata.fileName },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen
                        )
                        Text(
                            text = "Title ID: ${metadata.titleId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonBlue
                        )
                    }
                }

                MetadataRow(label = "Format / Container", value = metadata.format)
                MetadataRow(label = "Header Magic", value = "${metadata.magic} (${if (metadata.isValidMagic) "VALID" else "GENERIC"})")
                MetadataRow(label = "File Size", value = formatBytes(metadata.fileSizeBytes))
                MetadataRow(label = "Master Key Revision", value = "0x%02X".format(metadata.masterKeyRevision))
                MetadataRow(label = "Target SDK Version", value = metadata.sdkVersion)
                if (metadata.entryPointOffset > 0) {
                    MetadataRow(label = "Entry Point Offset", value = "0x%08X".format(metadata.entryPointOffset))
                }
                if (metadata.textSegmentSize > 0) {
                    MetadataRow(label = ".text Segment", value = formatBytes(metadata.textSegmentSize))
                    MetadataRow(label = ".rodata Segment", value = formatBytes(metadata.rodataSegmentSize))
                    MetadataRow(label = ".data Segment", value = formatBytes(metadata.dataSegmentSize))
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Path: ${metadata.filePath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onImportToLibrary,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
                ) {
                    Text("IMPORT TO LIBRARY", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = onDirectLaunch,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PLAY NOW", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("CLOSE")
            }
        }
    )
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/**
 * Filter directory contents based on user selected extensions and sort order.
 */
private fun loadAndFilterDirectoryContents(
    directory: File,
    filter: SwitchFileFilter,
    sortOption: FileSortOption,
    searchQuery: String
): Pair<List<File>, List<File>> {
    if (!directory.exists() || !directory.canRead()) {
        return Pair(emptyList(), emptyList())
    }

    val allFiles = directory.listFiles() ?: return Pair(emptyList(), emptyList())

    val query = searchQuery.trim().lowercase()

    // 1. Filter Subdirectories
    val folders = allFiles
        .filter { it.isDirectory && !it.name.startsWith(".") }
        .filter { if (query.isNotEmpty()) it.name.lowercase().contains(query) else true }
        .sortedBy { it.name.lowercase() }

    // 2. Filter Files by extensions and search query
    val files = allFiles
        .filter { it.isFile && !it.name.startsWith(".") }
        .filter { file ->
            val ext = file.extension.lowercase()
            val matchesFilter = when (filter) {
                SwitchFileFilter.ALL_FILES -> true
                else -> filter.extensions.contains(ext)
            }
            val matchesQuery = if (query.isNotEmpty()) file.name.lowercase().contains(query) else true
            matchesFilter && matchesQuery
        }

    // 3. Sort files
    val sortedFiles = when (sortOption) {
        FileSortOption.NAME_ASC -> files.sortedBy { it.name.lowercase() }
        FileSortOption.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
        FileSortOption.SIZE_DESC -> files.sortedByDescending { it.length() }
        FileSortOption.SIZE_ASC -> files.sortedBy { it.length() }
        FileSortOption.DATE_DESC -> files.sortedByDescending { it.lastModified() }
    }

    return Pair(folders, sortedFiles)
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    val gb = mb / 1024.0
    return if (gb >= 1.0) {
        "%.2f GB".format(gb)
    } else {
        "%.1f MB".format(mb)
    }
}
