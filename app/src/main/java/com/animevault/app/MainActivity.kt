package com.animevault.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF060811)
private val Surface = Color(0xFF0D1321)
private val Surface2 = Color(0xFF151C2E)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF9AA4B2)
private val Purple = Color(0xFF8B5CF6)
private val Pink = Color(0xFFEC4899)
private val Cyan = Color(0xFF22D3EE)
private val Green = Color(0xFF34D399)

private data class StreamLink(val site: String, val title: String, val url: String, val thumbnail: String)
private data class Anime(
    val id: Int,
    val title: String,
    val romaji: String,
    val episodes: Int,
    val duration: Int,
    val score: Double,
    val year: Int,
    val status: String,
    val cover: String,
    val banner: String,
    val description: String,
    val genres: List<String>,
    val isAdult: Boolean,
    val streams: List<StreamLink> = emptyList()
)
private data class Airing(val anime: Anime, val episode: Int, val airingAt: Long)
private data class Provider(val name: String, val url: String)

private class Store(context: Context) {
    private val p = context.getSharedPreferences("animevault_v2", Context.MODE_PRIVATE)
    fun bool(key: String, def: Boolean = false) = p.getBoolean(key, def)
    fun setBool(key: String, v: Boolean) = p.edit().putBoolean(key, v).apply()
    fun int(key: String, def: Int = 0) = p.getInt(key, def)
    fun setInt(key: String, v: Int) = p.edit().putInt(key, v).apply()
    fun string(key: String, def: String = "") = p.getString(key, def) ?: def
    fun setString(key: String, v: String) = p.edit().putString(key, v).apply()
    fun long(key: String, def: Long = 0L) = p.getLong(key, def)
    fun setLong(key: String, v: Long) = p.edit().putLong(key, v).apply()
    fun list(key: String): Set<String> = p.getStringSet(key, emptySet()) ?: emptySet()
    fun setList(key: String, v: Set<String>) = p.edit().putStringSet(key, v).apply()
    fun watched(id: Int) = int("watched_$id", 0)
    fun saveWatched(id: Int, episode: Int, duration: Int, totalEpisodes: Int) {
        val current = watched(id)
        val next = maxOf(current, episode)
        val delta = if (next > current) next - current else 0
        setInt("watched_$id", next)
        if (duration > 0 && delta > 0) setLong("watchSeconds", long("watchSeconds") + delta.toLong() * duration * 60L)
        val tracked = list("allTracked").toMutableSet(); tracked.add(id.toString()); setList("allTracked", tracked)
        if (episode > 0) { val cont = list("continueAnime").toMutableSet(); cont.add(id.toString()); setList("continueAnime", cont) }
        if (totalEpisodes > 0 && next >= totalEpisodes) {
            val done = list("list_completed").toMutableSet(); done.add(id.toString()); setList("list_completed", done)
            setString("status_$id", "completed")
            val cont = list("continueAnime").toMutableSet(); cont.remove(id.toString()); setList("continueAnime", cont)
        } else {
            val watch = list("list_watchlist").toMutableSet(); watch.add(id.toString()); setList("list_watchlist", watch)
            setString("status_$id", "watching")
        }
    }
    fun addToWatchlist(id:Int){val s=list("list_watchlist").toMutableSet();s.add(id.toString());setList("list_watchlist",s);val t=list("allTracked").toMutableSet();t.add(id.toString());setList("allTracked",t)}
    fun isFav(id: Int) = list("favorites").contains(id.toString())
    fun toggleFav(id: Int) { val s=list("favorites").toMutableSet(); if(!s.add(id.toString())) s.remove(id.toString()); setList("favorites",s) }
    fun provider(i:Int)=Provider(string("p${i}n"),string("p${i}u"))
    fun saveProvider(i:Int,n:String,u:String){setString("p${i}n",n);setString("p${i}u",u)}
    fun exportJson():String{
        val o=JSONObject();o.put("streamAllow",bool("streamAllow"));o.put("matureEnabled",bool("matureEnabled"));
        val pArr=JSONArray();for(i in 1..8)pArr.put(JSONObject().put("name",string("p${i}n")).put("url",string("p${i}u")));o.put("providers",pArr);o.put("watchedEpisodes",long("watchSeconds"));return o.toString(2)
    }
    fun exportCsv():String{
        val ids=list("allTracked").mapNotNull{it.toIntOrNull()}; val sb=StringBuilder("Anime ID,Watched Episodes,Status,Favorite\n")
        ids.forEach{ id-> sb.append("$id,${watched(id)},${string("status_$id")},${isFav(id)}\n") };return sb.toString()
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); store=Store(this); setContent { AnimeVaultApp(store) } }
    fun openExternal(url:String){runCatching{startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))}.onFailure{Toast.makeText(this,"Cannot open link",Toast.LENGTH_SHORT).show()}}
    fun shareText(title:String,text:String){startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_SUBJECT,title);putExtra(Intent.EXTRA_TEXT,text)},"Export AnimeVault"))}
}

