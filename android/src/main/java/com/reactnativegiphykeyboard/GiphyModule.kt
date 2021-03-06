package com.reactnativegiphykeyboard

import android.util.Log
import android.content.res.Resources
import com.facebook.react.ReactActivity
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import com.giphy.sdk.core.models.Media
import com.giphy.sdk.core.models.enums.RenditionType
import com.giphy.sdk.core.models.enums.RatingType
import com.giphy.sdk.ui.GPHContentType
import com.giphy.sdk.ui.GPHSettings
import com.giphy.sdk.ui.Giphy
import com.giphy.sdk.ui.themes.GPHTheme
import com.giphy.sdk.ui.themes.GridType
import com.giphy.sdk.ui.utils.imageWithRenditionType
import com.giphy.sdk.ui.views.GiphyDialogFragment
import androidx.appcompat.app.AppCompatActivity;

/** Events */
private const val MEDIA_SELECTED_EVENT = "mediaSelected"
private const val GIPHY_DISMISSED_EVENT = "giphyDismissed"

/** Default Options */
private const val DEFAULT_RENDITION = "original"
private const val DEFAULT_FILE_TYPE = "mp4"
private const val DEFAULT_GRID_TYPE = "waterfall"

/** Extensions **/
fun ReadableMap.getStringSafe(name: String): String? = if (hasKey(name)) getString(name) else null

fun ReadableMap.getArrayList(name: String): ArrayList<Any> =
        if (hasKey(name)) getArray(name)!!.toArrayList()
        else arrayListOf()

fun ReactApplicationContext.sendEvent(event: String, data: Any? = null) {
    getJSModule(RCTDeviceEventEmitter::class.java).emit(event, data)
}

fun getGPHContentType(value: String): GPHContentType? = when (value) {
    "gifs" -> GPHContentType.gif
    "stickers" -> GPHContentType.sticker
    "emoji" -> GPHContentType.emoji
    "text" -> GPHContentType.text
    else -> null
}

class GiphyModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private var giphyDialog: GiphyDialogFragment? = null

    override fun initialize() {
        super.initialize()

        val packageName = reactContext.packageName
        val apiKeyResId = reactContext.resources.getIdentifier("giphy_api_key", "string", packageName)
        val verModeResId = reactContext.resources.getIdentifier("giphy_verification_mode", "bool", packageName)
        val apiKey = reactContext.getString(apiKeyResId)
        val verificationMode = try {
            reactContext.resources.getBoolean(verModeResId)
        } catch (e: Resources.NotFoundException) {
            false
        }

        Giphy.configure(reactContext, apiKey, verificationMode)
    }

    override fun getName(): String = "RNGiphyKeyboard"

    @ReactMethod
    fun openGiphy(options: ReadableMap) {
        val activity = reactContext.currentActivity as? AppCompatActivity ?: return

        // Create new dialog instance and assign it
        giphyDialog = with(options) {
            // Get settings from JS call
            val settings = GPHSettings().apply {
                mediaTypeConfig = getArrayList("mediaTypes")
                        .filterIsInstance<String>()
                        .mapNotNull(::getGPHContentType)
                        .toTypedArray()
                        .ifEmpty { GPHContentType.values() }
                gridType = getStringSafe("gridType").let { gridType ->
                    GridType.valueOf(gridType ?: DEFAULT_GRID_TYPE)
                }
                theme = when (getStringSafe("theme")) {
                    "dark" -> GPHTheme.Dark
                    else -> GPHTheme.Light
                }
            }
            // set rating
            settings.rating = RatingType.g

            // Create instance
            GiphyDialogFragment.newInstance(settings).apply {
                val rendition = getStringSafe("rendition") ?: DEFAULT_RENDITION
                val fileType = getStringSafe("fileType") ?: DEFAULT_FILE_TYPE

                gifSelectionListener = getGifListener(rendition, fileType)
            }
        }

        // Present the dialog
        giphyDialog?.show(activity.supportFragmentManager, "giphy_dialog")
    }

    @ReactMethod
    fun dismissGiphy() {
        giphyDialog?.dismiss()
    }

    private fun getGifListener(
            rendition: String = DEFAULT_RENDITION,
            fileType: String = DEFAULT_FILE_TYPE
    ) = object : GiphyDialogFragment.GifSelectionListener {

        override fun onGifSelected(
            media: Media, 
            searchTerm: String?,
            selectedContentType: GPHContentType
        ) {
            val image = media.imageWithRenditionType(RenditionType.valueOf(rendition)) ?: return
            val aspectRatio = image.width / image.height.toDouble()
            val url = when (fileType) {
                "mp4" -> image.mp4Url
                "gif" -> image.gifUrl
                "webp" -> image.webPUrl
                else -> ""
            }
            val body = Arguments.createMap().apply {
                putString("url", url)
                putString("url", url)
                putInt("width", image.width)
                putInt("height", image.height)
                putDouble("aspectRatio", aspectRatio)
            }

            reactContext.sendEvent(MEDIA_SELECTED_EVENT, body)
        }

        override fun onDismissed(selectedContentType: GPHContentType) {
            giphyDialog = null

            reactContext.sendEvent(GIPHY_DISMISSED_EVENT)
        }

        override fun didSearchTerm(term: String) {
            Log.d( "arcade","didSearchTerm $term")
        }
    }
}
