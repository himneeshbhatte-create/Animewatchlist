package com.animevault.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : android.app.Activity() {
    private val bg = Color.rgb(9, 11, 18)
    private val panel = Color.rgb(17, 21, 34)
    private val panel2 = Color.rgb(23, 28, 43)
    private val text = Color.rgb(244, 246, 251)
    private val muted = Color.rgb(152, 162, 179)
    private val accent = Color.rgb(124, 92, 255)
    private val success = Color.rgb(70, 211, 154)
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("animevault", Context.MODE_PRIVATE) }

    data class Provider(val name: String, val url: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showApp()
    }

    private fun showApp() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bg)
        }
        setContentView(root)
        buildNavigation()
        content = FrameLayout(this).apply {
            setBackgroundColor(bg)
        }
        root.addView(content, LinearLayout.LayoutParams(0, -1, 1f))
        showHome()
    }

    private fun buildNavigation() {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(18), dp(10), dp(12))
            setBackgroundColor(panel)
        }
        val width = if (resources.configuration.screenWidthDp >= 600) dp(220) else dp(74)
        root.addView(nav, LinearLayout.LayoutParams(width, -1))

        val logo = TextView(this).apply {
            text = "🎌\nAnimeVault"
            textSize = if (width > dp(100)) 20f else 14f
            setTextColor(text)
            gravity = Gravity.CENTER
            setPadding(4, 8, 4, 20)
        }
        nav.addView(logo, LinearLayout.LayoutParams(-1, dp(75)))

        val items = listOf(
            "🏠" to "Dashboard" to ::showDashboard,
            "📅" to "Schedule" to ::showSchedule,
            "📚" to "Watchlist" to { showList("Watchlist", "watchlist") },
            "✅" to "Completed" to { showList("Completed", "completed") },
            "⭐" to "Favorites" to { showList("Favorites", "favorites") },
            "🔥" to "Ongoing" to { showList("Ongoing", "ongoing") },
            "🔒" to "Mature" to ::showMature,
            "⚙" to "Settings" to ::showSettings
        )
        items.forEach { item ->
            val b = navButton(item.first, item.second, width)
            b.setOnClickListener { item.third.invoke() }
            nav.addView(b)
        }
        val spacer = Space(this)
        nav.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        val streamText = TextView(this).apply {
            text = if (streamAllowed()) "● STREAM ON" else "○ STREAM OFF"
            textSize = 11f
            setTextColor(if (streamAllowed()) success else muted)
            gravity = Gravity.CENTER
            setPadding(4, 10, 4, 10)
            setOnClickListener { toggleStreamAllow() }
        }
        nav.addView(streamText, LinearLayout.LayoutParams(-1, dp(44)))
    }

    private fun navButton(icon: String, label: String, width: Int): TextView = TextView(this).apply {
        text = if (width > dp(100)) "$icon  $label" else icon
        textSize = if (width > dp(100)) 14f else 22f
        setTextColor(text)
        gravity = if (width > dp(100)) Gravity.CENTER_VERTICAL else Gravity.CENTER
        setPadding(8, 12, 8, 12)
        isAllCaps = false
        background = rounded(panel2, 14)
        stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, 5, 0, 5) }
    }

    private fun baseScroll(): ScrollView {
        val s = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        s.addView(col)
        content.removeAllViews()
        content.addView(s, FrameLayout.LayoutParams(-1, -1))
        return s
    }

    private fun columnOf(s: ScrollView): LinearLayout = s.getChildAt(0) as LinearLayout

    private fun showHome() {
        val s = baseScroll(); val c = columnOf(s)
        title(c, "AnimeVault")
        subtitle(c, "Popular anime first · Continue Watching below")
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Browse Anime" to ::showBrowse, "Dashboard" to ::showDashboard).forEach { pair ->
            val b = button(pair.first, accent)
            b.setOnClickListener { pair.second.invoke() }
            actions.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0,0,dp(8),0) })
        }
        c.addView(actions)
        title(c, "Popular Anime")
        val popularContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(popularContainer)
        loadPopular(popularContainer)
        title(c, "Continue Watching")
        val cw = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(cw)
        renderContinue(cw)
    }

    private fun showBrowse() {
        val s = baseScroll(); val c = columnOf(s)
        title(c, "Browse Anime")
        val search = EditText(this).apply {
            hint = "Search anime..."; setTextColor(text); setHintTextColor(muted); setPadding(dp(14), 0, dp(14), 0)
            background = rounded(panel2, 14)
        }
        c.addView(search, LinearLayout.LayoutParams(-1, dp(50)))
        val go = button("Search", accent)
        go.setOnClickListener { searchAnime(search.text.toString(), c) }
        c.addView(go)
        title(c, "Popular")
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(list); loadPopular(list)
    }

    private fun showDashboard() {
        val s = baseScroll(); val c = columnOf(s)
        title(c, "Dashboard")
        val watched = prefs.getInt("watchedEpisodes", 0)
        val hours = prefs.getLong("watchSeconds", 0L) / 3600.0
        val stats = arrayOf(
            "Anime in Watchlist" to prefs.getInt("watchlistCount", 0).toString(),
            "Completed Anime" to prefs.getInt("completedCount", 0).toString(),
            "Episodes Watched" to watched.toString(),
            "Watch Hours" to String.format(Locale.US, "%.1f h", hours)
        )
        stats.forEach { stat ->
            val card = card().apply { orientation = LinearLayout.VERTICAL }
            smallLabel(card, stat.first)
            val v = TextView(this).apply { text = stat.second; textSize = 24f; setTextColor(text) }
            card.addView(v)
            c.addView(card)
        }
        title(c, "Watch Time")
        val bar = TextView(this).apply {
            text = "█".repeat((hours.coerceAtMost(40.0) / 2).toInt()) + "  ${String.format(Locale.US, "%.1f", hours)} hours"
            textSize = 16f; setTextColor(accent); setPadding(12, 18, 12, 18); background = rounded(panel2, 14)
        }
        c.addView(bar)
        title(c, "Data & Export")
        val export = button("Export watch data", panel2)
        export.setOnClickListener { exportData() }
        c.addView(export)
    }

    private fun showSchedule() {
        val s = baseScroll(); val c = columnOf(s)
        title(c, "Schedule")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val prev = button("← Prev 7", panel2); val next = button("Next 7 →", panel2)
        row.addView(prev, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0,0,dp(6),0) })
        row.addView(next, LinearLayout.LayoutParams(0, dp(44), 1f))
        c.addView(row)
        val info = TextView(this).apply { text = "Loading airing schedule…"; setTextColor(muted); setPadding(0,12,0,12) }
        c.addView(info)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(list)
        loadSchedule(list, 0, info)
        prev.setOnClickListener { loadSchedule(list, -7, info) }
        next.setOnClickListener { loadSchedule(list, 7, info) }
    }

    private fun showList(name: String, key: String) {
        val s = baseScroll(); val c = columnOf(s)
        title(c, name)
        val count = prefs.getInt("${key}Count", 0)
        val msg = if (count == 0) "No anime here yet. Add titles from Browse." else "$count anime tracked."
        subtitle(c, msg)
        val demo = prefs.getStringSet("${key}Titles", emptySet())!!.toList()
        demo.forEach { t -> c.addView(simpleRow(t)) }
    }

    private fun showMature() {
        val s = baseScroll(); val c = columnOf(s)
        title(c, "Mature")
        subtitle(c, "Separate section. Hidden unless enabled in Settings.")
        val enabled = prefs.getBoolean("matureEnabled", false)
        if (!enabled) {
            val b = button("Enable Mature Area in Settings", panel2)
            b.setOnClickListener { showSettings() }
            c.addView(b); return
        }
        c.addView(simpleRow("Browse Mature"))
        c.addView(simpleRow("Mature Schedule"))
        c.addView(simpleRow("Mature Watching"))
        c.addView(simpleRow("Mature Completed"))
    }

    private fun showSettings() {
        val s = baseScroll(); val c = columnOf(s)
        title(c, "Settings")
        toggleRow(c, "Stream Allow", streamAllowed()) { checked -> prefs.edit().putBoolean("streamAllow", checked).apply() }
        toggleRow(c, "Mature Area", prefs.getBoolean("matureEnabled", false)) { checked -> prefs.edit().putBoolean("matureEnabled", checked).apply() }
        title(c, "External Providers")
        subtitle(c, "Saved permanently in Android app storage. They remain after closing, reopening, and restarting the phone (unless app data is cleared or the app is uninstalled).")
        for (i in 1..8) {
            val name = EditText(this).apply { hint = if (i == 8) "Provider 8 (18+)" else "Provider $i name"; setTextColor(text); setHintTextColor(muted); background = rounded(panel2, 12) }
            val url = EditText(this).apply { hint = "Provider $i URL"; setTextColor(text); setHintTextColor(muted); background = rounded(panel2, 12); inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI }
            name.setText(prefs.getString("provider${i}Name", ""))
            url.setText(prefs.getString("provider${i}Url", ""))
            val autosave = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    prefs.edit()
                        .putString("provider${i}Name", name.text.toString())
                        .putString("provider${i}Url", url.text.toString())
                        .apply()
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            name.addTextChangedListener(autosave)
            url.addTextChangedListener(autosave)
            c.addView(name, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0,0,0,6) })
            c.addView(url, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0,0,0,6) })
            val save = button("Save Provider $i", panel2)
            save.setOnClickListener {
                prefs.edit().putString("provider${i}Name", name.text.toString()).putString("provider${i}Url", url.text.toString()).apply()
                save.text = "✓ Saved permanently on this phone"
                handler.postDelayed({ save.text = "Save Provider $i" }, 1500)
            }
            c.addView(save, LinearLayout.LayoutParams(-1, dp(42)).apply { setMargins(0,0,0,14) })
        }
        title(c, "App Data")
        val backup = button("Export local settings JSON", panel2); backup.setOnClickListener { exportData() }; c.addView(backup)
    }

    private fun toggleRow(c: LinearLayout, label: String, state: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = rounded(panel2, 14); setPadding(14, 4, 8, 4) }
        val t = TextView(this).apply { text = label; textSize = 16f; setTextColor(text) }
        val sw = Switch(this).apply { isChecked = state; setOnCheckedChangeListener { _, checked -> onChange(checked) } }
        row.addView(t, LinearLayout.LayoutParams(0, dp(56), 1f)); row.addView(sw)
        c.addView(row, LinearLayout.LayoutParams(-1, dp(64)).apply { setMargins(0,0,0,10) })
    }

    private fun toggleStreamAllow() {
        val n = !streamAllowed(); prefs.edit().putBoolean("streamAllow", n).apply(); showSettings()
    }

    private fun streamAllowed() = prefs.getBoolean("streamAllow", false)

    private fun watchEpisode(title: String, episode: Int) {
        if (!streamAllowed()) {
            toast("Enable Stream Allow in Settings first")
            return
        }
        val providers = mutableListOf<Provider>()
        for (i in 1..8) {
            if (i == 8 && !prefs.getBoolean("matureEnabled", false)) continue
            val n = prefs.getString("provider${i}Name", "")?.trim().orEmpty()
            val u = prefs.getString("provider${i}Url", "")?.trim().orEmpty()
            if (u.isNotEmpty()) providers += Provider(n.ifBlank { "Provider $i" }, u)
        }
        if (providers.isEmpty()) { toast("No saved providers. Add them in Settings."); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        providers.forEach { p ->
            val b = button(p.name, panel2); b.setOnClickListener {
                val url = p.url.replace("{title}", URLEncoder.encode(title, "UTF-8")).replace("{episode}", episode.toString())
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { toast("Unable to open provider") }
            }; box.addView(b)
        }
        val dlg = android.app.AlertDialog.Builder(this).setTitle("Watch $title · Episode $episode").setView(box).setNegativeButton("Close", null).setPositiveButton("I watched it") { _, _ -> markWatched(title, episode) }.create()
        dlg.show()
    }

    private fun markWatched(title: String, episode: Int) {
        prefs.edit().putInt("watchedEpisodes", prefs.getInt("watchedEpisodes",0)+1).apply()
        toast("Episode $episode marked watched")
    }

    private fun renderContinue(c: LinearLayout) {
        val titles = prefs.getStringSet("continueTitles", emptySet())!!.toList()
        if (titles.isEmpty()) c.addView(simpleRow("No unfinished anime yet")) else titles.forEach { c.addView(simpleRow(it)) }
    }

    private fun loadPopular(container: LinearLayout) {
        val loading = simpleRow("Loading popular anime…"); container.addView(loading)
        thread {
            val q = """{ \"Page\":{\"media\":[]}}"""
            val query = "query { Page(page:1, perPage:10) { media(type:ANIME, sort:POPULARITY_DESC, isAdult:false) { id title { romaji english } episodes averageScore coverImage { large } } } }"
            val obj = graph(query)
            handler.post {
                container.removeAllViews()
                val arr = obj?.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val a = arr.getJSONObject(i); val titleObj = a.optJSONObject("title")
                    val title = titleObj?.optString("english").takeUnless { it.isNullOrBlank() } ?: titleObj?.optString("romaji").orEmpty()
                    val ep = a.optInt("episodes", 0)
                    val row = simpleRow("$title  •  ${if (ep>0) "$ep eps" else "airing"}")
                    row.setOnClickListener { showAnimeDetail(title, ep) }
                    container.addView(row)
                }
                if (arr.length()==0) container.addView(simpleRow("Could not load AniList right now"))
            }
        }
    }

    private fun searchAnime(term: String, c: LinearLayout) {
        if (term.isBlank()) return
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title(c, "Search results")
        c.addView(results)
        thread {
            val query = "query { Page(page:1, perPage:20) { media(search:\"${term.replace("\"", "") }\", type:ANIME, isAdult:false) { id title { romaji english } episodes averageScore } } }"
            val obj = graph(query)
            handler.post {
                val arr = obj?.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val a=arr.getJSONObject(i); val t=a.optJSONObject("title"); val title=t?.optString("english").takeUnless{it.isNullOrBlank()}?:t?.optString("romaji").orEmpty(); val ep=a.optInt("episodes",0)
                    val row=simpleRow("$title  •  ${if(ep>0) "$ep eps" else "airing"}"); row.setOnClickListener{showAnimeDetail(title,ep)}; results.addView(row)
                }
            }
        }
    }

    private fun showAnimeDetail(title: String, episodes: Int) {
        val s=baseScroll(); val c=columnOf(s)
        title(c,title); subtitle(c,"$episodes episodes")
        val range=1..if(episodes>0) minOf(episodes, 300) else 1
        val grid=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        c.addView(grid)
        var current=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        grid.addView(current)
        range.forEachIndexed{idx, ep ->
            if(idx>0 && idx%4==0){ current=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}; grid.addView(current)}
            val b=button("EP $ep",panel2); b.setOnClickListener{watchEpisode(title,ep)}; current.addView(b,LinearLayout.LayoutParams(0,dp(46),1f).apply{setMargins(2,2,2,2)})
        }
    }

    private fun loadSchedule(list: LinearLayout, offsetDays: Int, info: TextView) {
        info.text="Loading schedule…"; list.removeAllViews()
        thread {
            val from=(System.currentTimeMillis()/1000L)+offsetDays*86400L
            val to=from+7*86400L
            val query="query { Page(page:1, perPage:50) { airingSchedules(airingAt_greater:$from, airingAt_lesser:$to, sort:AIRING_AT) { airingAt episode media { id title { romaji english } episodes } } } }"
            val obj=graph(query)
            handler.post {
                val arr=obj?.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("airingSchedules")?:JSONArray()
                info.text="${arr.length()} scheduled episodes"
                for(i in 0 until arr.length()){
                    val a=arr.getJSONObject(i); val m=a.optJSONObject("media"); val t=m?.optJSONObject("title"); val title=t?.optString("english").takeUnless{it.isNullOrBlank()}?:t?.optString("romaji").orEmpty(); val ep=a.optInt("episode"); val whenTxt=SimpleDateFormat("EEE, dd MMM • HH:mm",Locale.getDefault()).format(Date(a.optLong("airingAt")*1000))
                    val row=simpleRow("$whenTxt\n$title · EP $ep"); row.setOnClickListener{watchEpisode(title,ep)}; list.addView(row)
                }
            }
        }
    }

    private fun graph(query: String): JSONObject? {
        return try {
            val conn=URL("https://graphql.anilist.co").openConnection() as HttpURLConnection
            conn.requestMethod="POST"; conn.setRequestProperty("Content-Type","application/json"); conn.connectTimeout=15000; conn.readTimeout=20000; conn.doOutput=true
            val body=JSONObject().put("query",query).toString(); conn.outputStream.use{it.write(body.toByteArray())}
            val input=if(conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val txt=BufferedReader(InputStreamReader(input)).use{it.readText()}; conn.disconnect(); JSONObject(txt)
        } catch(_:Exception){ null }
    }

    private fun exportData(){
        val text=JSONObject().apply{put("streamAllow",streamAllowed());put("matureEnabled",prefs.getBoolean("matureEnabled",false));put("providers",JSONArray().apply{for(i in 1..8)put(JSONObject().put("name",prefs.getString("provider${i}Name","" )).put("url",prefs.getString("provider${i}Url","")))})}.toString(2)
        val intent=Intent(Intent.ACTION_SEND).apply{type="application/json";putExtra(Intent.EXTRA_TEXT,text)};startActivity(Intent.createChooser(intent,"Export AnimeVault settings"))
    }

    private fun simpleRow(t:String):TextView=TextView(this).apply{text=t;setTextColor(text);textSize=15f;setPadding(16,16,16,16);background=rounded(panel2,14);layoutParams=LinearLayout.LayoutParams(-1,dp(62)).apply{setMargins(0,5,0,5)}}
    private fun title(c:LinearLayout,t:String){c.addView(TextView(this).apply{text=t;textSize=24f;setTextColor(text);setTypeface(null,android.graphics.Typeface.BOLD);setPadding(0,14,0,6)})}
    private fun subtitle(c:LinearLayout,t:String){c.addView(TextView(this).apply{text=t;textSize=14f;setTextColor(muted);setPadding(0,0,0,14)})}
    private fun smallLabel(c:LinearLayout,t:String){c.addView(TextView(this).apply{text=t;textSize=12f;setTextColor(muted)})}
    private fun card()=LinearLayout(this).apply{setPadding(14,12,14,12);background=rounded(panel,16);layoutParams=LinearLayout.LayoutParams(-1,dp(92)).apply{setMargins(0,6,0,6)}}
    private fun button(t:String, color:Int)=Button(this).apply{text=t;textSize=13f;setTextColor(text);background=rounded(color,14);stateListAnimator=null}
    private fun rounded(color:Int,r:Float)=android.graphics.drawable.GradientDrawable().apply{setColor(color);cornerRadius=dp(r.toInt()).toFloat()}
    private fun toast(t:String)=Toast.makeText(this,t,Toast.LENGTH_SHORT).show()
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
}