@Composable
fun AnimeVaultApp(store: Store) {
    val activity = LocalContext.current as MainActivity
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf("home") }
    var selected by remember { mutableStateOf<Anime?>(null) }

    fun go(target: String) {
        screen = target
        scope.launch { drawerState.close() }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Purple, secondary = Pink, background = Bg, surface = Surface, onBackground = TextPrimary, onSurface = TextPrimary)) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Surface) {
                Text("AnimeVault", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(20.dp))
                DrawerItem("Home", "home", screen) { go(it) }
                DrawerItem("Dashboard", "dashboard", screen) { go(it) }
                DrawerItem("Schedule", "schedule", screen) { go(it) }
                DrawerItem("Watchlist", "watchlist", screen) { go(it) }
                DrawerItem("Completed", "completed", screen) { go(it) }
                DrawerItem("Favorites", "favorites", screen) { go(it) }
                DrawerItem("Ongoing", "ongoing", screen) { go(it) }
                DrawerItem("Mature", "mature", screen) { go(it) }
                DrawerItem("Settings", "settings", screen) { go(it) }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Stream Allow", color = TextMuted, modifier = Modifier.weight(1f))
                    Switch(checked = store.bool("streamAllow"), onCheckedChange = { store.setBool("streamAllow", it) })
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                TopAppBar(
                    title = { Text("AnimeVault", color = TextPrimary, fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null, tint = TextPrimary) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Surface) {
                    NavItem("home", Icons.Default.Home, "Home", screen) { screen = "home" }
                    NavItem("search", Icons.Default.Search, "Search", screen) { screen = "search" }
                    NavItem("schedule", Icons.Default.CalendarMonth, "Schedule", screen) { screen = "schedule" }
                    NavItem("library", Icons.Default.LibraryBooks, "Library", screen) { screen = "library" }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (screen) {
                    "home" -> HomeScreen(store, { selected = it; screen = "detail" }, { screen = "search" })
                    "search" -> SearchScreen(store) { selected = it; screen = "detail" }
                    "schedule" -> ScheduleScreen(false, store) { selected = it; screen = "detail" }
                    "library" -> ListScreen(store, "Watchlist") { selected = it; screen = "detail" }
                    "dashboard" -> DashboardScreen(store, activity)
                    "watchlist" -> ListScreen(store, "Watchlist") { selected = it; screen = "detail" }
                    "completed" -> ListScreen(store, "Completed") { selected = it; screen = "detail" }
                    "favorites" -> ListScreen(store, "Favorites") { selected = it; screen = "detail" }
                    "ongoing" -> ListScreen(store, "Ongoing") { selected = it; screen = "detail" }
                    "mature" -> MatureScreen(store) { selected = it; screen = "detail" }
                    "settings" -> SettingsScreen(store, activity)
                    "detail" -> selected?.let { AnimeDetailScreen(it, store, activity) { screen = "home" } }
                }
            }
        }
    }
    }
}

@Composable fun DrawerItem(label:String,key:String,selected:String,onClick:(String)->Unit){NavigationDrawerItem(label={Text(label,color=TextPrimary)},selected=selected==key,onClick={onClick(key)},modifier=Modifier.padding(horizontal=12.dp,vertical=3.dp),colors=NavigationDrawerItemDefaults.colors(selectedContainerColor=Purple.copy(alpha=.20f),unselectedContainerColor=Color.Transparent,selectedTextColor=TextPrimary,unselectedTextColor=TextMuted))}

