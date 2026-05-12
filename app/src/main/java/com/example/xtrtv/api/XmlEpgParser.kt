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
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        
        // Channel parsing state
        var currentMappingId: String? = null
        
        // Programme parsing state
        var currentChannelId: String? = null
        var currentTitle: String? = null
        var currentStart: Long = 0
        var currentStop: Long = 0
        var currentDesc: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
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
                            currentStart = parseDate(parser.getAttributeValue(null, "start"))
                            currentStop = parseDate(parser.getAttributeValue(null, "stop"))
                        }
                        "title" -> {
                            currentTitle = parser.nextText()
                        }
                        "desc" -> {
                            currentDesc = parser.nextText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
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
            }
            eventType = parser.next()
        }
        Log.d(TAG, "Finished parse: ${channelMap.size} display names mapped")
        return EpgResult(channelMap)
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null) return 0
        // Remove colon from timezone offset if present (e.g., +02:00 -> +0200)
        val cleanDate = dateStr.trim().replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
        return try {
            dateFormat.parse(cleanDate)?.time ?: 0
        } catch (e: Exception) {
            try {
                dateFormatNoTz.parse(cleanDate)?.time ?: 0
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to parse date: $dateStr")
                0
            }
        }
    }
}
