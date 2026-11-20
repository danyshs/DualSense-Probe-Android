package dev.dsprobe.pad

/** Lowercase, space-separated: the format of the fixture files. */
fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it.toInt() and 0xff) }