@Composable fun HomeScreen(store:Store,onOpen:(Anime)->Unit,onBrowse:()->Unit){var list by remember{mutableStateOf<List<Anime>>(emptyList())};LaunchedEffect(Unit){list=Api.popular(false)};LazyColumn(contentPadding=PaddingValues(bottom=80.dp)){item{HeroCard(list.firstOrNull(),onOpen,onBrowse)};item{SectionTitle("Popular Anime")};item{if(list.isEmpty())LoadingRow() else AnimeRow(list.take(12),onOpen)};item{SectionTitle("Continue Watching")};item{ContinueRow(store,onOpen)};item{SectionTitle("Trending")};item{AnimeRow(list.drop(3).take(10),onOpen)}}}

@Composable fun HeroCard(anime:Anime?,onOpen:(Anime)->Unit,onBrowse:()->Unit){Box(Modifier.padding(12.dp).fillMaxWidth().height(370.dp).clip(RoundedCornerShape(24.dp)).background(Surface)){if(anime==null){CircularProgressIndicator(Modifier.align(Alignment.Center),color=Purple)}else{NetworkImage(anime.banner.ifBlank{anime.cover},Modifier.fillMaxSize(),ContentScale.Crop);Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Bg.copy(alpha=.97f)))));Column(Modifier.align(Alignment.BottomStart).padding(20.dp)){Text(anime.title,color=TextPrimary,fontSize=28.sp,fontWeight=FontWeight.ExtraBold);Text("${anime.year.takeIf{it>0}?:"—"} • ${anime.status}",color=TextMuted);Spacer(Modifier.height(12.dp));Row{Button(onClick={onOpen(anime)},colors=ButtonDefaults.buttonColors(Purple)){Text("Details")};Spacer(Modifier.width(8.dp));OutlinedButton(onClick=onBrowse){Text("Browse")}}}}}}

@Composable fun SectionTitle(t:String){Text(t,color=TextPrimary,fontSize=21.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=16.dp,vertical=12.dp))}
@Composable fun AnimeRow(list:List<Anime>,onOpen:(Anime)->Unit){LazyRow(contentPadding=PaddingValues(horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){items(list){AnimeCard(it,onOpen)}}}
@Composable fun AnimeCard(a:Anime,onOpen:(Anime)->Unit){Column(Modifier.width(150.dp).clickable{onOpen(a)}){NetworkImage(a.cover,Modifier.width(150.dp).height(215.dp).clip(RoundedCornerShape(14.dp)),ContentScale.Crop);Text(a.title,color=TextPrimary,fontWeight=FontWeight.SemiBold,maxLines=2,modifier=Modifier.padding(top=7.dp));Text("★ ${String.format(Locale.US,"%.1f",a.score)} • ${if(a.episodes>0)a.episodes else "?"} eps",color=TextMuted,fontSize=12.sp)}}

@Composable fun ContinueRow(store:Store,onOpen:(Anime)->Unit){val ids=store.list("continueAnime").toList().mapNotNull{it.toIntOrNull()};if(ids.isEmpty()){Text("Nothing here yet. Start watching an anime from Browse.",color=TextMuted,modifier=Modifier.padding(horizontal=16.dp,vertical=6.dp))}else{LazyRow(contentPadding=PaddingValues(horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){items(ids){id->var anime by remember(id){mutableStateOf<Anime?>(null)};LaunchedEffect(id){anime=Api.media(id)};anime?.let{a->Column(Modifier.width(210.dp).clickable{onOpen(a)}){NetworkImage(a.cover,Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(14.dp)),ContentScale.Crop);Text(a.title,color=TextPrimary,fontWeight=FontWeight.Bold,maxLines=1);val w=store.watched(id);LinearProgressIndicator(progress={if(a.episodes>0)w.toFloat()/a.episodes else 0f},modifier=Modifier.fillMaxWidth());Text("$w / ${if(a.episodes>0)a.episodes else "?"}",color=TextMuted,fontSize=12.sp)}}}}}}

@Composable fun SearchScreen(store:Store,onOpen:(Anime)->Unit){var q by remember{mutableStateOf("")};var results by remember{mutableStateOf<List<Anime>>(emptyList())};var loading by remember{mutableStateOf(false)};Column(Modifier.fillMaxSize()){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(q,{q=it},modifier=Modifier.weight(1f),singleLine=true,label={Text("Search anime")});Spacer(Modifier.width(8.dp));Button(onClick={loading=true}){Text("Search")}};LaunchedEffect(loading){if(loading){results=Api.search(q);loading=false}};LazyColumn(contentPadding=PaddingValues(bottom=80.dp)){items(results){AnimeSearchRow(it,onOpen)}}}}
@Composable fun AnimeSearchRow(a:Anime,onOpen:(Anime)->Unit){Row(Modifier.fillMaxWidth().clickable{onOpen(a)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){NetworkImage(a.cover,Modifier.size(78.dp,112.dp).clip(RoundedCornerShape(10.dp)),ContentScale.Crop);Column(Modifier.padding(start=12.dp)){Text(a.title,color=TextPrimary,fontWeight=FontWeight.Bold);Text("${a.year} • ${a.status}",color=TextMuted);Text("★ ${String.format(Locale.US,"%.1f",a.score)} • ${a.episodes.takeIf{it>0}?:"?"} eps",color=Cyan,fontSize=12.sp)}}}

@Composable fun ScheduleScreen(adult:Boolean,store:Store,onOpen:(Anime)->Unit){var offset by remember{mutableStateOf(0)};var list by remember{mutableStateOf<List<Airing>>(emptyList())};LaunchedEffect(offset,adult){list=Api.schedule(offset,adult)};Column(Modifier.fillMaxSize()){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={offset-=7}){Text("← Prev 7")};Button(onClick={offset=0}){Text("Today")};Button(onClick={offset+=7}){Text("Next 7 →")}};if(list.isEmpty()){Text("No scheduled episodes in this window.",color=TextMuted,modifier=Modifier.padding(16.dp))}else LazyColumn(contentPadding=PaddingValues(bottom=80.dp)){items(list){air->Row(Modifier.fillMaxWidth().clickable{onOpen(air.anime)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){NetworkImage(air.anime.cover,Modifier.size(64.dp,88.dp).clip(RoundedCornerShape(10.dp)),ContentScale.Crop);Column(Modifier.padding(start=12.dp)){Text(SimpleDateFormat("EEE • dd MMM • HH:mm",Locale.getDefault()).format(Date(air.airingAt*1000)),color=Cyan,fontSize=12.sp);Text(air.anime.title,color=TextPrimary,fontWeight=FontWeight.Bold);Text("Episode ${air.episode}",color=TextMuted)}}}}}}

