package com.example.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonRed
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.viewmodel.SwtcTab

data class NavTabItem(
    val tab: SwtcTab,
    val title: String,
    val icon: ImageVector
)

@Composable
fun SwtcNavBar(
    selectedTab: SwtcTab,
    onTabSelected: (SwtcTab) -> Unit
) {
    val items = listOf(
        NavTabItem(SwtcTab.BOOT_SETUP, "Boot", Icons.Default.RocketLaunch),
        NavTabItem(SwtcTab.VIRTUAL_STORAGE, "Storage", Icons.Default.SdStorage),
        NavTabItem(SwtcTab.MY_FOLDER, "Folder", Icons.Default.Folder),
        NavTabItem(SwtcTab.CARTRIDGE_LIBRARY, "Library", Icons.Default.Games),
        NavTabItem(SwtcTab.SAVE_STATES, "States", Icons.Default.Save),
        NavTabItem(SwtcTab.WEB_ENVIRONMENT, "Web", Icons.Default.Public),
        NavTabItem(SwtcTab.HARDWARE_MONITOR, "Hardware", Icons.Default.Hardware),
        NavTabItem(SwtcTab.SETTINGS, "Settings", Icons.Default.Settings)
    )

    NavigationBar(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = SurfaceDark,
        contentColor = Color.White
    ) {
        items.forEach { item ->
            val isSelected = selectedTab == item.tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(item.tab) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = if (isSelected) NeonRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        fontSize = 10.sp,
                        color = if (isSelected) NeonRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = SurfaceVariantDark
                ),
                modifier = Modifier.testTag("nav_tab_${item.tab.name}")
            )
        }
    }
}
