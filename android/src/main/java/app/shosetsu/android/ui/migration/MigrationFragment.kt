package app.shosetsu.android.ui.migration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.shosetsu.android.R
import app.shosetsu.android.common.ext.viewModelDi
import app.shosetsu.android.domain.model.local.SourceMigrationState
import app.shosetsu.android.viewmodel.abstracted.AMigrationViewModel
import coil.compose.AsyncImage
import org.jsoup.Jsoup

/* This file is part of Shosetsu, distributed under the GNU GPL-3.0.
 * Original migration scaffold: Doomsdayrs, 2021.
 * Nameless: complete interactive migration screen, modified 2026-10-05.
 * Original licensing and attribution are retained; see LICENSE.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationView(novelIds: List<Int>, onBack: () -> Unit, onOpenNovel: (Int) -> Unit) {
    val viewModel: AMigrationViewModel = viewModelDi()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(novelIds) { viewModel.setNovels(novelIds) }
    fun back() { if (!viewModel.backStep()) onBack() }
    BackHandler(enabled = state.currentSourceId != null || state.migrating || state.prepared != null) { back() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.source_migration_title)) },
                navigationIcon = { IconButton(onClick = { back() }, enabled = !state.migrating) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.source_migration_back))
                } },
                actions = { if (state.novels.size > 1) Text(
                    stringResource(R.string.source_migration_completed_count, state.completed.size, state.novels.size),
                    modifier = Modifier.padding(end = 16.dp), style = MaterialTheme.typography.labelMedium,
                ) },
            )
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).imePadding(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.busy) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(state.activity, style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.error?.let { error -> item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        if (state.currentSourceId != null) Text(stringResource(R.string.source_migration_error_hint), style = MaterialTheme.typography.bodySmall)
                        if (!state.initialized) TextButton(onClick = { viewModel.setNovels(novelIds) }, enabled = !state.busy) {
                            Text(stringResource(R.string.source_migration_retry))
                        }
                    }
                }
            } }
            val outcome = state.outcome
            if (outcome != null) {
                item {
                    Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.source_migration_done), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.source_migration_done_summary, outcome.targetTitle, outcome.plan.readMatched, outcome.plan.bookmarksMatched))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(if (outcome.originalKept) R.string.source_migration_kept else R.string.source_migration_replaced))
                }
                item { Button(onClick = { onOpenNovel(outcome.targetId) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.source_migration_open))
                } }
                if (state.pending.isNotEmpty()) item { FilledTonalButton(onClick = viewModel::nextNovel, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.source_migration_next))
                } }
                item { TextButton(onClick = onBack, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.source_migration_finish))
                } }
            } else if (state.currentNovel != null) {
                if (state.novels.size > 1) item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.pending, key = { it.id }) { novel ->
                            FilterChip(selected = novel.id == state.currentNovelId, onClick = { viewModel.setWorkingOn(novel.id) },
                                enabled = !state.busy, label = { Text(novel.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)) })
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.source_migration_selected), style = MaterialTheme.typography.labelLarge)
                    NovelLine(state.currentNovel!!.title, state.currentNovel!!.sourceName, state.currentNovel!!.novel.imageURL)
                }
                when {
                    state.prepared != null -> item { ReviewMigration(state, viewModel) }
                    state.currentSourceId != null -> {
                        item { SearchMigration(state, viewModel) }
                        if (state.searched) item {
                            Text(stringResource(R.string.source_migration_results, state.searchedQuery), style = MaterialTheme.typography.titleMedium)
                            if (state.results.isEmpty() && !state.busy) Text(stringResource(R.string.source_migration_no_results), modifier = Modifier.padding(top = 8.dp))
                        }
                        items(state.results, key = { it.link }) { target ->
                            OutlinedCard(onClick = { viewModel.prepareTarget(target) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    NovelLine(target.title.ifBlank { target.link }, state.currentSource?.name ?: "", target.imageURL)
                                    Text(target.link, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (state.nextPage != null) item { TextButton(onClick = { viewModel.search(true) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.source_migration_more))
                        } }
                        item { DirectUrlMigration(state, viewModel) }
                    }
                    else -> {
                        item {
                            Text(stringResource(R.string.source_migration_choose_source), style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(value = state.sourceFilter, onValueChange = viewModel::setSourceFilter,
                                label = { Text(stringResource(R.string.source_migration_filter_sources)) }, enabled = !state.busy,
                                singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                            TextButton(onClick = viewModel::refreshSources, enabled = !state.busy) { Text(stringResource(R.string.source_migration_refresh)) }
                            if (state.sources.isEmpty() && !state.busy) Text(stringResource(R.string.source_migration_no_sources))
                        }
                        val sources = state.sources.filter { it.name.contains(state.sourceFilter, ignoreCase = true) || it.language.contains(state.sourceFilter, ignoreCase = true) }
                        if (sources.isEmpty() && state.sources.isNotEmpty()) item { Text(stringResource(R.string.source_migration_no_filter_match)) }
                        items(sources, key = { it.id }) { source ->
                            OutlinedCard(onClick = { viewModel.selectSource(source.id) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AsyncImage(model = source.imageURL, contentDescription = null, modifier = Modifier.size(36.dp))
                                    Column {
                                        Text(source.name, style = MaterialTheme.typography.titleMedium)
                                        Text(source.language, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelLine(title: String, subtitle: String, image: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage(model = image, contentDescription = null, modifier = Modifier.width(48.dp).height(64.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SearchMigration(state: SourceMigrationState, vm: AMigrationViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.source_migration_search_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.source_migration_to, state.currentSource?.name ?: ""))
        OutlinedTextField(value = state.query, onValueChange = vm::setQuery, enabled = !state.busy,
            label = { Text(stringResource(R.string.source_migration_query)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.search() }), modifier = Modifier.fillMaxWidth())
        Button(onClick = { keyboard?.hide(); vm.search() }, enabled = !state.busy && state.query.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.source_migration_search))
        }
    }
}

@Composable
private fun DirectUrlMigration(state: SourceMigrationState, vm: AMigrationViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.source_migration_url_heading), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = state.novelUrl, onValueChange = vm::setNovelUrl, enabled = !state.busy,
            label = { Text(stringResource(R.string.source_migration_url_label)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.source_migration_url_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = vm::prepareUrl, enabled = !state.busy && state.novelUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.source_migration_load_url))
        }
    }
}

@Composable
private fun ReviewMigration(state: SourceMigrationState, vm: AMigrationViewModel) {
    val prepared = state.prepared ?: return
    val plan = state.preview ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.source_migration_review_title), style = MaterialTheme.typography.titleLarge)
        NovelLine(prepared.target.title, stringResource(R.string.source_migration_to, state.currentSource?.name ?: ""), prepared.target.imageURL)
        if (prepared.target.authors.isNotEmpty()) Text(stringResource(R.string.source_migration_author, prepared.target.authors.joinToString()), style = MaterialTheme.typography.bodySmall)
        val description = remember(prepared) { Jsoup.parse(prepared.target.description).text().take(360) }
        if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall)
        Text(prepared.target.link, style = MaterialTheme.typography.bodySmall)
        if (prepared.targetAlreadyInLibrary) Text(stringResource(R.string.source_migration_target_in_library), color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        Text(stringResource(R.string.source_migration_chapter_counts, plan.sourceCount, plan.targetCount))
        Text(stringResource(R.string.source_migration_matches, plan.matches.size, plan.readMatched, plan.readingMatched, plan.bookmarksMatched))
        if (plan.unmatchedProgress.isNotEmpty()) {
            Text(stringResource(R.string.source_migration_unmatched, plan.unmatchedProgress.size), color = MaterialTheme.colorScheme.error)
            Text(plan.unmatchedProgress.take(5).joinToString("\n") { "• ${it.title}" }, style = MaterialTheme.typography.bodySmall)
        } else Text(stringResource(R.string.source_migration_no_unmatched), color = MaterialTheme.colorScheme.primary)
        OptionRow(stringResource(R.string.source_migration_by_position), state.usePosition, !state.busy, vm::setUsePosition)
        Text(stringResource(R.string.source_migration_position_warning), style = MaterialTheme.typography.bodySmall)
        if (state.usePosition) Text(stringResource(R.string.source_migration_positional_count, plan.positionalMatches), color = MaterialTheme.colorScheme.error)
        OptionRow(stringResource(R.string.source_migration_keep_original), state.keepOriginal, !state.busy && plan.unmatchedProgress.isEmpty(), vm::setKeepOriginal)
        Text(stringResource(R.string.source_migration_keep_hint), style = MaterialTheme.typography.bodySmall)
        if (prepared.downloads > 0) Text(stringResource(R.string.source_migration_download_warning, prepared.downloads), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.source_migration_preserved), style = MaterialTheme.typography.bodySmall)
        Button(onClick = vm::migrate, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.source_migration_confirm)) }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(label, modifier = Modifier.weight(1f).padding(start = 8.dp))
    }
}