@Composable fun ListScreen(store:Store,name:String,onOpen:(Anime)->Unit){val ids=when(name){"Completed"->store.list("list_completed");"Favorites"->store.list("favorites");"Ongoing"->store.list("list_watchlist").filter{store.string("status_${it}")=="watching"}.toSet();else->store.list("list_watchlist")}.toList().mapNotNull{it.toIntOrNull()};Column(Modifier.fillMaxSize()){Text(name,color=TextPrimary,fontSize=28.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(16.dp));if(ids.isEmpty())Text("No anime here yet. Add titles from Browse.",color=TextMuted,modifier=Modifier.padding(16.dp));LazyColumn(contentPadding=PaddingValues(bottom=80.dp)){items(ids){id->var a by remember(id){mutableStateOf<Anime?>(null)};LaunchedEffect(id){a=Api.media(id)};a?.let{AnimeSearchRow(it,onOpen)}}}}}

@Composable fun DashboardScreen(store:Store,activity:MainActivity){var hours by remember{mutableStateOf(store.long("watchSeconds")/3600.0)};LaunchedEffect(Unit){hours=store.long("watchSeconds")/3600.0};val tracked=store.list("allTracked").size;val completed=store.list("list_completed").size;val fav=store.list("favorites").size;val eps=store.list("allTracked").sumOf{it.toIntOrNull()?.let{store.watched(it)}?:0};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){Text("Dashboard",color=TextPrimary,fontSize=30.sp,fontWeight=FontWeight.ExtraBold);Text("Everything you've watched, in one place.",color=TextMuted);Spacer(Modifier.height(14.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){StatCard("Watchlist",tracked.toString(),Modifier.weight(1f));StatCard("Completed",completed.toString(),Modifier.weight(1f))};Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.padding(top=10.dp)){StatCard("Episodes",eps.toString(),Modifier.weight(1f));StatCard("Watch hours",String.format(Locale.US,"%.1f",hours),Modifier.weight(1f))};SectionTitle("Watch time");Text("${String.format(Locale.US,"%.1f",hours)} hours",color=Purple,fontSize=34.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(16.dp));SectionTitle("Data & Export");Button(onClick={activity.shareText("AnimeVault CSV",store.exportCsv())}){Text("Export CSV")};Spacer(Modifier.height(8.dp));OutlinedButton(onClick={activity.shareText("AnimeVault JSON",store.exportJson())}){Text("Export JSON")}}}
@Composable fun StatCard(label:String,value:String,modifier:Modifier){Card(modifier,colors=CardDefaults.cardColors(containerColor=Surface2)){Column(Modifier.padding(14.dp)){Text(label,color=TextMuted,fontSize=12.sp);Text(value,color=TextPrimary,fontSize=24.sp,fontWeight=FontWeight.Bold)}}}

