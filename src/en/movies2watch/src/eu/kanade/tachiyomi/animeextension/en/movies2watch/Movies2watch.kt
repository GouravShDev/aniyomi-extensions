package eu.kanade.tachiyomi.animeextension.en.movies2watch

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.lang.Exception

class Movies2watch : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Movies2watch"

    override val baseUrl = "https://movies2watch.cc/"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder()
            .add("Referer", "https://movies2watch.cc/")
    }

    // Popular Anime

    override fun popularAnimeSelector(): String = "div#trending-movies div.film-poster,div#trending-tv div.film-poster"
//    override fun popularAnimeSelector(): String = "div#trending-tv div.film-poster"

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/home")

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(element.select("a").attr("href").replace("watch", "anime").substringBefore("-episode"))
        anime.thumbnail_url = element.select("img").attr("data-src")
        anime.title = element.select("a").attr("title")
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = "ul.pagination li.page-item a[rel=next]"

    // episodes

    override fun episodeListSelector() = throw Exception("not used")

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val episodeList = mutableListOf<SEpisode>()
        val infoElement = document.select("div.detail_page-watch")
        val id = infoElement.attr("data-id")
        val dataType = infoElement.attr("data-type") // Tv = 2 or movie = 1
        if (dataType == "2") {
            val seasonUrl = "https://movies2watch.cc/ajax/v2/tv/seasons/$id"
            val seasonsHtml = client.newCall(
                GET(
                    seasonUrl,
                    headers = Headers.headersOf("Referer", document.location())
                )
            ).execute().asJsoup()
            val seasonsElements = seasonsHtml.select("a.dropdown-item.ss-item")
            seasonsElements.forEach {
                val seasonEpList = parseEpisodesFromSeries(it)
                episodeList.addAll(seasonEpList)
            }
        } else {
            val movieUrl = "https://movies2watch.cc/ajax/movie/episodes/$id"
            val episode = SEpisode.create()
            episode.name = document.select("h2.heading-name").text()
            episode.episode_number = 1F
            episode.setUrlWithoutDomain(movieUrl)
            episodeList.add(episode)
        }
        return episodeList
    }

    override fun episodeFromElement(element: Element): SEpisode = throw Exception("not used")

    private fun parseEpisodesFromSeries(element: Element): List<SEpisode> {
        val seasonId = element.attr("data-id")
        val seasonName = element.text()
        val episodesUrl = "https://movies2watch.cc/ajax/v2/season/episodes/$seasonId"
        val episodesHtml = client.newCall(
            GET(
                episodesUrl,
            )
        ).execute().asJsoup()
        val episodeElements = episodesHtml.select("div.eps-item")
        return episodeElements.map { episodeFromElement(it, seasonName) }
    }

    private fun episodeFromElement(element: Element, seasonName: String): SEpisode {
        val episodeId = element.attr("data-id")
        val episode = SEpisode.create()
        val epNum = element.select("div.episode-number").text()
        val epName = element.select("h3.film-name a").text()
        episode.name = "$seasonName $epNum $epName"
        episode.setUrlWithoutDomain("https://movies2watch.cc/ajax/v2/episode/servers/$episodeId")
        return episode
    }

    private fun getNumberFromEpsString(epsStr: String): String {
        return epsStr.filter { it.isDigit() }
    }

    // Video Extractor

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val referer = response.request.url.encodedPath
        val newHeaders = Headers.headersOf("referer", baseUrl + referer)
        val iframe = document.selectFirst("div#servers-list ul.nav li a:contains(VidStream)").attr("data-embed")
        val getSKey = client.newCall(GET(iframe, newHeaders)).execute().body!!.string() // .asJsoup()
        val sKey = getSKey.substringAfter("window.skey = '").substringBefore("'")
        val apiHeaders = headers.newBuilder()
            .set("referer1", "$iframe")
            .build()
        val apiLink = iframe.replace("/e/", "/info/") + "&skey=" + sKey
        val iframeResponse = client.newCall(GET(apiLink, apiHeaders))
            .execute().asJsoup()
        return videosFromElement(iframeResponse)
    }

    override fun videoListSelector() = throw Exception("not used")

    private fun videosFromElement(element: Element): List<Video> {
//        val masterUrl = element.text().substringAfterLast("file\":\"").substringBeforeLast("\"}").replace("\\/", "/")
        val masterUrl = "https://e-3.foximage.net/_v5/44c86c0c8530769fe875616e77d15642911403beb91339f8a4b6154c658a5a07d7119ef35e8de75b926feea2e3dec054c1816d60a1ed21e4e78dc70f26b9a9ff109ee1901d0645a5adea3e838e50ae3c16a68de55d83c893f2c87e54ca4e07ce708b744f94a236d0477b818b1866152c5412169bf479019d5fd84a059d5d4276/playlist.m3u8"
        val masterPlaylist = client.newCall(GET(masterUrl)).execute().body!!.string()
        val videoList = mutableListOf<Video>()
        masterPlaylist.substringAfter("#EXT-X-STREAM-INF:").split("#EXT-X-STREAM-INF:").forEach {
            val quality = it.substringAfter("RESOLUTION=").substringAfter("x").substringBefore("hls").replace("\n", "") + "p"
            val videoUrl = masterUrl.substringBeforeLast("/") + "/" + it.substringAfter("\n").substringBefore("\n")
            videoList.add(Video(videoUrl, quality, videoUrl, null))
        }
        return videoList
    }

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString("preferred_quality", null)
        if (quality != null) {
            val newList = mutableListOf<Video>()
            var preferred = 0
            for (video in this) {
                if (video.quality.contains(quality)) {
                    newList.add(preferred, video)
                    preferred++
                } else {
                    newList.add(video)
                }
            }
            return newList
        }
        return this
    }

    override fun videoFromElement(element: Element) = throw Exception("not used")

    override fun videoUrlParse(document: Document) = throw Exception("not used")

    // Search

    override fun searchAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(element.select("a").attr("href").replace("watch", "anime").substringBefore("-episode"))
        anime.thumbnail_url = element.select("img").attr("data-src")
        anime.title = element.select("a").attr("title")
        return anime
    }

    override fun searchAnimeNextPageSelector(): String = "ul.pagination li.page-item a[rel=next]"

    override fun searchAnimeSelector(): String = "div.film-poster"

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = GET("$baseUrl/search/?keyword=$query&page=$page")

    // Details

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.thumbnail_url = document.select("img.film-poster-img").first().attr("src")
        anime.title = document.select("h2.heading-name").text()
        anime.genre = document.select("div.row-line:contains(Genre) a").joinToString(", ") { it.text() }
        anime.description = document.select("div.description").text()
        anime.author = document.select("div.row-line:contains(Production) a").joinToString(", ") { it.text() }
        return anime
    }

    // Latest

    override fun latestUpdatesSelector(): String = "div.film-poster"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/anime-list/recently-updated?page=$page")

    override fun latestUpdatesFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(element.select("a").attr("href").replace("watch", "anime").substringBefore("-episode"))
        anime.thumbnail_url = element.select("img").attr("data-src")
        anime.title = element.select("a").attr("title")
        return anime
    }

    override fun latestUpdatesNextPageSelector(): String = "ul.pagination li.page-item a[rel=next]"

    // Settings

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val videoQualityPref = ListPreference(screen.context).apply {
            key = "preferred_quality"
            title = "Preferred quality"
            entries = arrayOf("1080p", "720p", "480p", "360p")
            entryValues = arrayOf("1080", "720", "480", "360")
            setDefaultValue("1080")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }
        screen.addPreference(videoQualityPref)
    }
}
