package com.example.skywalk.features.encyclopedia.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.skywalk.features.encyclopedia.domain.models.CelestialObject
import com.example.skywalk.features.encyclopedia.presentation.components.FlowRow
import com.example.skywalk.features.encyclopedia.presentation.components.PropertyItem
import com.example.skywalk.features.encyclopedia.presentation.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncyclopediaDetailScreen(
    celestialObject: CelestialObject,
    onBackPressed: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header image with top app bar overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                // Full-width image
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(celestialObject.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = celestialObject.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Gradient overlay for better text visibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                )

                // Top app bar (transparent)
                TopAppBar(
                    title = { /* Empty title, will show below */ },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackPressed,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )

                // Object name and type at bottom of image
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    // Type badge
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = celestialObject.type.name.replace("_", " "),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Object name
                    Text(
                        text = celestialObject.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Visibility badge if available
                celestialObject.visibility?.magnitude?.let { magnitude ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Magnitude: ${String.format("%.1f", magnitude)}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Summary if available
                if (celestialObject.summary.isNotEmpty()) {
                    Text(
                        text = celestialObject.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Divider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                }

                // Key properties section
                celestialObject.properties?.let { properties ->
                    if (properties.distance.isNotEmpty() || properties.diameter.isNotEmpty() || properties.mass.isNotEmpty()) {
                        SectionHeader(title = "Key Properties")

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (properties.distance.isNotEmpty()) {
                                    PropertyItem(
                                        icon = Icons.Filled.Place,
                                        title = "Distance",
                                        value = properties.distance
                                    )
                                }

                                if (properties.diameter.isNotEmpty()) {
                                    PropertyItem(
                                        icon = Icons.Filled.Done,
                                        title = "Diameter",
                                        value = properties.diameter
                                    )
                                }

                                if (properties.mass.isNotEmpty()) {
                                    PropertyItem(
                                        icon = Icons.Filled.Star,
                                        title = "Mass",
                                        value = properties.mass
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Description section
                SectionHeader(title = "Description")
                Text(
                    text = celestialObject.description,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Observing guide
                celestialObject.observing?.let { observing ->
                    if (observing.bestTimeToView.isNotEmpty() ||
                        observing.findingTips.isNotEmpty() ||
                        observing.equipment.isNotEmpty() ||
                        observing.features.isNotEmpty()
                    ) {
                        SectionHeader(title = "Observing Guide")

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (observing.bestTimeToView.isNotEmpty()) {
                                    ObservingItem(
                                        icon = Icons.Filled.DateRange,
                                        title = "Best Time to View",
                                        content = observing.bestTimeToView
                                    )
                                }

                                if (observing.findingTips.isNotEmpty()) {
                                    ObservingItem(
                                        icon = Icons.Filled.Search,
                                        title = "Finding Tips",
                                        content = observing.findingTips
                                    )
                                }

                                if (observing.equipment.isNotEmpty()) {
                                    ObservingItem(
                                        icon = Icons.Filled.Build,
                                        title = "Recommended Equipment",
                                        content = observing.equipment
                                    )
                                }

                                if (observing.features.isNotEmpty()) {
                                    ObservingItem(
                                        icon = Icons.Filled.Info,
                                        title = "What to Look For",
                                        content = observing.features
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Celestial coordinates
                celestialObject.coordinates?.let { coordinates ->
                    if (coordinates.rightAscension.isNotEmpty() || coordinates.declination.isNotEmpty()) {
                        SectionHeader(title = "Celestial Coordinates")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (coordinates.rightAscension.isNotEmpty()) {
                                CoordinateCard(
                                    label = "Right Ascension",
                                    value = coordinates.rightAscension,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (coordinates.declination.isNotEmpty()) {
                                CoordinateCard(
                                    label = "Declination",
                                    value = coordinates.declination,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Interesting facts
                if (celestialObject.facts.isNotEmpty()) {
                    SectionHeader(title = "Interesting Facts")

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            celestialObject.facts.forEachIndexed { index, fact ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(28.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondary
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${index + 1}",
                                                color = MaterialTheme.colorScheme.onSecondary,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Text(
                                        text = fact,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (index < celestialObject.facts.size - 1) {
                                    Divider(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 44.dp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Keywords
                if (celestialObject.keywords.isNotEmpty()) {
                    SectionHeader(title = "Keywords")

                    FlowRow(
                        mainAxisSpacing = 8,
                        crossAxisSpacing = 8
                    ) {
                        celestialObject.keywords.forEach { keyword ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = keyword,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ObservingItem(
    icon: ImageVector,
    title: String,
    content: String
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 40.dp)
        )
    }
}

@Composable
private fun CoordinateCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}