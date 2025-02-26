package eu.kanade.tachiyomi.animeextension.hi.animeWorld.extractors

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.OkHttpClient

class StreamSBExtractor(private val client: OkHttpClient) {

    private val hexArray = "0123456789ABCDEF".toCharArray()

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF

            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    fun videosFromUrl(url: String, headers: Headers, language: String): List<Video> {
        val id = url.substringAfter("e/").substringBefore(".html")
        Log.i("id", id)
        val bytes = id.toByteArray()
        Log.i("bytes", "$bytes")
        val bytesToHex = bytesToHex(bytes)
        Log.i("bytesToHex", bytesToHex)
        val master = "https://sbplay2.com/sources41/566d337678566f743674494a7c7c${bytesToHex}7c7c346b6767586d6934774855537c7c73747265616d7362/6565417268755339773461447c7c346133383438333436313335376136323337373433383634376337633465366534393338373136643732373736343735373237613763376334363733353737303533366236333463353333363534366137633763373337343732363536313664373336327c7c6b586c3163614468645a47617c7c73747265616d7362"
        Log.i("master", master)
        val json = Json.decodeFromString<JsonObject>(
            client.newCall(GET(master, headers))
                .execute().body!!.string()
        )
        Log.i("json", "$json")
        val masterUrl = json["stream_data"]!!.jsonObject["file"]!!.jsonPrimitive.content
        val masterPlaylist = client.newCall(GET(masterUrl, headers)).execute().body!!.string()
        Log.i("masterplayl", masterPlaylist)
        val videoList = mutableListOf<Video>()
        masterPlaylist.substringAfter("#EXT-X-STREAM-INF:").split("#EXT-X-STREAM-INF:").forEach {
            val quality = "$language :" + it.substringAfter("RESOLUTION=").substringAfter("x").substringBefore(",") + "p"
            val videoUrl = it.substringAfter("\n").substringBefore("\n")
            videoList.add(Video(videoUrl, quality, videoUrl, null, headers))
        }
        return videoList
    }
}
