package org.mochios.projects.ui.`object`

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
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
import org.mochios.android.api.userMessage
import org.mochios.projects.R
import org.mochios.projects.repository.ProjectsRepository
import javax.inject.Inject
import org.mochios.android.R as MochiR

data class FileStats(
    val file: String,
    val additions: Int,
    val deletions: Int
)

data class DiffViewerUiState(
    val diffHtml: String = "",
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

    val projectId: String = savedStateHandle.get<String>("projectId") ?: ""
    val repo: String = savedStateHandle.get<String>("repo") ?: ""
    val source: String = savedStateHandle.get<String>("source") ?: ""
    val target: String = savedStateHandle.get<String>("target") ?: ""

    private val _uiState = MutableStateFlow(DiffViewerUiState())
    val uiState: StateFlow<DiffViewerUiState> = _uiState.asStateFlow()

    init {
        loadPreference()
        loadDiff()
    }

    private var rawDiff: String = ""

    private fun loadPreference() {
        viewModelScope.launch {
            try {
                val pref = repository.getDiffPreference()
                val unified = pref != "split"
                if (unified != _uiState.value.isUnified) {
                    _uiState.value = _uiState.value.copy(
                        isUnified = unified,
                        diffHtml = if (rawDiff.isNotEmpty()) renderDiff(rawDiff, unified) else _uiState.value.diffHtml
                    )
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
                rawDiff = diff
                val stats = parseFileStats(diff)
                _uiState.value = _uiState.value.copy(
                    diffHtml = renderDiff(diff, _uiState.value.isUnified),
                    fileStats = stats,
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
        _uiState.value = _uiState.value.copy(
            isUnified = newUnified,
            diffHtml = renderDiff(rawDiff, newUnified)
        )
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

    private fun renderDiff(diff: String, unified: Boolean): String {
        val escaped = diff
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        return if (unified) renderUnified(escaped) else renderSplit(escaped)
    }

    private fun renderUnified(escaped: String): String {
        val lines = escaped.split("\n")
        val htmlLines = lines.joinToString("\n") { line ->
            when {
                line.startsWith("+") -> "<div class=\"add\">$line</div>"
                line.startsWith("-") -> "<div class=\"del\">$line</div>"
                line.startsWith("@@") -> "<div class=\"hunk\">$line</div>"
                line.startsWith("diff ") -> "<div class=\"file\">$line</div>"
                else -> "<div class=\"ctx\">$line</div>"
            }
        }
        return wrapHtml(htmlLines, false)
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

        sb.append("<table>")
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
        return wrapHtml(sb.toString(), true)
    }

    private fun wrapHtml(body: String, isSplit: Boolean): String {
        val splitCss = if (isSplit) """
            table { width: 100%; border-collapse: collapse; table-layout: fixed; }
            td { padding: 1px 4px; white-space: pre-wrap; word-break: break-all; width: 50%; vertical-align: top; border-right: 1px solid #d0d0d0; }
            td:last-child { border-right: none; }
            .empty { background: #f5f5f5; }
            @media (prefers-color-scheme: dark) { .empty { background: #222; } td { border-right-color: #444; } }
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
                .add { background: #e6ffed; color: #22863a; }
                .del { background: #ffeef0; color: #cb2431; }
                .hunk { background: #f1f8ff; color: #032f62; font-weight: bold; margin-top: 8px; }
                .file { background: #f1f8ff; color: #032f62; font-weight: bold; margin-top: 12px; }
                .ctx { color: #24292e; }
                $splitCss
                @media (prefers-color-scheme: dark) {
                    body { background: #1a1c1e; }
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(MochiR.string.common_back))
                    }
                },
                actions = {
                    FilterChip(
                        selected = uiState.isUnified,
                        onClick = { viewModel.toggleViewMode() },
                        label = { Text(if (uiState.isUnified) stringResource(R.string.projects_diff_unified) else stringResource(R.string.projects_diff_split)) },
                        leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = androidx.compose.ui.Modifier.height(16.dp)) }
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
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.error != null -> {
                    Text(
                        text = uiState.error!!.userMessage(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.diffHtml.isNotBlank() -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // File stats
                        if (uiState.fileStats.isNotEmpty()) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                uiState.fileStats.forEach { stat ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stat.file,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (stat.additions > 0) {
                                            Text(
                                                text = "+${stat.additions}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = androidx.compose.ui.graphics.Color(0xFF22863A)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        if (stat.deletions > 0) {
                                            Text(
                                                text = "-${stat.deletions}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = androidx.compose.ui.graphics.Color(0xFFCB2431)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
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
                                webView.loadDataWithBaseURL(
                                    null,
                                    uiState.diffHtml,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                else -> {
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