@Composable fun MatureScreen(store:Store,onOpen:(Anime)->Unit){var mode by remember{mutableStateOf("browse")};Column(Modifier.fillMaxSize()){Text("Mature",color=TextPrimary,fontSize=28.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(16.dp));Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(mode=="browse",{mode="browse"},label={Text("Browse")});FilterChip(mode=="schedule",{mode="schedule"},label={Text("Schedule")});FilterChip(mode=="watching",{mode="watching"},label={Text("Watching")});FilterChip(mode=="completed",{mode="completed"},label={Text("Completed")})};when(mode){"schedule"->ScheduleScreen(true,store,onOpen);"watching"->ListScreen(store,"Watchlist",onOpen);"completed"->ListScreen(store,"Completed",onOpen);else->{var list by remember{mutableStateOf<List<Anime>>(emptyList())};LaunchedEffect(Unit){list=Api.popular(true)};AnimeRow(list,onOpen)}}}}

@Composable fun SettingsScreen(store:Store,activity:MainActivity){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){Text("Settings",color=TextPrimary,fontSize=30.sp,fontWeight=FontWeight.ExtraBold);SettingSwitch("Stream Allow",store.bool("streamAllow")){store.setBool("streamAllow",it)};SettingSwitch("Mature Area",store.bool("matureEnabled")){store.setBool("matureEnabled",it)};SectionTitle("External Providers");Text("Saved permanently on this phone. Changes save immediately.",color=TextMuted);for(i in 1..8)ProviderEditor(store,i);Spacer(Modifier.height(12.dp));Button(onClick={activity.shareText("AnimeVault Settings",store.exportJson())}){Text("Export settings")}}}
@Composable fun SettingSwitch(label:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().background(Surface2,RoundedCornerShape(14.dp)).padding(horizontal=14.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically){Text(label,color=TextPrimary,modifier=Modifier.weight(1f));Switch(value,onChange)}}
@Composable fun ProviderEditor(store:Store,i:Int){var name by remember{mutableStateOf(store.string("p${i}n"))};var url by remember{mutableStateOf(store.string("p${i}u"))};Column(Modifier.padding(vertical=6.dp)){OutlinedTextField(name,{name=it;store.saveProvider(i,name,url)},modifier=Modifier.fillMaxWidth(),label={Text(if(i==8)"18+ Provider" else "Provider $i")});OutlinedTextField(url,{url=it;store.saveProvider(i,name,url)},modifier=Modifier.fillMaxWidth().padding(top=6.dp),label={Text("URL template")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri))}}

