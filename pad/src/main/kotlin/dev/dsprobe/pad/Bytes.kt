package dev.dsprobe.pad

internal const val BYTE_MASK = 0xff
internal const val BITS_PER_BYTE = 8
internal const val NIBBLE_LOW_MASK = 0x0f
internal const val NIBBLE_HIGH_MASK = 0xf0
internal const val NIBBLE_BITS = 4

/** Byte [i] as 0..255. */
fun ByteArray.u8(i: Int): Int = this[i].toInt() and BYTE_MASK

/** Bytes [i] and [i] + 1, little-endian, as 0..65535. */
fun ByteArray.u16le(i: Int): Int = u8(i) or (u8(i + 1) shl BITS_PER_BYTE)

/** Bytes [i] to [i] + 3, little-endian. */
fun ByteArray.u32le(i: Int): UInt = (u16le(i) or (u16le(i + 2) shl (2 * BITS_PER_BYTE))).toUInt()
