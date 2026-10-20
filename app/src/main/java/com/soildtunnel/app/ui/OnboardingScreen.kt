package com.soildtunnel.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.soildtunnel.app.R
import com.soildtunnel.app.ui.components.AmbientBackground
import com.soildtunnel.app.ui.components.OnboardingHero
import com.soildtunnel.app.ui.components.OnboardingProgress
import com.soildtunnel.app.ui.theme.AuraIndigo
import com.soildtunnel.app.ui.theme.EdgeNeon
import com.soildtunnel.app.ui.theme.NeonCyan
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AmbientBackground(
            accent = NeonCyan,
            active = false,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Brush.linearGradient(listOf(NeonCyan, AuraIndigo)))
                        .border(1.dp, EdgeNeon, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "SoildTunnel",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onFinished) {
                    Text(
                        stringResource(R.string.onboarding_skip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                OnboardingPage(page = page, pagerState = pagerState)
            }

            OnboardingProgress(
                page = pagerState.currentPage,
                pages = 3,
                modifier = Modifier.padding(bottom = 18.dp),
            )

            OnboardingCta(
                isLast = pagerState.currentPage >= 2,
                onClick = {
                    if (pagerState.currentPage < 2) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onFinished()
                    }
                },
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingPage(
    page: Int,
    pagerState: PagerState,
) {
    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
    val shift = (offset.coerceIn(-1f, 1f) * 60f).dp
    val fade by animateFloatAsState(
        targetValue = (1f - abs(offset).coerceAtMost(1f)).coerceIn(0.25f, 1f),
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "pageFade",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(196.dp)
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            OnboardingHero(page = page, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(
                when (page) {
                    0 -> R.string.onboarding_title_1
                    1 -> R.string.onboarding_title_2
                    else -> R.string.onboarding_title_3
                },
            ),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(
                when (page) {
                    0 -> R.string.onboarding_body_1
                    1 -> R.string.onboarding_body_2
                    else -> R.string.onboarding_body_3
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun OnboardingCta(
    isLast: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.horizontalGradient(
                    listOf(NeonCyan, AuraIndigo),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (isLast) R.string.onboarding_start else R.string.onboarding_next,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
