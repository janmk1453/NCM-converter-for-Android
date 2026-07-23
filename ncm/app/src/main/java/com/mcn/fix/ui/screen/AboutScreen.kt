package com.mcn.fix.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcn.fix.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun AboutScreen(
    onBack: () -> Unit = {},
    predictiveBackEnabled: Boolean = false,
) {
    BackHandler(enabled = predictiveBackEnabled, onBack = onBack)

    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val scrollProgress by remember { derivedStateOf {
        val visible = listState.firstVisibleItemIndex
        val offset = listState.firstVisibleItemScrollOffset
        if (visible > 0) 1f else (offset.toFloat() / 300f).coerceIn(0f, 1f)
    } }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.about_title),
                scrollBehavior = scrollBehavior,
                color = if (scrollProgress >= 0.999f) MiuixTheme.colorScheme.surface else Color.Transparent,
                titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = scrollProgress),
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        AboutContent(
            padding = innerPadding,
            listState = listState,
            scrollBehavior = scrollBehavior,
            scrollProgress = scrollProgress,
        )
    }
}

@Composable
private fun AboutContent(
    padding: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    scrollProgress: Float,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val packageInfo = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "1.0.0"
    val versionCode = packageInfo?.longVersionCode ?: 1L

    val shaderOk = isRuntimeShaderSupported()
    val backdrop = rememberLayerBackdrop()

    val isDark = MiuixTheme.colorScheme.surface.let { c ->
        (0.299 * c.red * 255 + 0.587 * c.green * 255 + 0.114 * c.blue * 255) < 128
    }
    val logoBlend = remember(isDark) {
        if (isDark) listOf(
            BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
            BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
            BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
        ) else listOf(
            BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
            BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
            BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
        )
    }

    val headerAlpha = 1f - scrollProgress
    val headerScale = 1f - (scrollProgress * 0.18f)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 80.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        overscrollEffect = null,
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.height(3.dp))
                Icon(
                    imageVector = MiuixIcons.ConvertFile,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .height(100.dp)
                        .graphicsLayer { alpha = headerAlpha; scaleX = headerScale; scaleY = headerScale }
                        .textureBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(24.dp),
                            blurRadius = 200f,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurColors(blendColors = logoBlend),
                            contentBlendMode = BlendMode.DstIn,
                            enabled = shaderOk,
                        ),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    fontWeight = FontWeight.Bold,
                    fontSize = 35.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .graphicsLayer { alpha = headerAlpha; scaleX = headerScale * 0.9f + 0.1f; scaleY = headerScale * 0.9f + 0.1f }
                        .textureBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(16.dp),
                            blurRadius = 150f,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurColors(blendColors = logoBlend),
                            contentBlendMode = BlendMode.DstIn,
                            enabled = shaderOk,
                        ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.about_blend_version, versionName, versionCode),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = headerAlpha },
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
        }

        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 60f,
                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                        colors = BlurColors(blendColors = logoBlend, brightness = 0f, contrast = 1f, saturation = 1.5f),
                        enabled = shaderOk,
                    ),
                colors = CardDefaults.defaultColors(
                    if (shaderOk) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Text(
                    text = stringResource(R.string.app_description),
                    modifier = Modifier.padding(16.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 60f,
                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                        colors = BlurColors(blendColors = logoBlend, brightness = 0f, contrast = 1f, saturation = 1.5f),
                        enabled = shaderOk,
                    ),
                colors = CardDefaults.defaultColors(
                    if (shaderOk) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
                ),
            ) {
                ArrowPreference(
                    title = stringResource(R.string.about_github),
                    summary = stringResource(R.string.about_github_desc),
                    onClick = { uriHandler.openUri("https://github.com/janmk1453/NCM-converter-for-Android") },
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}
