package com.riedel.borderline.data.place

import android.content.res.AssetManager
import org.json.JSONArray

/**
 * The immutable list of places, read from the bundled asset.
 *
 * This is reference data, not user data, so it is deliberately NOT in Room: it
 * ships with the APK, never changes at runtime, and is small enough (252 entries,
 * ~50 kB) that keeping it in memory beats a database round trip per map tap. It
 * also means the app has a full world map on first launch with no network.
 *
 * Parsed with org.json rather than kotlinx.serialization: it is in the platform,
 * so it costs no dependency and no extra compiler plugin for one flat array.
 */
class PlaceRegistry private constructor(val places: List<Place>) {

    private val byCode: Map<String, Place> = places.associateBy { it.code }

    operator fun get(code: String): Place? = byCode[code]

    val codes: Set<String> get() = byCode.keys

    companion object {
        const val ASSET_NAME = "places.json"

        fun load(assets: AssetManager): PlaceRegistry {
            val json = assets.open(ASSET_NAME).use { it.readBytes().toString(Charsets.UTF_8) }
            return parse(json)
        }

        /** Split out from [load] so unit tests can feed it a literal. */
        fun parse(json: String): PlaceRegistry {
            val array = JSONArray(json)
            val places = ArrayList<Place>(array.length())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val bbox = o.getJSONArray("bbox")
                places += Place(
                    code = o.getString("code"),
                    name = o.getString("name"),
                    continent = o.getString("continent"),
                    region = o.getString("region"),
                    sovereign = o.getString("sovereign"),
                    uninhabited = o.getBoolean("uninhabited"),
                    unMember = o.getBoolean("unMember"),
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                    bounds = Bounds(
                        west = bbox.getDouble(0),
                        south = bbox.getDouble(1),
                        east = bbox.getDouble(2),
                        north = bbox.getDouble(3),
                    ),
                    areaKm2 = o.getLong("areaKm2"),
                    popEst = o.getLong("popEst"),
                )
            }
            require(places.isNotEmpty()) { "$ASSET_NAME is empty" }
            return PlaceRegistry(places)
        }
    }
}
