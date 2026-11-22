// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.projects.R
import org.mochios.projects.repository.ProjectsRepository
import javax.inject.Inject
import org.mochios.android.R as MochiR

data class FileStats(
    val file: String,
    val additions: Int,
    val deletions: Int
)

/**
 * @property diff The diff as the server sent it; null until it has, "" for
 *   branches with no changes between them.
 */
data class DiffViewerUiState(
    val diff: String? = null,
    val isLoading: Boolean = false,
    val error: MochiError? = null,
    val isUnified: Boolean = true,
    val fileStats: List<FileStats> = emptyList()
)

@HiltViewModel
class DiffViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectsRepository
) : ViewModel() {

    val repo: String = savedStateHandle.get<String>("repo") ?: ""
    val source: String = savedStateHandle.get<String>("source") ?: ""
    val target: String = savedStateHandle.get<String>("target") ?: ""

    private val _uiState = MutableStateFlow(DiffViewerUiState())
    val uiState: StateFlow<DiffViewerUiState> = _uiState.asStateFlow()

    init {
        loadPreference()
        loadDiff()
    }

    private fun loadPreference() {
        viewModelScope.launch {
            try {
                val unified = repository.getDiffPreference() != "split"
                if (unified != _uiState.value.isUnified) {
                    _uiState.value = _uiState.value.copy(isUnified = unified)
                }
            } catch (_: Exception) {
                // Default ("unified") already set; ignore failures.
            }
        }
    }

    fun loadDiff() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val diff = repository.getDiff(repo, target, source)
                _uiState.value = _uiState.value.copy(
                    diff = diff,
                    fileStats = parseFileStats(diff),
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun toggleViewMode() {
        val newUnified = !_uiState.value.isUnified
        _uiState.value = _uiState.value.copy(isUnified = newUnified)
        viewModelScope.launch {
            try {
                repository.setDiffPreference(if (newUnified) "unified" else "split")
            } catch (_: Exception) {
                // Persistence failure is non-fatal; UI already reflects toggle.
            }
        }
    }

    private fun parseFileStats(diff: String): List<FileStats> {
        val stats = mutableListOf<FileStats>()
        var currentFile = ""
        var adds = 0
        var dels = 0
        for (line in diff.split("\n")) {
            if (line.startsWith("diff --git")) {
                if (currentFile.isNotEmpty()) {
                    stats.add(FileStats(currentFile, adds, dels))
                }
                currentFile = line.substringAfter(" b/", "")
                adds = 0
                dels = 0
            } else if (currentFile.isNotEmpty()) {
                if (line.startsWith("+") && !line.startsWith("+++")) adds++
                else if (line.startsWith("-") && !line.startsWith("---")) dels++
            }
        }
        if (currentFile.isNotEmpty()) {
            stats.add(FileStats(currentFile, adds, dels))
        }
        return stats
    }
}

private fun escape(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

/**
 * The page the diff viewer shows: the changed files and their added and
 * removed line counts, in the user's number format, at the top of the same
 * scrolling page as the diff, so a long list of files never leaves the diff
 * without room; then the diff, [unified] or side by side.
 */
internal fun diffPage(diff: String, unified: Boolean, stats: List<FileStats>, format: Format): String {
    val summary = if (stats.isEmpty()) "" else buildString {
        append("<table class=\"summary\">")
        for (stat in stats) {
            append("<tr><td class=\"name\">").append(escape(stat.file)).append("</td><td class=\"count\">")
            if (stat.additions > 0) append("<span class=\"plus\">+").append(format.formatNumber(stat.additions)).append("</span> ")
            if (stat.deletions > 0) append("<span class=\"minus\">-").append(format.formatNumber(stat.deletions)).append("</span>")
            append("</td></tr>")
        }
        append("</table>")
    }
    val escaped = escape(diff)
    val body = if (unified) renderUnified(escaped) else renderSplit(escaped)
    return wrapHtml(summary + body, !unified)
}

private fun renderUnified(escaped: String): String =
    escaped.split("\n").joinToString("\n") { line ->
        when {
            line.startsWith("+") -> "<div class=\"add\">$line</div>"
            line.startsWith("-") -> "<div class=\"del\">$line</div>"
            line.startsWith("@@") -> "<div class=\"hunk\">$line</div>"
            line.startsWith("diff ") -> "<div class=\"file\">$line</div>"
            else -> "<div class=\"ctx\">$line</div>"
        }
    }

private fun renderSplit(escaped: String): String {
    val lines = escaped.split("\n")
    val sb = StringBuilder()
    val leftLines = mutableListOf<Pair<String, String>>() // class, content
    val rightLines = mutableListOf<Pair<String, String>>()

    fun flushSplit() {
        val maxLen = maxOf(leftLines.size, rightLines.size)
        for (i in 0 until maxLen) {
            val (lc, lt) = leftLines.getOrElse(i) { "empty" to "" }
            val (rc, rt) = rightLines.getOrElse(i) { "empty" to "" }
            sb.append("<tr><td class=\"$lc\">$lt</td><td class=\"$rc\">$rt</td></tr>\n")
        }
        leftLines.clear()
        rightLines.clear()
    }

    sb.append("<table class=\"split\">")
    for (line in lines) {
        when {
            line.startsWith("diff ") || line.startsWith("@@") -> {
                flushSplit()
                val cls = if (line.startsWith("@@")) "hunk" else "file"
                sb.append("<tr><td class=\"$cls\" colspan=\"2\">$line</td></tr>\n")
            }
            line.startsWith("-") -> leftLines.add("del" to line)
            line.startsWith("+") -> rightLines.add("add" to line)
            else -> {
                flushSplit()
                sb.append("<tr><td class=\"ctx\">$line</td><td class=\"ctx\">$line</td></tr>\n")
            }
        }
    }
    flushSplit()
    sb.append("</table>")
    return sb.toString()
}

private fun wrapHtml(body: String, isSplit: Boolean): String {
    val splitCss = if (isSplit) """
        table.split { width: 100%; border-collapse: collapse; table-layout: fixed; }
        table.split td { padding: 1px 4px; white-space: pre-wrap; word-break: break-all; width: 50%; vertical-align: top; border-right: 1px solid #d0d0d0; }
        table.split td:last-child { border-right: none; }
        .empty { background: #f5f5f5; }
        @media (prefers-color-scheme: dark) { .empty { background: #222; } table.split td { border-right-color: #444; } }
    """ else """
        div { padding: 1px 4px; white-space: pre-wrap; word-break: break-all; }
    """

    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            body { font-family: monospace; font-size: 12px; margin: 0; padding: 8px; background: #fff; }
            table.summary { width: 100%; border-collapse: collapse; margin-bottom: 8px; font-size: 11px; }
            table.summary td { padding: 2px 4px; vertical-align: top; }
            table.summary .name { word-break: break-all; color: #24292e; }
            table.summary .count { text-align: right; white-space: nowrap; }
            .plus { color: #22863a; }
            .minus { color: #cb2431; }
            .add { background: #e6ffed; color: #22863a; }
            .del { background: #ffeef0; color: #cb2431; }
            .hunk { background: #f1f8ff; color: #032f62; font-weight: bold; margin-top: 8px; }
            .file { background: #f1f8ff; color: #032f62; font-weight: bold; margin-top: 12px; }
            .ctx { color: #24292e; }
            $splitCss
            @media (prefers-color-scheme: dark) {
                body { background: #1a1c1e; }
                table.summary .name { color: #e3e2e6; }
                .plus { color: #7ee787; }
                .minus { color: #f85149; }
                .add { background: #1b3a28; color: #7ee787; }
                .del { background: #3c1b20; color: #f85149; }
                .hunk { background: #1c2d41; color: #79c0ff; }
                .file { background: #1c2d41; color: #79c0ff; }
                .ctx { color: #e3e2e6; }
            }
        </style>
        </head>
        <body>$body</body>
        </html>
    """.trimIndent()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffViewerScreen(
    onBack: () -> Unit,
    viewModel: DiffViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.projects_diff_title, viewModel.source, viewModel.target)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(MochiR.string.common_back))
                    }
                },
                actions = {
                    FilterChip(
                        selected = uiState.isUnified,
                        onClick = { viewModel.toggleViewMode() },
                        label = { Text(if (uiState.isUnified) stringResource(R.string.projects_diff_unified) else stringResource(R.string.projects_diff_split)) },
                        leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.height(16.dp)) }
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val diff = uiState.diff
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.error != null -> {
                    ErrorState(
                        error = uiState.error!!,
                        onRetry = { viewModel.loadDiff() }
                    )
                }

                // On what the server sent, not on the page made of it, which
                // always has markup.
                !diff.isNullOrBlank() -> {
                    val format = LocalFormat.current
                    val page = remember(diff, uiState.isUnified, uiState.fileStats, format) {
                        diffPage(diff, uiState.isUnified, uiState.fileStats, format)
                    }
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = false
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL(null, page, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                diff != null -> {
                    Text(
                        text = stringResource(R.string.projects_diff_empty),
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