@Composable
fun AnimeDetailScreen(a: Anime, store: Store, activity: MainActivity, onBack: () -> Unit) {
    var showPoster by remember { mutableStateOf(false) }
    var rangeStart by remember { mutableStateOf(1) }
    val maxEp = a.episodes.coerceAtLeast(1)
    var current by remember(a.id) { mutableIntStateOf(store.watched(a.id)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(360.dp)) {
            NetworkImage(a.banner.ifBlank { a.cover }, Modifier.fillMaxSize(), ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg))))
            IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Icon(Icons.Default.ArrowBack, null, tint = TextPrimary) }
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(a.title, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("${a.year.takeIf { it > 0 } ?: "—"} • ${a.status} • ${a.episodes.takeIf { it > 0 } ?: "?"} eps", color = TextMuted)
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { store.addToWatchlist(a.id) }) { Text("Watchlist") }
            OutlinedButton(onClick = { store.toggleFav(a.id) }) { Text(if (store.isFav(a.id)) "★ Favorite" else "☆ Favorite") }
            OutlinedButton(onClick = { showPoster = true }) { Text("Poster") }
        }
        Text(stripHtml(a.description), color = TextMuted, modifier = Modifier.padding(horizontal = 16.dp))
        if (a.genres.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(a.genres) { AssistChip(onClick = {}, label = { Text(it) }) }
            }
        }
        SectionTitle("Episodes")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (start in 1..maxEp step 100) {
                FilterChip(selected = rangeStart == start, onClick = { rangeStart = start }, label = { Text("$start-${minOf(start + 99, maxEp)}") })
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.height(340.dp).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items((rangeStart..minOf(rangeStart + 99, maxEp)).toList()) { ep ->
                val watched = ep <= current
                AssistChip(
                    onClick = { store.saveWatched(a.id, ep, a.duration, a.episodes); current = store.watched(a.id) },
                    label = { Text(if (watched) "✓$ep" else ep.toString()) }
                )
            }
        }
        Text("Progress: $current / ${if (a.episodes > 0) a.episodes else "?"}", color = TextMuted, modifier = Modifier.padding(16.dp))
        if (store.bool("streamAllow")) {
            Button(
                onClick = { showWatchDialog(activity, a, maxOf(1, current), store) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pink)
            ) { Text("Watch Episode ${maxOf(1, current)}") }
        } else {
            Text("Enable Stream Allow in Settings to reveal watch links.", color = TextMuted, modifier = Modifier.padding(16.dp))
        }
        Spacer(Modifier.height(100.dp))
    }
    if (showPoster) {
        AlertDialog(
            onDismissRequest = { showPoster = false },
            confirmButton = { TextButton(onClick = { showPoster = false }) { Text("Close") } },
            text = { NetworkImage(a.cover, Modifier.fillMaxWidth().height(520.dp), ContentScale.Fit) }
        )
    }
}

fun showWatchDialog(activity: MainActivity, a: Anime, episode: Int, store: Store) {
    val dialog = android.app.AlertDialog.Builder(activity)
    val box = android.widget.LinearLayout(activity).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(24, 12, 24, 12)
    }
    if (a.streams.isNotEmpty()) {
        val header = android.widget.TextView(activity).apply {
            text = "Available external links"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 18f
            setPadding(0, 0, 0, 10)
        }
        box.addView(header)
        a.streams.distinctBy { it.url }.forEach { stream ->
            val b = android.widget.Button(activity).apply {
                text = stream.site.ifBlank { stream.title.ifBlank { "Watch" } }
                setOnClickListener { activity.openExternal(stream.url) }
            }
            box.addView(b)
        }
    }
    (1..7).map { store.provider(it) }.filter { it.url.isNotBlank() }.forEach { provider ->
        val b = android.widget.Button(activity).apply {
            text = provider.name.ifBlank { "Provider" }
            setOnClickListener { activity.openExternal(buildProviderUrl(provider.url, a.title, episode)) }
        }
        box.addView(b)
    }
    if (a.isAdult && store.bool("matureEnabled")) {
        val p = store.provider(8)
        if (p.url.isNotBlank()) {
            val b = android.widget.Button(activity).apply {
                text = p.name.ifBlank { "18+ Provider" }
                setOnClickListener { activity.openExternal(buildProviderUrl(p.url, a.title, episode)) }
            }
            box.addView(b)
        }
    }
    dialog.setTitle("Watch ${a.title} • Episode $episode")
        .setView(box)
        .setPositiveButton("I watched it") { _, _ -> store.saveWatched(a.id, episode, a.duration, a.episodes) }
        .setNegativeButton("Close", null)
        .show()
}

fun buildProviderUrl(template:String,title:String,episode:Int)=template.replace("{title}",URLEncoder.encode(title,"UTF-8")).replace("{episode}",episode.toString())

@Composable fun LoadingRow(){Row(Modifier.fillMaxWidth().padding(24.dp),horizontalArrangement=Arrangement.Center){CircularProgressIndicator(color=Purple)}}
@Composable fun NetworkImage(url:String,modifier:Modifier,contentScale:ContentScale){var bytes by remember(url){mutableStateOf<ByteArray?>(null)};LaunchedEffect(url){bytes=withContext(Dispatchers.IO){try{URL(url).openConnection().apply{connectTimeout=10000;readTimeout=15000;setRequestProperty("User-Agent","AnimeVault/2.0")}.getInputStream().use{it.readBytes()}}catch(_:Exception){null}}};val b=bytes?.let{BitmapFactory.decodeByteArray(it,0,it.size)};if(b!=null)Image(b.asImageBitmap(),null,modifier,contentScale)else Box(modifier.background(Surface2),contentAlignment=Alignment.Center){Icon(Icons.Default.Image,null,tint=TextMuted)}}

