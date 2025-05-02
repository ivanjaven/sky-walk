package com.example.skywalk.features.encyclopedia.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.skywalk.features.encyclopedia.domain.models.CelestialObject
import com.example.skywalk.features.encyclopedia.presentation.components.CategoryChip
import com.example.skywalk.features.encyclopedia.presentation.components.CelestialObjectCard
import com.example.skywalk.features.encyclopedia.presentation.components.SectionHeader
import com.example.skywalk.features.encyclopedia.presentation.viewmodel.EncyclopediaUiState
import com.example.skywalk.features.encyclopedia.presentation.viewmodel.EncyclopediaViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncyclopediaScreen(
    viewModel: EncyclopediaViewModel,
    onNavigateToDetail: (CelestialObject) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val navigatingBack by viewModel.navigatingBack.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Handle back navigation restoration
    LaunchedEffect(navigatingBack) {
        if (navigatingBack) {
            viewModel.setNavigatingBack(false)
            // Ensure data is loaded when returning
            val currentCategory = selectedCategory
            if (currentCategory != null) {
                viewModel.loadObjectsByCategory(currentCategory)
            } else if (searchQuery.isNotEmpty()) {
                viewModel.search(searchQuery)
            } else {
                viewModel.loadAllObjects()
            }
        }
    }

    // Handle refresh
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refreshData()
            isRefreshing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp) // Reduced from 16.dp to allow cards more space
    ) {
        // Search bar - keep padding but make it more compact
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.search(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp), // Reduced from 16.dp
            placeholder = { Text("Search celestial objects") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.search("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        // Category chips - adjust horizontal spacing for better density
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp), // Reduced from 8.dp
            contentPadding = PaddingValues(vertical = 6.dp) // Reduced from 8.dp
        ) {
            item {
                CategoryChip(
                    text = "All",
                    selected = selectedCategory == null,
                    onClick = { viewModel.loadAllObjects() }
                )
            }

            items(categories) { category ->
                CategoryChip(
                    text = category.replaceFirstChar { it.uppercase() },
                    selected = category == selectedCategory,
                    onClick = { viewModel.loadObjectsByCategory(category) }
                )
            }
        }

        // Content section
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing),
            onRefresh = { isRefreshing = true }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp) // For bottom navigation
            ) {
                when (val state = uiState) {
                    is EncyclopediaUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    is EncyclopediaUiState.Success -> {
                        if (state.celestialObjects.isEmpty()) {
                            Text(
                                text = "No celestial objects found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 16.dp // Reduce this value from what might be a larger value
                                ),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(state.celestialObjects) { celestialObject ->
                                    CelestialObjectCard(
                                        celestialObject = celestialObject,
                                        onClick = { onNavigateToDetail(celestialObject) }
                                    )
                                }
                            }
                        }
                    }

                    is EncyclopediaUiState.Error -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Error loading data",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.refreshData() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}