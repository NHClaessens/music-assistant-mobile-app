package io.music_assistant.client.sharedicons

import musicassistantclient.shared_icons.generated.resources.Res
import org.jetbrains.compose.resources.DrawableResource

object SharedIcons {
    private val iconMap: Map<String, DrawableResource> = mapOf(
        "homepod-mini" to Res.drawable.homepod_mini,
        "sonos" to Res.drawable.sonos,
        "mac" to Res.drawable.mac,
        "apple-tv" to Res.drawable.apple_tv,
        "google-nest" to Res.drawable.google_nest,
        "voice-pe" to Res.drawable.voice_pe,
        "wiim" to Res.drawable.wiim,
        "speaker" to Res.drawable.speaker,
        "speakers" to Res.drawable.speakers,
        "soundbar" to Res.drawable.soundbar,
        "radio" to Res.drawable.radio,
        "tv" to Res.drawable.tv,
        "monitor" to Res.drawable.monitor,
        "laptop" to Res.drawable.laptop,
        "smartphone" to Res.drawable.smartphone,
        "tablet" to Res.drawable.tablet,
        "headphones" to Res.drawable.headphones,
        "bluetooth" to Res.drawable.bluetooth,
        "airplay" to Res.drawable.airplay,
        "cast" to Res.drawable.cast,
        "car" to Res.drawable.car,
        "music" to Res.drawable.music,
        "vinyl" to Res.drawable.vinyl,
        "mic" to Res.drawable.mic,
        "volume" to Res.drawable.volume,
        "living-room" to Res.drawable.living_room,
        "bedroom" to Res.drawable.bedroom,
        "bathroom" to Res.drawable.bathroom,
        "toilet" to Res.drawable.toilet,
        "kitchen" to Res.drawable.kitchen,
        "office" to Res.drawable.office,
        "hallway" to Res.drawable.hallway,
        "garden" to Res.drawable.garden,
        "outdoor" to Res.drawable.outdoor,
        "sun" to Res.drawable.sun,
        "home" to Res.drawable.home,
        "building" to Res.drawable.building,
    )

    fun getIcon(id: String?): DrawableResource = iconMap[id] ?: Res.drawable.speaker
}