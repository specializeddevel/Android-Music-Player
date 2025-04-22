package com.raulburgosmurray.musicplayer


    import android.content.Context
    import android.graphics.Bitmap
    import android.graphics.BitmapFactory
    import android.graphics.Color
    import android.util.Log
    import androidx.core.graphics.ColorUtils
    import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.musicListPA
    import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.songPosition
    import java.io.File
    import java.lang.Math.pow
    import java.security.MessageDigest
    import kotlin.math.pow

    object ColorUtilsImproved {

        /**
         * Obtiene el color dominante de una imagen, priorizando colores vibrantes.
         * @param bitmap La imagen a analizar.
         * @param sampleSize Reduce el tamaño de la imagen para un análisis más rápido.
         * @param vibrantThreshold Umbral para considerar un color como "vibrante".
         * @return El color dominante de la imagen.
         */
        fun getDominantColor(bitmap: Bitmap, sampleSize: Int = 10, vibrantThreshold: Float = 0.2f): Int {
            val scaledWidth = bitmap.width / sampleSize
            val scaledHeight = bitmap.height / sampleSize
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false)
            val pixelCount = scaledWidth * scaledHeight
            val pixels = IntArray(pixelCount)
            scaledBitmap.getPixels(pixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)
            scaledBitmap.recycle()

            val colorCounts = mutableMapOf<Int, Int>()
            var dominantColor = pixels[0]
            var maxCount = 0

            for (pixel in pixels) {
                // Ignora píxeles transparentes
                if (Color.alpha(pixel) < 50) continue

                // Redondea el color para agrupar tonos similares
                val roundedColor = roundColor(pixel)

                val count = colorCounts.getOrDefault(roundedColor, 0) + 1
                colorCounts[roundedColor] = count

                if (count > maxCount) {
                    maxCount = count
                    dominantColor = roundedColor
                }
            }

            // Intenta priorizar colores vibrantes
            val vibrantColor = colorCounts.entries.maxByOrNull {
                val color = it.key
                val hsl = FloatArray(3)
                ColorUtils.RGBToHSL(Color.red(color), Color.green(color), Color.blue(color), hsl)
                if (hsl[1] >= vibrantThreshold) { // Usa la saturación como medida de vibrancia
                    it.value
                } else {
                    0 // No es vibrante
                }
            }?.key

            return vibrantColor ?: dominantColor
        }

        /**
         * Redondea los componentes de color para agrupar tonos similares.
         * @param color El color a redondear.
         * @param tolerance La tolerancia para agrupar colores.
         * @return El color redondeado.
         */
        private fun roundColor(color: Int, tolerance: Int = 15): Int {
            val a = Color.alpha(color)
            val r = (Color.red(color) / tolerance) * tolerance
            val g = (Color.green(color) / tolerance) * tolerance
            val b = (Color.blue(color) / tolerance) * tolerance
            return Color.argb(a, r, g, b)
        }

        /**
         * Obtiene un color intermedio entre dos colores.
         * @param color1 El primer color.
         * @param color2 El segundo color.
         * @param ratio La proporción de color1 en el color resultante (0.0 a 1.0).
         * @return El color intermedio.
         */
        fun getIntermediateColor(color1: Int, color2: Int, ratio: Float = 0.5f): Int {
            val clampedRatio = ratio.coerceIn(0f, 1f)
            val a1 = Color.alpha(color1)
            val r1 = Color.red(color1)
            val g1 = Color.green(color1)
            val b1 = Color.blue(color1)
            val a2 = Color.alpha(color2)
            val r2 = Color.red(color2)
            val g2 = Color.green(color2)
            val b2 = Color.blue(color2)

            val a = (a1 + (a2 - a1) * clampedRatio).toInt()
            val r = (r1 + (r2 - r1) * clampedRatio).toInt()
            val g = (g1 + (g2 - g1) * clampedRatio).toInt()
            val b = (b1 + (b2 - b1) * clampedRatio).toInt()

            return Color.argb(a, r, g, b)
        }

        /**
         * Obtiene un color derivado del color dominante, ajustando la luminosidad.
         * @param dominantColor El color dominante.
         * @param luminosityFactor Factor para ajustar la luminosidad (0.0 a 1.0).
         *   Valores menores a 1.0 oscurecen el color, valores mayores a 1.0 lo iluminan.
         * @return El color derivado.
         */
        fun getDerivedColor(dominantColor: Int, luminosityFactor: Float = 0.8f): Int {
            val hsl = FloatArray(3)
            ColorUtils.RGBToHSL(
                Color.red(dominantColor),
                Color.green(dominantColor),
                Color.blue(dominantColor),
                hsl
            )

            // Ajusta la luminosidad
            val newLuminosity = (hsl[2] * luminosityFactor).coerceIn(0f, 1f)
            hsl[2] = newLuminosity

            // Convierte de nuevo a RGB
            return ColorUtils.HSLToColor(hsl)
        }

        /**
         * Calcula la luminancia relativa de un color.
         * @param color El color a evaluar.
         * @return La luminancia relativa.
         */
        fun calculateLuminance(color: Int): Double {
            val red = Color.red(color) / 255.0
            val green = Color.green(color) / 255.0
            val blue = Color.blue(color) / 255.0

            val r = if (red <= 0.03928) red / 12.92 else pow((red + 0.055) / 1.055, 2.4)
            val g = if (green <= 0.03928) green / 12.92 else pow((green + 0.055) / 1.055, 2.4)
            val b = if (blue <= 0.03928) blue / 12.92 else pow((blue + 0.055) / 1.055, 2.4)

            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        /**
         * Obtiene un color de contraste optimizado para un color de fondo.
         * @param backgroundColor El color de fondo.
         * @return Color.BLACK o Color.WHITE según el contraste óptimo.
         */
        fun getOptimalContrastColor(backgroundColor: Int): Int {
            val luminance = calculateLuminance(backgroundColor)
            return if (luminance > 0.179) {
                Color.BLACK
            } else {
                Color.WHITE
            }
        }


        fun decodeImage(context: Context, image: ByteArray?): Bitmap {
            val img = Music.getImgArt(musicListPA[songPosition].path)
            val image = if (img != null) {
                BitmapFactory.decodeByteArray(img, 0, img.size)
            } else {
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.music_player_icon_splash_screen
                )
            }
            return image
        }
    }
