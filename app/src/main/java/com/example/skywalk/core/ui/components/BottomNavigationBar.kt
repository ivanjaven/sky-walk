package com.example.skywalk.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.skywalk.core.navigation.BottomNavItem

@Composable
fun BottomNavigationBar(
    items: List<BottomNavItem>,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onItemClick: (BottomNavItem) -> Unit
) {
    val backStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry.value?.destination?.route

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Regular navigation items
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                if (item.isMainAction) {
                    // Empty space for the center button
                    Spacer(modifier = Modifier.width(56.dp))
                } else {
                    NavigationItem(
                        item = item,
                        isSelected = currentRoute == item.route,
                        onClick = { onItemClick(item) }
                    )
                }
            }
        }

        // Center action button
        items.find { it.isMainAction }?.let { mainItem ->
            FloatingActionButton(
                onClick = { onItemClick(mainItem) },
                modifier = Modifier
                    .size(64.dp)
                    .offset(y = (-8).dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = mainItem.icon,
                    contentDescription = mainItem.name,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun NavigationItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.name,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}