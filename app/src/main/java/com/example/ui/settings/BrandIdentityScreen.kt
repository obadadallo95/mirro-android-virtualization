package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R

enum class LogoVariant {
    FULL_COLOR,
    ON_DARK,
    ON_LIGHT,
    MONOCHROME
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrandIdentityScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var selectedLogoVariant by remember { mutableStateOf(LogoVariant.FULL_COLOR) }
    var showSplashPreview by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Mirro Brand Identity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Brand Guidelines & Design System",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("brand_identity_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Hero Brand Board Header
            HeroLogoShowcaseCard(
                currentVariant = selectedLogoVariant,
                onSelectVariant = { selectedLogoVariant = it }
            )

            // Section 1: Logo Concept & Architectural Anatomy
            SectionHeader(
                title = "1. Approved Mark & Anatomy",
                subtitle = "Mirrored M with twin facing panels and central negative-space M"
            )
            SymbolAnatomyCard()

            // Explorations: 3 Directions Evaluated
            ExplorationsEvaluatedCard()

            // Section 2: App Icon & Small-Size Scalability
            SectionHeader(
                title = "2. Android App Icon & Scalability",
                subtitle = "Adaptive icon safe zones and micro-size legibility test"
            )
            AppIconShowcaseCard()

            // Section 3: The Color Palette
            SectionHeader(
                title = "3. Color Palette & Functional Roles",
                subtitle = "The Reflective Spectrum — Restrained, Confident, Android-First"
            )
            ColorPaletteCard()

            // Section 4: Typography Hierarchy
            SectionHeader(
                title = "4. Typography System",
                subtitle = "Clean modern neo-grotesque sans-serif pairings"
            )
            TypographyShowcaseCard()

            // Section 5: Splash Screen Concept
            SectionHeader(
                title = "5. Splash Screen Concept",
                subtitle = "Android 12+ launch screen specification"
            )
            SplashScreenShowcaseCard(onOpenPreview = { showSplashPreview = true })

            // Section 6: Brand Principles
            SectionHeader(
                title = "6. Brand Principles & Voice",
                subtitle = "Product philosophy translated to visual tone"
            )
            BrandPrinciplesCard()

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSplashPreview) {
        SplashScreenPreviewDialog(onDismiss = { showSplashPreview = false })
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HeroLogoShowcaseCard(
    currentVariant: LogoVariant,
    onSelectVariant: (LogoVariant) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Preview Canvas
            val canvasBg = when (currentVariant) {
                LogoVariant.FULL_COLOR -> Color(0xFF090D16) // Obsidian Dark
                LogoVariant.ON_DARK -> Color(0xFF0F172A)    // Midnight Navy
                LogoVariant.ON_LIGHT -> Color(0xFFF8FAFC)   // Crisp Light
                LogoVariant.MONOCHROME -> Color(0xFF000000) // Pure Black
            }

            val textPrimary = when (currentVariant) {
                LogoVariant.ON_LIGHT -> Color(0xFF0F172A)
                else -> Color(0xFFFFFFFF)
            }

            val textSecondary = when (currentVariant) {
                LogoVariant.ON_LIGHT -> Color(0xFF64748B)
                else -> Color(0xFF94A3B8)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(canvasBg)
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val iconRes = when (currentVariant) {
                        LogoVariant.MONOCHROME -> R.drawable.ic_mirro_symbol_monochrome
                        else -> R.drawable.ic_mirro_symbol
                    }

                    androidx.compose.foundation.Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = "Mirro Primary Mark",
                        modifier = Modifier.size(76.dp)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Mirro",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Same apps. More possibilities.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = textSecondary,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Variant Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LogoVariantChip("Hero (Color)", currentVariant == LogoVariant.FULL_COLOR) {
                    onSelectVariant(LogoVariant.FULL_COLOR)
                }
                LogoVariantChip("On Dark", currentVariant == LogoVariant.ON_DARK) {
                    onSelectVariant(LogoVariant.ON_DARK)
                }
                LogoVariantChip("On Light", currentVariant == LogoVariant.ON_LIGHT) {
                    onSelectVariant(LogoVariant.ON_LIGHT)
                }
                LogoVariantChip("Monochrome", currentVariant == LogoVariant.MONOCHROME) {
                    onSelectVariant(LogoVariant.MONOCHROME)
                }
            }
        }
    }
}

