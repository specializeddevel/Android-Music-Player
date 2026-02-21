package com.raulburgosmurray.musicplayer

import android.util.Base64

fun encodeBookId(bookId: String): String {
    return Base64.encodeToString(bookId.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
}

fun decodeBookId(encoded: String): String {
    return String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP))
}