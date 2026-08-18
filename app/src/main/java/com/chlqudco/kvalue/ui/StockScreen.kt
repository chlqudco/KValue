/*
 * K-Value의 단일 세로 스크롤 화면과 상태별 카드 배치를 정의한다.
 * 화면은 StockUiState를 받아 렌더링하고 검색·새로고침·DART 실행 이벤트를 콜백으로 돌려준다.
 * Repository나 계산기를 직접 참조하지 않는 상태 기반 Composable 구조라 Preview와 UI 테스트가 가능하다.
 * testTag, contentDescription, 문자열 리소스를 사용해 자동화 테스트와 접근성을 함께 고려한다.
 */
package com.chlqudco.kvalue.ui

import androidx.compose.foundation.clickable
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
import com.chlqudco.kvalue.domain.model.HistoricalForecastResult
import com.chlqudco.kvalue.domain.model.StockAnalysis
import com.chlqudco.kvalue.domain.model.StockSearchSuggestion
import com.chlqudco.kvalue.domain.model.SupportResistanceResult
import com.chlqudco.kvalue.domain.model.MissingDataSection
import com.chlqudco.kvalue.domain.model.DataProvider
import com.chlqudco.kvalue.domain.model.DataType
import com.chlqudco.kvalue.ui.components.FinancialSummary
import com.chlqudco.kvalue.ui.components.HistoricalForecastCard
import com.chlqudco.kvalue.ui.components.PriceChart
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/*
 * 화면 최상위 Composable이다. Scaffold가 앱 바와 Snackbar 영역을 제공하고 LazyColumn이 모든 카드를 세로로 배치한다.
 * DART 실행 실패는 일회성 UI 사건이므로 영구 화면 상태 대신 이 레벨의 Snackbar로 알린다.
 */
