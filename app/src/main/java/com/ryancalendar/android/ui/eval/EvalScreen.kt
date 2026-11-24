package com.ryancalendar.android.ui.eval

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryancalendar.android.domain.eval.EvalEnums
import com.ryancalendar.android.ui.theme.AppColors
import kotlinx.coroutines.launch

/** Values carried into the project editor when creating a record from an existing project. */
data class ProjectPrefill(
    val name: String = "",
    val level: String = "",
    val role: String = "",
    val stage: String = "",
    val date: String = ""
)

private val PAGE_TITLES = listOf("项目", "费用", "天数", "差旅")
private const val PAGE_PROJECTS = 0
private const val PAGE_TRAVELS = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvalScreen(
    viewModel: EvalViewModel,
    onOpenProject: (String) -> Unit,
    onNewProject: (ProjectPrefill?) -> Unit,
    onOpenTravel: (String) -> Unit,
    onNewTravel: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_TITLES.size })
    val scope = rememberCoroutineScope()
    val selectedProjects by viewModel.selectedProjectIds.collectAsState()
    val selectedTravels by viewModel.selectedTravelIds.collectAsState()
    val projectUi by viewModel.projectListUi.collectAsState()
    val travelUi by viewModel.travelListUi.collectAsState()
    val isMutating by viewModel.isMutating.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()

    val page = pagerState.currentPage
    val projectSelecting = page == PAGE_PROJECTS && selectedProjects.isNotEmpty()
    val travelSelecting = page == PAGE_TRAVELS && selectedTravels.isNotEmpty()
    val selecting = projectSelecting || travelSelecting

    // First open after install or login: fetch instead of showing an empty cache.
    LaunchedEffect(snapshot.loaded) {
        if (!snapshot.loaded) viewModel.refresh()
    }

    // Selection belongs to one page; leaving the page drops it.
    LaunchedEffect(page) {
        if (page != PAGE_PROJECTS) viewModel.clearProjectSelection()
        if (page != PAGE_TRAVELS) viewModel.clearTravelSelection()
    }

    BackHandler(enabled = selecting) {
        viewModel.clearProjectSelection()
        viewModel.clearTravelSelection()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.CanvasBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selecting) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.PrimarySoft),
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.clearProjectSelection()
                            viewModel.clearTravelSelection()
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "退出多选")
                        }
                    },
                    title = {
                        val text = if (projectSelecting) {
                            "已选 ${projectUi.selectedProjectCount} 个项目 · ${projectUi.selectedRecordCount} 条"
                        } else {
                            "已选 ${selectedTravels.size} 笔"
                        }
                        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    },
                    actions = {
                        IconButton(onClick = {
                            if (projectSelecting) viewModel.selectProjects(projectUi.selectableIds)
                            else viewModel.selectTravels(travelUi.filteredIds)
                        }) {
                            Icon(Icons.Outlined.DoneAll, contentDescription = "全选当前列表")
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface),
                    title = { Text("评估项目", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    actions = {
                        if (isMutating) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 16.dp).size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                )
            }
            PrimaryTabRow(
                selectedTabIndex = page,
                containerColor = AppColors.Surface
            ) {
                PAGE_TITLES.forEachIndexed { idx, title ->
                    Tab(
                        selected = page == idx,
                        onClick = { scope.launch { pagerState.animateScrollToPage(idx) } },
                        text = { Text(title, fontSize = 14.sp, fontWeight = if (page == idx) FontWeight.SemiBold else FontWeight.Normal) }
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                // Swiping pages while multi-selecting would silently drop the selection.
                userScrollEnabled = !selecting,
                beyondViewportPageCount = 0
            ) { p ->
                when (p) {
                    PAGE_PROJECTS -> ProjectsPage(viewModel, onOpenProject, onNewProject)
                    1 -> FeeStatsPage(viewModel, onOpenProject)
                    2 -> DaysStatsPage(viewModel, onOpenProject)
                    else -> TravelsPage(viewModel, onOpenTravel, onNewTravel)
                }
            }
        }

        when {
            projectSelecting -> Box(Modifier.align(Alignment.BottomCenter)) {
                BatchActionBar(info = "批量修改所选 ${projectUi.selectedRecordCount} 条记录") {
                    BatchMenuButton(
                        label = "项目状态",
                        options = EvalEnums.PROJECT_STATUSES,
                        tone = EvalTones::forStatus,
                        enabled = !isMutating,
                        modifier = Modifier.weight(1f)
                    ) { viewModel.batchSetProjects(status = it, settle = null) }
                    BatchMenuButton(
                        label = "费用状态",
                        options = EvalEnums.SETTLE_STATUSES,
                        tone = EvalTones::forSettle,
                        enabled = !isMutating,
                        modifier = Modifier.weight(1f)
                    ) { viewModel.batchSetProjects(status = null, settle = it) }
                }
            }

            travelSelecting -> Box(Modifier.align(Alignment.BottomCenter)) {
                BatchActionBar(
                    info = "已选 ${travelUi.totals.count} 笔 · 合计 ${money(travelUi.totals.total)}，报销状态设为："
                ) {
                    EvalEnums.REIMB_STATUSES.forEach { r ->
                        OutlinedButton(
                            onClick = { viewModel.batchSetTravelReimb(r) },
                            enabled = !isMutating,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(r, fontSize = 14.sp, color = EvalTones.forReimb(r).fg)
                        }
                    }
                }
            }

            page == PAGE_PROJECTS -> ExtendedFloatingActionButton(
                onClick = { onNewProject(null) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("新建项目") },
                containerColor = AppColors.Primary,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )

            page == PAGE_TRAVELS -> ExtendedFloatingActionButton(
                onClick = onNewTravel,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("记一笔") },
                containerColor = AppColors.Primary,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }
}
