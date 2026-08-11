package com.chlqudco.kvalue.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chlqudco.kvalue.R
import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.common.NumberFormatter
import com.chlqudco.kvalue.domain.model.StockAnalysis
import com.chlqudco.kvalue.domain.model.SupportReason
import com.chlqudco.kvalue.domain.model.SupportStatus
import com.chlqudco.kvalue.ui.components.FairValueCard
import com.chlqudco.kvalue.ui.components.FinancialSummary
import com.chlqudco.kvalue.ui.components.PriceChart
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    state: StockUiState,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onPerChanged: (PerScenario, String) -> Unit,
    onOpenDart: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dartError = stringResource(R.string.dart_open_error)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_title),
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.screen_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                SearchSection(
                    query = state.query,
                    queryError = state.queryError,
                    loadingCode = (state.content as? StockContentState.Loading)?.stockCode,
                    onQueryChanged = onQueryChanged,
                    onSearch = onSearch
                )
            }
            item {
                when (val content = state.content) {
                    StockContentState.Idle -> IdleCard()
                    is StockContentState.Loading -> LoadingCard()
                    is StockContentState.Success -> {
                        SuccessContent(
                            analysis = content.analysis,
                            state = state,
                            onRefresh = onRefresh,
                            onPerChanged = onPerChanged,
                            onOpenDart = {
                                if (!onOpenDart(content.analysis.dartUrl)) {
                                    scope.launch { snackbarHostState.showSnackbar(dartError) }
                                }
                            }
                        )
                    }
                    is StockContentState.Unsupported -> {
                        UnsupportedContent(
                            analysis = content.analysis,
                            onRefresh = onRefresh,
                            onOpenDart = {
                                if (!onOpenDart(content.analysis.dartUrl)) {
                                    scope.launch { snackbarHostState.showSnackbar(dartError) }
                                }
                            }
                        )
                    }
                    is StockContentState.Error -> ErrorCard(
                        error = content.error,
                        onRetry = onRefresh
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSection(
    query: String,
    queryError: QueryInputError?,
    loadingCode: String?,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit
) {
    val errorText = when (queryError) {
        QueryInputError.EMPTY -> stringResource(R.string.query_error_empty)
        QueryInputError.INVALID_FORMAT -> stringResource(R.string.query_error_format)
        null -> null
    }
    val canSearch = loadingCode == null || loadingCode != query.trim()
    Card(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (maxWidth < 340.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StockCodeField(
                        query = query,
                        errorText = errorText,
                        onQueryChanged = onQueryChanged,
                        onSearch = onSearch,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SearchButton(
                        enabled = canSearch,
                        onSearch = onSearch,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StockCodeField(
                        query = query,
                        errorText = errorText,
                        onQueryChanged = onQueryChanged,
                        onSearch = onSearch,
                        modifier = Modifier.weight(1f)
                    )
                    SearchButton(enabled = canSearch, onSearch = onSearch)
                }
            }
        }
    }
}

@Composable
private fun StockCodeField(
    query: String,
    errorText: String?,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier.testTag("stock_code_input"),
        label = { Text(stringResource(R.string.stock_code_label)) },
        placeholder = { Text(stringResource(R.string.stock_code_placeholder)) },
        supportingText = errorText?.let { value -> { Text(value) } },
        isError = errorText != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

@Composable
private fun SearchButton(
    enabled: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onSearch,
        modifier = modifier
            .heightIn(min = 56.dp)
            .testTag("stock_search_button"),
        enabled = enabled
    ) {
        Text(stringResource(R.string.search))
    }
}

@Composable
private fun IdleCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.idle_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.idle_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp
            )
            Text(text = stringResource(R.string.loading))
        }
    }
}

@Composable
private fun SuccessContent(
    analysis: StockAnalysis,
    state: StockUiState,
    onRefresh: () -> Unit,
    onPerChanged: (PerScenario, String) -> Unit,
    onOpenDart: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PriceSummaryCard(analysis, onRefresh)
        PriceChart(analysis.priceHistory)
        FinancialSummary(analysis.ratios, analysis.annualFinancials)
        FairValueCard(
            analysis = analysis,
            inputs = state.perInputs,
            result = state.fairValue,
            inputError = state.perInputError,
            onPerChanged = onPerChanged
        )
        DartButton(onOpenDart)
        SourcesCard(analysis)
    }
}

