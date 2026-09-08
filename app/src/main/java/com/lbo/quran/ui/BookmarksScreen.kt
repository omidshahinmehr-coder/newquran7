package com.lbo.quran.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbo.quran.ui.theme.quranFontByKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    viewModel: QuranViewModel,
    onBack: () -> Unit,
    onOpenAyah: (String) -> Unit
) {
    val state by viewModel.bookmarksScreen.collectAsState()
    val settings by viewModel.settings.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadBookmarks()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نشانک‌های من") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.bookmarks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("هنوز آیه‌ای نشانک‌گذاری نکرده‌اید.")
            }
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            items(state.bookmarks) { ayah ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable {
                            viewModel.requestScrollToAyah(ayah.aId)
                            onOpenAyah(ayah.aId)
                        }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val surahName = state.surahNames[ayah.surahNumber] ?: ""
                            Text(
                                "$surahName — آیه ${ayah.ayahNumber}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            IconButton(
                                onClick = {
                                    viewModel.toggleBookmark(ayah.aId)
                                    viewModel.loadBookmarks()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف نشانک", modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            ayah.text,
                            fontFamily = quranFontByKey(settings.quranFontKey),
                            fontSize = (settings.quranFontSize * 0.85f).sp,
                            lineHeight = (settings.quranFontSize * 1.6).sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
