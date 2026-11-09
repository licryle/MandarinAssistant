package fr.berliat.hskwidget.core

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.flag_cn
import fr.berliat.hskwidget.flag_en
import fr.berliat.hskwidget.flag_es
import fr.berliat.hskwidget.flag_fr
import fr.berliat.hskwidget.locale_en
import fr.berliat.hskwidget.locale_es
import fr.berliat.hskwidget.locale_fr
import fr.berliat.hskwidget.locale_cn_hsk3
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

@kotlinx.serialization.Serializable(with = LocaleSerializer::class)
enum class Locale(
    val code: String,
    val displayName: StringResource,
    val flag: DrawableResource?
) {
    ENGLISH("en", Res.string.locale_en, Res.drawable.flag_en),
    SPANISH("es", Res.string.locale_es, Res.drawable.flag_es),
    FRENCH("fr", Res.string.locale_fr, Res.drawable.flag_fr),
    CN_HSK3("zh-CN-HSK03", Res.string.locale_cn_hsk3, Res.drawable.flag_cn);

    companion object {
        // Get enum from string code
        fun fromCode(code: String): Locale? {
            val lang = code.split("-")[0] // for now, I won't translate with variants

            return entries.firstOrNull {
                it.code == lang || it.code.split("-")[0] == lang
            }
        }

        fun getDefault(): Locale {
            return ENGLISH
        }

        /**
         * Resolves the locale to use based on the preferred one, falling back to system locale,
         * and finally to default (English).
         */
        fun resolve(preferred: Locale?): Locale {
            if (preferred != null) return preferred

            val systemCode = LocaleManager.getCurrentLocale()
            fromCode(systemCode)?.let { return it }

            return getDefault()
        }
    }
}

object LocaleSerializer : KSerializer<Locale> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Locale", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Locale) {
        encoder.encodeString(value.code)
    }

    override fun deserialize(decoder: Decoder): Locale {
        val code = decoder.decodeString()
        return Locale.fromCode(code) ?: Locale.getDefault()
    }
}