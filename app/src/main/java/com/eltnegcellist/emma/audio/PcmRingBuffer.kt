package com.eltnegcellist.emma.audio

/** A sample buffer whose epochs discard microphone reads spanning pause/resume boundaries. */
internal class PcmRingBuffer(capacity: Int) {
    private val samples = ShortArray(capacity.also { require(it > 0) })
    private var next = 0
    private var size = 0
    private var enabled = true
    private var epoch = 0L

    @Synchronized fun epoch(): Long = epoch
    @Synchronized fun size(): Int = size

    @Synchronized fun append(input: ShortArray, count: Int, readEpoch: Long) {
        require(count in 0..input.size)
        if (!enabled || readEpoch != epoch) return
        for (i in 0 until count) {
            samples[next] = input[i]
            next = (next + 1) % samples.size
            size = minOf(size + 1, samples.size)
        }
    }

    @Synchronized fun clear() {
        epoch++
        next = 0
        size = 0
        samples.fill(0)
    }

    /** Keep a short pre-roll when speech starts so the first phoneme is not clipped. */
    @Synchronized fun retainLast(maxSamples: Int) {
        require(maxSamples >= 0)
        if (size <= maxSamples) return
        val keep = snapshotInternal(maxSamples)
        epoch++
        next = 0
        size = 0
        samples.fill(0)
        for (sample in keep) {
            samples[next] = sample
            next = (next + 1) % samples.size
            size++
        }
    }

    @Synchronized fun pause() { enabled = false; epoch++ }
    @Synchronized fun resume() { epoch++; enabled = true }

    @Synchronized fun snapshot(maxSamples: Int, consume: Boolean): ShortArray {
        require(maxSamples >= 0)
        val result = snapshotInternal(maxSamples)
        if (consume) clear()
        return result
    }

    private fun snapshotInternal(maxSamples: Int): ShortArray {
        val count = minOf(size, maxSamples)
        val start = (next - count + samples.size) % samples.size
        return ShortArray(count) { samples[(start + it) % samples.size] }
    }
}
