package com.kabulsignal.news.ui.article

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kabulsignal.news.R
import com.kabulsignal.news.data.model.Article
import com.kabulsignal.news.util.ArticleHtml

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    articleId: Int,
    onBack: () -> Unit,
    viewModel: ArticleViewModel = viewModel(factory = ArticleViewModel.factory(articleId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val article = state.article

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = article?.primaryCategory ?: stringResource(R.string.app_name),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (article != null && article.link.isNotBlank()) {
                        IconButton(onClick = { shareArticle(context, article) }) {
                            Icon(Icons.Default.Share, stringResource(R.string.action_share))
                        }
                        IconButton(onClick = { openUrl(context, article.link) }) {
                            Icon(Icons.Default.OpenInBrowser, stringResource(R.string.action_open_in_browser))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.isError || article == null -> ErrorState(onRetry = viewModel::load)
                else -> ArticleWebView(article)
            }
        }
    }
}

@Composable
private fun ArticleWebView(article: Article) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                webViewClient = object : WebViewClient() {
                    // Keep the article in-app; hand taps on links to the browser.
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        openUrl(context, url)
                        return true
                    }
                }
                val meta = listOfNotNull(article.author, article.dateLabel.takeIf { it.isNotBlank() })
                    .joinToString("  •  ")
                val html = ArticleHtml.document(
                    title = article.title,
                    meta = meta,
                    imageUrl = article.imageUrl,
                    contentHtml = article.contentHtml,
                )
                loadDataWithBaseURL(article.link.ifBlank { null }, html, "text/html", "UTF-8", null)
            }
        },
    )
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text(
            stringResource(R.string.error_generic),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun shareArticle(context: android.content.Context, article: Article) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, article.title)
        putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.link}")
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, article.title))
    }
}
