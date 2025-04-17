package com.example.skywalk.features.encyclopedia.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.skywalk.features.encyclopedia.domain.models.CelestialObject
import com.example.skywalk.features.encyclopedia.presentation.components.CategoryChip
import com.example.skywalk.features.encyclopedia.presentation.components.CelestialObjectCard
import com.example.skywalk.features.encyclopedia.presentation.components.FeaturedObjectCard
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
                viewModel.loadFeaturedObjects()
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
        modifier = Modifier.fillMaxSize()
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.search(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search celestial objects") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            singleLine = true,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        )

        // Categories row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CategoryChip(
                    text = "All",
                    selected = selectedCategory == null,
                    onClick = { viewModel.loadFeaturedObjects() }
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

        Spacer(modifier = Modifier.height(8.dp))

        // Content
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing),
            onRefresh = { isRefreshing = true }
        ) {
            when (val state = uiState) {
                is EncyclopediaUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is EncyclopediaUiState.Success -> {
                    if (state.celestialObjects.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No celestial objects found")
                        }
                    } else if (selectedCategory == null && searchQuery.isEmpty()) {
                        // Featured layout with header and grid
                        FeaturedLayout(
                            celestialObjects = state.celestialObjects,
                            onObjectClick = onNavigateToDetail
                        )
                    } else {
                        // Grid layout for categories or search results
                        CelestialObjectsGrid(
                            celestialObjects = state.celestialObjects,
                            onObjectClick = onNavigateToDetail
                        )
                    }
                }

                is EncyclopediaUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Error: ${state.message}")
                    }
                }
            }
        }
    }
}

@Composable
fun FeaturedLayout(
    celestialObjects: List<CelestialObject>,
    onObjectClick: (CelestialObject) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp) // For bottom navigation
    ) {
        // Featured section
        item {
            Text(
                text = "Featured",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Featured horizontal scrollable cards
        val featuredObjects = celestialObjects.take(5)
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(featuredObjects) { celestialObject ->
                    FeaturedObjectCard(
                        celestialObject = celestialObject,
                        onClick = { onObjectClick(celestialObject) }
                    )
                }
            }
        }

        // More to explore section
        item {
            Text(
                text = "More to Explore",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }

        // Grid of remaining objects
        val remainingObjects = celestialObjects.drop(5)
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                // Disable scrolling in the grid as we're inside a LazyColumn
                userScrollEnabled = false,
                modifier = Modifier.height((remainingObjects.size * 120).dp)
            ) {
                items(remainingObjects) { celestialObject ->
                    CelestialObjectCard(
                        celestialObject = celestialObject,
                        onClick = { onObjectClick(celestialObject) }
                    )
                }
            }
        }
    }
}

@Composable
fun CelestialObjectsGrid(
    celestialObjects: List<CelestialObject>,
    onObjectClick: (CelestialObject) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(celestialObjects) { celestialObject ->
            CelestialObjectCard(
                celestialObject = celestialObject,
                onClick = { onObjectClick(celestialObject) }
            )
        }
    }
}