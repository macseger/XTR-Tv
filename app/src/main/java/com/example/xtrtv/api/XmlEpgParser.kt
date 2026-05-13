package com.example.xtrtv.api

import android.util.Log
import android.util.Xml
import com.example.xtrtv.data.db.EpgEntity
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

object XmlEpgParser {
    private const val TAG = "XmlEpgParser"
    
    // SimpleDateFormat is not thread-safe, but we use it sequentially here.
    // However, creating it once and reusing is better than recreating.
    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val dateFormatNoTz = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

    data class EpgResult(
        val channelMap: Map<String, String> // Display Name -> Channel ID
    )

    suspend fun parse(inputStream: InputStream, onProgramParsed: suspend (EpgEntity) -> Unit): EpgResult {
        Log.d(TAG, "Starting EPG parse")
        val channelMap = mutableMapOf<String, String>()
        
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        
        // Channel parsing state
        var currentMappingId: String? = null
        
        // Programme parsing state
        var currentChannelId: String? = null
        var currentTitle: String? = null
        var currentStart: Long = 0
        var currentStop: Long = 0
        var currentDesc: String? = null

        // Cache for date parsing to avoid repeated heavy work on same strings
        val dateCache = mutableMapOf<String, Long>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> {
                        currentMappingId = parser.getAttributeValue(null, "id")
                        if (currentMappingId != null) {
                            channelMap[currentMappingId] = currentMappingId
                        }
                    }
                    "display-name" -> {
                        if (currentMappingId != null) {
                            val displayName = parser.nextText()
                            channelMap[displayName] = currentMappingId
                        }
                    }
                    "programme" -> {
                        currentChannelId = parser.getAttributeValue(null, "channel")
                        val startAttr = parser.getAttributeValue(null, "start")
                        val stopAttr = parser.getAttributeValue(null, "stop")
                        currentStart = dateCache.getOrPut(startAttr ?: "") { parseDate(startAttr) }
                        currentStop = dateCache.getOrPut(stopAttr ?: "") { parseDate(stopAttr) }
                        
                        // Clear cache periodically if it gets too large to prevent OOM
                        if (dateCache.size > 1000) dateCache.clear()
                    }
                    "title" -> {
                        currentTitle = parser.nextText()
                    }
                    "desc" -> {
                        currentDesc = parser.nextText()
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                val name = parser.name
                if (name == "channel") {
                    currentMappingId = null
                } else if (name == "programme" && currentChannelId != null && currentTitle != null) {
                    if (currentStart > 0 && currentStop > 0) {
                        onProgramParsed(
                            EpgEntity(
                                channelId = currentChannelId,
                                title = currentTitle,
                                start = currentStart,
                                stop = currentStop,
                                description = currentDesc
                            )
                        )
                    }
                    currentChannelId = null
                    currentTitle = null
                    currentDesc = null
                }
            }
            eventType = parser.next()
        }
        Log.d(TAG, "Finished parse: ${channelMap.size} display names mapped")
        return EpgResult(channelMap)
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null || dateStr.length < 14) return 0
        
        // Manual clean up instead of Regex for speed.
        // Handles timezones like +02:00 by converting to +0200
        val cleanDate = if (dateStr.length >= 5 && dateStr[dateStr.length - 3] == ':') {
            dateStr.substring(0, dateStr.length - 3) + dateStr.substring(dateStr.length - 2)
        } else {
            dateStr
        }

        return synchronized(dateFormat) {
            try {
                dateFormat.parse(cleanDate)?.time ?: 0
            } catch (e: Exception) {
                try {
                    dateFormatNoTz.parse(cleanDate)?.time ?: 0
                } catch (e2: Exception) {
                    0
                }
            }
        }
    }
}
