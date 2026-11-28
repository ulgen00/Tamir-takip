package com.cepatolyesi.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cepatolyesi.app.AppViewModel
import com.cepatolyesi.app.NavMode
import com.cepatolyesi.app.ThemeMode
import kotlinx.coroutines.launch

enum class Tab(val title: String, val short: String, val icon: ImageVector) {
    REPAIRS("Tamir", "Tamir", Icons.Filled.Build),
    CASH("Kasa", "Kasa", Icons.Filled.AccountBalanceWallet),
    SUM("Özet", "Özet", Icons.Filled.BarChart),
    IMEI("IMEI Sorgula", "IMEI", Icons.Filled.QrCodeScanner),
    SETTINGS("Ayarlar", "Ayarlar", Icons.Filled.Settings),
}

/** Açık olan alttan form: (hangi form, kayıt id'si ya da null = yeni) */
private data class SheetReq(val tx: Boolean, val id: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel, dark: Boolean) {
    val c = LocalColors.current
    var tab by rememberSaveable { mutableStateOf(Tab.REPAIRS) }
    var sheet by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.listSaver(
        save = { s -> if (s == null) emptyList() else listOf(s.tx, s.id ?: "") },
        restore = { l -> if (l.isEmpty()) null else SheetReq(l[0] as Boolean, (l[1] as String).ifEmpty { null }) },
    )) { mutableStateOf<SheetReq?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val drawerMode = vm.nav == NavMode.DRAWER

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }

    val content: @Composable () -> Unit = {
        Scaffold(
            containerColor = c.bg,
            topBar = {
                TopAppBar(
                    title = { Text(vm.shopTitle, color = c.acc, fontWeight = FontWeight.Bold, fontSize = 23.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (drawerMode) OutlinedIconButton(onClick = { scope.launch { drawer.open() } }, Modifier.padding(start = 8.dp),
                            shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, c.line)) { Icon(Icons.Filled.Menu, "Seçenekler") }
                    },
                    actions = { ThemeSwitch(dark) { vm.updateTheme(it) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg, scrolledContainerColor = c.bg),
                )
            },
            bottomBar = {
                if (!drawerMode) NavigationBar(containerColor = c.surf) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(t.short) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = c.acc, selectedTextColor = c.acc, indicatorColor = c.st[1].second, unselectedIconColor = c.mut, unselectedTextColor = c.mut))
                    }
                }
            },
            floatingActionButton = {
                if (tab == Tab.REPAIRS || tab == Tab.CASH) FloatingActionButton(onClick = { sheet = SheetReq(tab == Tab.CASH, null) }, containerColor = c.acc, contentColor = c.onAcc, shape = CircleShape) {
                    Icon(Icons.Filled.Add, "Yeni kayıt ekle")
                }
            },
        ) { padding ->
            // Sekme değişince içerik gidilen yönden kayarak gelir
            AnimatedContent(tab, label = "sekme", transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(320)) { it / 12 * dir } + fadeIn(tween(320))) togetherWith fadeOut(tween(120))
            }) { t ->
                when (t) {
                    Tab.REPAIRS -> RepairsScreen(vm, padding) { sheet = SheetReq(false, it) }
                    Tab.CASH -> CashScreen(vm, padding) { sheet = SheetReq(true, it) }
                    Tab.SUM -> SummaryScreen(vm, padding)
                    Tab.IMEI -> ImeiScreen(vm, padding)
                    Tab.SETTINGS -> SettingsScreen(vm, padding)
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer, gesturesEnabled = drawerMode,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = c.surf, modifier = Modifier.width(290.dp)) {
                Text("Seçenekler", color = c.acc, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(20.dp))
                Tab.entries.forEach { t ->
                    NavigationDrawerItem(
                        label = { Text(t.title) }, icon = { Icon(t.icon, null) }, selected = tab == t,
                        onClick = { tab = t; scope.launch { drawer.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = c.st[1].second, selectedTextColor = c.acc, selectedIconColor = c.acc, unselectedContainerColor = c.surf),
                    )
                }
            }
        },
        content = content,
    )

    sheet?.let { s ->
        if (s.tx) TxSheet(vm, s.id) { sheet = null } else RepairSheet(vm, s.id) { sheet = null }
    }
}

/** Sağ üstte güneş (açık tema) ve ay (koyu tema) */
@Composable
private fun ThemeSwitch(dark: Boolean, onSet: (ThemeMode) -> Unit) {
    val c = LocalColors.current
    Row(Modifier.padding(end = 12.dp).border(1.dp, c.line, RoundedCornerShape(99.dp)).background(c.surf, RoundedCornerShape(99.dp)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        for ((mode, icon, label) in listOf(Triple(ThemeMode.LIGHT, Icons.Filled.LightMode, "Açık tema"), Triple(ThemeMode.DARK, Icons.Filled.DarkMode, "Koyu tema"))) {
            val on = (mode == ThemeMode.DARK) == dark
            Box(Modifier.size(38.dp).background(if (on) c.acc else c.surf, CircleShape), contentAlignment = Alignment.Center) {
                IconButton(onClick = { onSet(mode) }, Modifier.size(38.dp)) { Icon(icon, label, tint = if (on) c.onAcc else c.mut, modifier = Modifier.size(20.dp)) }
            }
            if (mode == ThemeMode.LIGHT) Spacer(Modifier.width(2.dp))
        }
    }
}