@Composable
private fun UnsupportedContent(
    analysis: StockAnalysis,
    onRefresh: () -> Unit,
    onOpenDart: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PriceSummaryCard(analysis, onRefresh)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.unsupported_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(text = supportReasonText(analysis.support))
            }
        }
        PriceChart(analysis.priceHistory)
        FinancialSummary(analysis.ratios, analysis.annualFinancials)
        DartButton(onOpenDart)
        SourcesCard(analysis)
    }
}

@Composable
private fun PriceSummaryCard(
    analysis: StockAnalysis,
    onRefresh: () -> Unit
) {
    val price = analysis.price
    val priceText = NumberFormatter.won(price.currentPrice)
    val changeText = NumberFormatter.signedPercentage(price.changeRate)
    val description = stringResource(
        R.string.price_accessibility,
        analysis.companyName,
        analysis.stockCode,
        priceText,
        changeText
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth < 360.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PricePrimaryInfo(analysis, priceText, changeText)
                        RefreshButton(onRefresh)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PricePrimaryInfo(analysis, priceText, changeText)
                        RefreshButton(onRefresh)
                    }
                }
            }
            Text(
                text = stringResource(
                    R.string.price_as_of,
                    NumberFormatter.dateTime(price.asOf)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun PricePrimaryInfo(
    analysis: StockAnalysis,
    priceText: String,
    changeText: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${analysis.companyName}  ${analysis.stockCode}",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = priceText,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "${stringResource(R.string.change_rate)} $changeText",
            color = when {
                analysis.price.changeRate > 0.0 -> MaterialTheme.colorScheme.tertiary
                analysis.price.changeRate < 0.0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            }
        )
    }
}

@Composable
private fun RefreshButton(onRefresh: () -> Unit) {
    TextButton(
        onClick = onRefresh,
        modifier = Modifier.testTag("stock_refresh_button")
    ) {
        Text(stringResource(R.string.refresh))
    }
}

@Composable
private fun DartButton(onOpenDart: () -> Unit) {
    Button(
        onClick = onOpenDart,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("dart_open_button")
    ) {
        Text(stringResource(R.string.dart_open))
    }
}

@Composable
private fun SourcesCard(analysis: StockAnalysis) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.sources_title),
                style = MaterialTheme.typography.titleMedium
            )
            analysis.sources.forEach { source ->
                Text(
                    text = stringResource(
                        R.string.source_row,
                        source.provider,
                        source.dataType,
                        source.asOf
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (analysis.sources.any { it.provider == "샘플 데이터" }) {
                Text(
                    text = stringResource(R.string.sample_data_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = stringResource(R.string.disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorCard(error: AppError, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.error_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = errorText(error))
            Button(
                onClick = onRetry,
                modifier = Modifier.testTag("stock_retry_button")
            ) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun errorText(error: AppError): String = when (error) {
    AppError.InvalidInput -> stringResource(R.string.query_error_format)
    AppError.StockNotFound -> stringResource(R.string.error_stock_not_found)
    AppError.NetworkUnavailable -> stringResource(R.string.error_network)
    AppError.Timeout -> stringResource(R.string.error_timeout)
    AppError.Unauthorized -> stringResource(R.string.error_unauthorized)
    AppError.RateLimited -> stringResource(R.string.error_rate_limited)
    AppError.ServiceUnavailable -> stringResource(R.string.error_service)
    is AppError.PartialData -> stringResource(R.string.error_partial)
    is AppError.UnsupportedStock -> supportReasonText(
        SupportStatus.Unsupported(error.reason)
    )
    AppError.Unknown -> stringResource(R.string.error_unknown)
}

@Composable
private fun supportReasonText(status: SupportStatus): String {
    val reason = (status as? SupportStatus.Unsupported)?.reason
        ?: return stringResource(R.string.unsupported_unknown)
    return when (reason) {
        SupportReason.NON_POSITIVE_EPS -> stringResource(R.string.unsupported_non_positive_eps)
        SupportReason.ETF_OR_ETN -> stringResource(R.string.unsupported_etf)
        SupportReason.PREFERRED_STOCK -> stringResource(R.string.unsupported_preferred)
        SupportReason.SPAC -> stringResource(R.string.unsupported_spac)
        SupportReason.REIT -> stringResource(R.string.unsupported_reit)
        SupportReason.FINANCIAL_COMPANY -> stringResource(R.string.unsupported_financial)
        SupportReason.UNKNOWN_TYPE -> stringResource(R.string.unsupported_unknown)
    }
}