object Api{
    private fun post(query:String):JSONObject?=try{val c=URL("https://graphql.anilist.co").openConnection() as HttpURLConnection;c.requestMethod="POST";c.connectTimeout=15000;c.readTimeout=20000;c.setRequestProperty("Content-Type","application/json");c.doOutput=true;c.outputStream.use{it.write(JSONObject().put("query",query).toString().toByteArray())};val txt=(if(c.responseCode in 200..299)c.inputStream else c.errorStream).bufferedReader().readText();c.disconnect();JSONObject(txt)}catch(_:Exception){null}
    fun popular(adult:Boolean):List<Anime>{val q="query{Page(page:1,perPage:20){media(type:ANIME,sort:POPULARITY_DESC,isAdult:$adult){id title{romaji english} episodes duration averageScore seasonYear status coverImage{large} bannerImage description(asHtml:false) genres isAdult streamingEpisodes{title thumbnail url site}}}}}";return parseList(post(q))}
    fun search(term:String):List<Anime>{if(term.isBlank())return emptyList();val s=term.replace("\\","\\\\").replace("\"","\\\"");val q="query{Page(page:1,perPage:20){media(search:\"$s\",type:ANIME,isAdult:false,sort:SEARCH_MATCH){id title{romaji english} episodes duration averageScore seasonYear status coverImage{large} bannerImage description(asHtml:false) genres isAdult streamingEpisodes{title thumbnail url site}}}}";return parseList(post(q))}
    fun media(id:Int):Anime?{val q="query{Media(id:$id){id title{romaji english} episodes duration averageScore seasonYear status coverImage{large} bannerImage description(asHtml:false) genres isAdult streamingEpisodes{title thumbnail url site}}}";return post(q)?.optJSONObject("data")?.optJSONObject("Media")?.let{parseAnime(it)}}
    fun schedule(offset:Int,adult:Boolean):List<Airing>{val from=System.currentTimeMillis()/1000+offset*86400L;val to=from+7*86400L;val q="query{Page(page:1,perPage:50){airingSchedules(airingAt_greater:$from,airingAt_lesser:$to,sort:AIRING_AT){airingAt episode media{id title{romaji english} episodes duration averageScore seasonYear status coverImage{large} bannerImage description(asHtml:false) genres isAdult}}}}}";val arr=post(q)?.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("airingSchedules")?:return emptyList();return (0 until arr.length()).mapNotNull{idx->val o=arr.optJSONObject(idx)?:return@mapNotNull null;val m=o.optJSONObject("media")?:return@mapNotNull null;if(m.optBoolean("isAdult")!=adult)return@mapNotNull null;Airing(parseAnime(m),o.optInt("episode"),o.optLong("airingAt"))}}
    private fun parseList(obj:JSONObject?):List<Anime>{val arr=obj?.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")?:return emptyList();return (0 until arr.length()).map{parseAnime(arr.getJSONObject(it))}}
    private fun parseAnime(o:JSONObject):Anime{val t=o.optJSONObject("title");val title=t?.optString("english").takeUnless{it.isNullOrBlank()}?:t?.optString("romaji").orEmpty();val streams=mutableListOf<StreamLink>();val sa=o.optJSONArray("streamingEpisodes");if(sa!=null){for(i in 0 until sa.length()){val s=sa.optJSONObject(i)?:continue;streams.add(StreamLink(s.optString("site"),s.optString("title"),s.optString("url"),s.optString("thumbnail")))}};return Anime(o.optInt("id"),title,t?.optString("romaji").orEmpty(),o.optInt("episodes"),o.optInt("duration"),o.optDouble("averageScore")/10.0,o.optInt("seasonYear"),o.optString("status"),o.optJSONObject("coverImage")?.optString("large").orEmpty(),o.optString("bannerImage"),o.optString("description"),o.optJSONArray("genres")?.let{j->(0 until j.length()).map{j.optString(it)}}?:emptyList(),o.optBoolean("isAdult"),streams)}
}
fun stripHtml(s:String)=s.replace(Regex("<[^>]*>"),"").replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'").replace("&lt;","<").replace("&gt;",">").trim()
