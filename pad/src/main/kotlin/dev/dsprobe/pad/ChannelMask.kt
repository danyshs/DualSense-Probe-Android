package dev.dsprobe.pad

/**
 * Bit n set = channel n (0-based) of the 4-channel OUT stream (channels 0-1 audio, 2-3 haptics,
 * P44, P61).
 */
@JvmInline
value class ChannelMask(val bits: Int)