@Composable
private fun LogoVariantChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, fontSize = 11.sp) },
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun SymbolAnatomyCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Core Symbol Geometry",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "The Mirro symbol synthesizes three ideas into a single glyph: a mirror's reflection line, the letter M, and two autonomous app spaces.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnatomyFeatureItem(
                number = "01",
                title = "The Optical Mirror Meridian",
                description = "A central vertical axis of symmetry that acts as the mirror plane. It creates a subtle negative space corridor down the center, establishing clear physical reflection."
            )

            AnatomyFeatureItem(
                number = "02",
                title = "The Letter M Geometry",
                description = "Twin outer vertical pillars, dual rounded apexes, and inward diagonal facets naturally trace the letter M. The glyph is instantly legible as an 'M' at first glance without explanation."
            )

            AnatomyFeatureItem(
                number = "03",
                title = "Duplication with Separation",
                description = "The left wing represents the Primary Host Instance (grounded Royal Cobalt #1E40AF). The right wing represents the Reflected Instance (luminous Cyan #38BDF8). They are identical in form, but isolated in space."
            )

            AnatomyFeatureItem(
                number = "04",
                title = "Non-Entanglement Micro-Gap",
                description = "The two halves never touch or fuse together. This directly communicates the technical isolation of user data, session tokens, and cache between instances."
            )
        }
    }
}

@Composable
private fun AnatomyFeatureItem(number: String, title: String, description: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExplorationsEvaluatedCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Design Iterations Evaluated",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            ExplorationOptionRow(
                tag = "Direction A (Chosen)",
                title = "The Planar Portal (Reflected M)",
                verdict = "Selected as Hero",
                verdictColor = Color(0xFF10B981),
                analysis = "Combines high M readability with an unmistakable optical reflection line. Two distinct, symmetrical monoliths that scale effortlessly from 16dp to billboards."
            )

            ExplorationOptionRow(
                tag = "Direction B",
                title = "The Folded Glass Ribbon",
                verdict = "Rejected",
                verdictColor = Color(0xFFEF4444),
                analysis = "An isometric continuous ribbon forming two arches. While visually slick, the continuous connection contradicted data isolation, suggesting linked rather than separate accounts."
            )

            ExplorationOptionRow(
                tag = "Direction C",
                title = "The Sliced Letterform",
                verdict = "Rejected",
                verdictColor = Color(0xFFEF4444),
                analysis = "A standard M cut vertically with opacity offset. Felt like a damaged or fragmented letter rather than two complete, thriving application environments."
            )
        }
    }
}

@Composable
private fun ExplorationOptionRow(
    tag: String,
    title: String,
    verdict: String,
    verdictColor: Color,
    analysis: String
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = verdict,
                    style = MaterialTheme.typography.labelSmall,
                    color = verdictColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = analysis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppIconShowcaseCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Android Adaptive Icon & Micro-Scalability",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // Large Icon Frame
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF090D16), Color(0xFF0F172A), Color(0xFF1E293B))
                            )
                        )
                        .border(1.dp, Color(0x2BFFFFFF), RoundedCornerShape(26.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Mirro Adaptive Launcher Icon",
                        modifier = Modifier.size(108.dp)
                    )
                }
            }

            Text(
                text = "Micro-Scale Legibility Test (Actual Device Sizes)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Scaled Sizes Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScaledIconItem(size = 64, label = "64dp (Home)")
                ScaledIconItem(size = 48, label = "48dp (Drawer)")
                ScaledIconItem(size = 32, label = "32dp (Settings)")
                ScaledIconItem(size = 24, label = "24dp (Status)")
            }

            Text(
                text = "Result: The high negative space contrast between the outer stems and the inner reflection line ensures instant recognition even at tiny 24dp notification sizes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScaledIconItem(size: Int, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape((size * 0.24).dp))
                .background(Color(0xFF0F172A))
                .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape((size * 0.24).dp)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.ic_mirro_symbol),
                contentDescription = null,
                modifier = Modifier.size((size * 0.7).dp)
            )
        }
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPaletteCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "The Reflective Spectrum",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "A restrained, confident palette that pairs deep industrial cobalts with luminous reflection blues and clean neutrals.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorSwatchRow(
                    name = "Primary Blue",
                    hex = "#2563EB",
                    role = "Primary instance / Brand anchor / Trust",
                    color = Color(0xFF2563EB)
                )
                ColorSwatchRow(
                    name = "Secondary Blue",
                    hex = "#60A5FA",
                    role = "Facing reflection / Right wing / Interactive",
                    color = Color(0xFF60A5FA)
                )
                ColorSwatchRow(
                    name = "Charcoal",
                    hex = "#0F172A",
                    role = "Primary typography / Dark mode canvas / Monoliths",
                    color = Color(0xFF0F172A)
                )
                ColorSwatchRow(
                    name = "Light Gray",
                    hex = "#E2E8F0",
                    role = "Borders / Outlines / Micro-gap dividers",
                    color = Color(0xFFE2E8F0),
                    isLight = true
                )
                ColorSwatchRow(
                    name = "Background",
                    hex = "#F8FAFC",
                    role = "Light mode pristine canvas",
                    color = Color(0xFFF8FAFC),
                    isLight = true
                )
                ColorSwatchRow(
                    name = "Surface",
                    hex = "#FFFFFF",
                    role = "Cards / Elevated sheets / Crisp white",
                    color = Color(0xFFFFFFFF),
                    isLight = true
                )
            }
        }
    }
}

