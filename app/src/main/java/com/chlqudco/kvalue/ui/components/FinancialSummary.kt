/*
 * 핵심 재무비율과 최근 연도별 실적을 카드 형태로 렌더링하는 UI 컴포넌트 모음이다.
 * 도메인 숫자는 NumberFormatter를 통해 원·배·퍼센트·조/억 단위의 사용자 문자열로 바꾼다.
 * 값이 없는 항목은 0으로 보정하지 않고 공통 데이터 없음 문구를 표시한다.
 * FlowRow와 반응형 폭을 사용해 좁은 화면이나 글자 확대에서도 지표가 잘리지 않게 배치한다.
 */
package com.chlqudco.kvalue.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chlqudco.kvalue.R
import com.chlqudco.kvalue.common.NumberFormatter
import com.chlqudco.kvalue.domain.model.AnnualFinancial
import com.chlqudco.kvalue.domain.model.FinancialRatios

// 핵심 비율 카드와 연도별 실적 카드를 순서대로 묶는 공개 진입점이다.
@Composable
fun FinancialSummary(
    ratios: FinancialRatios,
    annualFinancials: List<AnnualFinancial>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RatioCard(ratios)
        AnnualFinancialCard(annualFinancials)
    }
}

// FlowRow는 가용 폭이 줄면 지표 항목을 다음 줄로 넘겨 고정 열 레이아웃의 잘림을 피한다.
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RatioCard(ratios: FinancialRatios) {
    val missing = stringResource(R.string.data_missing)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.financial_ratios_title),
                style = MaterialTheme.typography.titleMedium
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val itemWidth = (maxWidth - 12.dp) / 2
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    maxItemsInEachRow = 2
                ) {
                    RatioItem(
                        stringResource(R.string.eps),
                        ratios.eps?.let(NumberFormatter::won) ?: missing,
                        Modifier.width(itemWidth)
                    )
                    RatioItem(
                        stringResource(R.string.per),
                        ratios.per?.let(NumberFormatter::multiple) ?: missing,
                        Modifier.width(itemWidth)
                    )
                    RatioItem(
                        stringResource(R.string.pbr),
                        ratios.pbr?.let(NumberFormatter::multiple) ?: missing,
                        Modifier.width(itemWidth)
                    )
                    RatioItem(
                        stringResource(R.string.bps),
                        ratios.bps?.let(NumberFormatter::won) ?: missing,
                        Modifier.width(itemWidth)
                    )
                    RatioItem(
                        stringResource(R.string.roe),
                        ratios.roe?.let(NumberFormatter::percentage) ?: missing,
                        Modifier.width(itemWidth)
                    )
                }
            }
            ratios.reportingPeriod?.let {
                Text(
                    text = stringResource(R.string.reporting_period, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RatioItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

// 연도별 원본 Long을 조·억 단위로 포맷하고 값이 없으면 데이터 없음 문구를 사용한다.
@Composable
private fun AnnualFinancialCard(annualFinancials: List<AnnualFinancial>) {
    val missing = stringResource(R.string.data_missing)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.annual_financials_title),
                style = MaterialTheme.typography.titleMedium
            )
            if (annualFinancials.isEmpty()) {
                Text(
                    text = missing,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                annualFinancials.sortedBy { it.fiscalYear }.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.fiscal_year, item.fiscalYear),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FinancialRow(
                            stringResource(R.string.revenue),
                            item.revenue?.let(NumberFormatter::compactWon) ?: missing
                        )
                        FinancialRow(
                            stringResource(R.string.operating_income),
                            item.operatingIncome?.let(NumberFormatter::compactWon) ?: missing
                        )
                        FinancialRow(
                            stringResource(R.string.net_income),
                            item.netIncome?.let(NumberFormatter::compactWon) ?: missing
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinancialRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End
        )
    }
}