fun StockScreen(
    state: StockUiState,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onSuggestionSelected: (StockSearchSuggestion) -> Unit,
    onRefresh: () -> Unit,
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
                    catalog = state.catalog,
                    suggestions = state.suggestions,
                    loadingQuery = (state.content as? StockContentState.Loading)?.submittedQuery,
                    onQueryChanged = onQueryChanged,
                    onSearch = onSearch,
                    onSuggestionSelected = onSuggestionSelected
                )
            }
            // sealed content 상태를 when으로 모두 처리하므로 비어 있는 본문 분기가 생기지 않는다.
            item {
                when (val content = state.content) {
                    StockContentState.Idle -> IdleCard()
                    is StockContentState.Loading -> LoadingCard()
                    is StockContentState.Success -> {
                        SuccessContent(
                            analysis = content.analysis,
                            forecast = content.forecast,
                            supportResistance = content.supportResistance,
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
/*
 * 검색 입력, 조회 버튼, 카탈로그 상태와 자동완성 목록을 하나의 카드로 묶는다.
 * 340dp보다 좁으면 입력과 버튼을 세로로 바꿔 최소 터치 영역과 텍스트 폭을 확보한다.
 */
private fun SearchSection(
    query: String,
    queryError: QueryInputError?,
    catalog: StockCatalogState,
    suggestions: StockSuggestionState,
    loadingQuery: String?,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onSuggestionSelected: (StockSearchSuggestion) -> Unit
) {
    val errorText = when (queryError) {
        QueryInputError.EMPTY -> stringResource(R.string.query_error_empty)
        QueryInputError.NO_MATCH -> stringResource(R.string.query_error_no_match)
        QueryInputError.SELECT_SUGGESTION -> {
            stringResource(R.string.query_error_select_suggestion)
        }
        null -> null
    }
    val canSearch = loadingQuery == null || loadingQuery != query.trim()
    Card(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val useVerticalLayout = maxWidth < 340.dp
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (useVerticalLayout) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                StockSearchSuggestions(
                    state = suggestions,
                    onSuggestionSelected = onSuggestionSelected
                )
                StockCatalogStatus(catalog)
            }
        }
    }
}

@Composable
// 앱 시작 프리로드의 Loading·Ready·Error를 testTag가 있는 한 줄 상태로 표시한다.
private fun StockCatalogStatus(state: StockCatalogState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stock_catalog_status"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state is StockCatalogState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
        }
        Text(
            text = when (state) {
                StockCatalogState.Loading -> stringResource(R.string.catalog_preloading)
                is StockCatalogState.Ready -> stringResource(
                    R.string.catalog_ready,
                    state.stockCount
                )
                is StockCatalogState.Error -> stringResource(R.string.catalog_error)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state is StockCatalogState.Error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
/*
 * 자동완성 sealed 상태에 따라 진행 표시, 결과 목록, 빈 결과 또는 오류 문구를 그린다.
 * 각 결과 행은 회사명·종목코드를 합친 접근성 설명과 안정적인 종목코드 testTag를 갖는다.
 */
private fun StockSearchSuggestions(
    state: StockSuggestionState,
    onSuggestionSelected: (StockSearchSuggestion) -> Unit
) {
    when (state) {
        StockSuggestionState.Hidden -> Unit
        StockSuggestionState.Loading -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = stringResource(R.string.suggestion_loading),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        is StockSuggestionState.Results -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stock_search_suggestions"),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                state.suggestions.forEach { suggestion ->
                    val description = stringResource(
                        R.string.suggestion_accessibility,
                        suggestion.companyName,
                        suggestion.stockCode
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("stock_suggestion_${suggestion.stockCode}")
                            .semantics(mergeDescendants = true) {
                                contentDescription = description
                            }
                            .clickable { onSuggestionSelected(suggestion) }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = suggestion.companyName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = suggestion.stockCode,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        StockSuggestionState.NoResults -> {
            Text(
                text = stringResource(R.string.suggestion_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is StockSuggestionState.Error -> {
            Text(
                text = stringResource(R.string.suggestion_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
// IME 검색 액션도 화면의 조회 버튼과 같은 onSearch 콜백으로 연결한다.
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
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

@Composable
// 현재 입력과 같은 종목을 이미 로딩 중이면 enabled=false가 되어 중복 조회를 막는다.
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
/*
 * 정상 조회에서 가격, 차트, 통계 전망, 재무, 공시, 출처를 제품 순서대로 조립한다.
 */
private fun SuccessContent(
    analysis: StockAnalysis,
    forecast: HistoricalForecastResult,
    supportResistance: SupportResistanceResult,
    onRefresh: () -> Unit,
    onOpenDart: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PriceSummaryCard(analysis, onRefresh)
        PriceChart(
            points = analysis.priceHistory,
            supportResistance = supportResistance
        )
        HistoricalForecastCard(forecast)
        FinancialSummary(analysis.ratios, analysis.annualFinancials)
        DartButton(onOpenDart)
        SourcesCard(analysis)
    }
}

@Composable
/*
 * 가격과 등락률을 하나의 semantics 설명으로 병합해 스크린 리더가 관련 정보를 연속해서 읽게 한다.
 * 폭이 좁으면 현재가와 새로고침 버튼을 세로로 배치하고 등락은 부호·문구·색을 함께 사용한다.
 */
private fun PriceSummaryCard(
    analysis: StockAnalysis,
    onRefresh: () -> Unit
) {
    val price = analysis.price
    val priceText = NumberFormatter.won(price.currentPrice)
    val changeText = price.changeRate?.let(NumberFormatter::signedPercentage)
        ?: stringResource(R.string.data_missing)
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
                analysis.price.changeRate != null && analysis.price.changeRate > 0.0 -> {
                    MaterialTheme.colorScheme.tertiary
                }
                analysis.price.changeRate != null && analysis.price.changeRate < 0.0 -> {
                    MaterialTheme.colorScheme.error
                }
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
// 도메인에 보존된 출처 목록과 누락 섹션을 사용자 문구로 변환하고 샘플 데이터 여부도 별도 고지한다.
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
                        dataProviderText(source.provider),
                        dataTypeText(source.dataType),
                        source.asOf
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (analysis.sources.any { it.provider == DataProvider.SAMPLE }) {
                Text(
                    text = stringResource(R.string.sample_data_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (analysis.missingData.isNotEmpty()) {
                val missingDataLabels = mapOf(
                    MissingDataSection.PRICE_HISTORY to stringResource(
                        R.string.missing_price_history
                    ),
                    MissingDataSection.FINANCIAL_RATIOS to stringResource(
                        R.string.missing_financial_ratios
                    ),
                    MissingDataSection.ANNUAL_FINANCIALS to stringResource(
                        R.string.missing_annual_financials
                    ),
                    MissingDataSection.DART to stringResource(R.string.missing_dart)
                )
                val missingSections = analysis.missingData
                    .sortedBy(MissingDataSection::ordinal)
                    .joinToString(", ") { missingDataLabels.getValue(it) }
                Text(
                    text = stringResource(R.string.partial_data_notice, missingSections),
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
private fun dataProviderText(provider: DataProvider): String = when (provider) {
    DataProvider.SAMPLE -> stringResource(R.string.provider_sample)
    DataProvider.KIS -> stringResource(R.string.provider_kis)
    DataProvider.OPEN_DART -> stringResource(R.string.provider_open_dart)
}

@Composable
private fun dataTypeText(dataType: DataType): String = when (dataType) {
    DataType.PRICE -> stringResource(R.string.data_type_price)
    DataType.ADJUSTED_DAILY_PRICE -> stringResource(R.string.data_type_adjusted_daily_price)
    DataType.FINANCIAL_RATIOS -> stringResource(R.string.data_type_financial_ratios)
    DataType.INCOME_STATEMENT -> stringResource(R.string.data_type_income_statement)
    DataType.DISCLOSURE_LINK -> stringResource(R.string.data_type_disclosure_link)
}

@Composable
// AppError 종류를 안정적인 문자열 리소스로 바꾸고 동일 종목을 다시 요청하는 버튼을 제공한다.
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
    AppError.Unknown -> stringResource(R.string.error_unknown)
}