@Composable
private fun ColorSwatchRow(
    name: String,
    hex: String,
    role: String,
    color: Color,
    isLight: Boolean = false
) {
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color)
                .border(
                    1.dp,
                    if (isLight) Color(0xFFCBD5E1) else Color(0x22FFFFFF),
                    RoundedCornerShape(10.dp)
                )
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = hex,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = role,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TypographyShowcaseCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Typography Hierarchy",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Utilizes Android's native Neo-Grotesque family (Roboto Flex / Inter / Plus Jakarta Sans) for supreme readability, clean geometry, and high-DPI precision.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TypographySpecimenRow(
                    role = "Display / Wordmark",
                    spec = "28sp • Bold • Tracking -0.02em",
                    sample = "Mirro"
                )
                TypographySpecimenRow(
                    role = "Headlines",
                    spec = "20sp • SemiBold • Tracking -0.01em",
                    sample = "One app. Two spaces."
                )
                TypographySpecimenRow(
                    role = "Titles",
                    spec = "16sp • Medium",
                    sample = "ChatGPT Personal & ChatGPT Work"
                )
                TypographySpecimenRow(
                    role = "Body",
                    spec = "14sp • Regular • Leading 20sp",
                    sample = "Run multiple accounts with isolated data and privacy."
                )
                TypographySpecimenRow(
                    role = "Technical Labels",
                    spec = "11sp • SemiBold • Tracking +0.05em (Caps)",
                    sample = "ENGINE: BLUEPRINT STAGING"
                )
            }
        }
    }
}

@Composable
private fun TypographySpecimenRow(role: String, spec: String, sample: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = role, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(
                text = spec,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = sample,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SplashScreenShowcaseCard(onOpenPreview: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Android Launch Splash Screen",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Designed in accordance with Google's Android 12+ SplashScreen API guidelines. Center-staged, calm, and distraction-free.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF090D16))
                    .clickable { onOpenPreview() }
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.ic_mirro_splash_logo),
                        contentDescription = "Mirro Splash Emblem",
                        modifier = Modifier.size(52.dp)
                    )
                    Text(
                        text = "Tap to Preview Full Launch Screen",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF38BDF8)
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandPrinciplesCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Messaging & Tagline Hierarchy",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            TaglineRow(
                role = "Primary Tagline (Hero)",
                tagline = "Same apps. More possibilities.",
                rationale = "Core approved brand promise: unconstrained utility without ads, limits, or subscriptions."
            )

            TaglineRow(
                role = "Secondary Functional Hook",
                tagline = "Mirror your apps.",
                rationale = "Active verb form, great for store listings, onboarding, and quick share copy."
            )

            TaglineRow(
                role = "Product Promise",
                tagline = "Same apps. Independent spaces. Zero tracking.",
                rationale = "Combines feature utility with our privacy-first, ad-free product philosophy."
            )
        }
    }
}

@Composable
private fun TaglineRow(role: String, tagline: String, rationale: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = role,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "“$tagline”",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = rationale,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SplashScreenPreviewDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090D16))
                .clickable { onDismiss() }
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.ic_mirro_splash_logo),
                    contentDescription = "Mirro Splash Screen",
                    modifier = Modifier.size(110.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Mirro",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Same apps. More possibilities.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.2.sp
                )
            }

            Text(
                text = "Tap anywhere to dismiss splash preview",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }
}
