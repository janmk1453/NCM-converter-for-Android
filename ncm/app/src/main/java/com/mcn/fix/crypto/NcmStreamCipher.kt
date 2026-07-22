package com.mcn.fix.crypto

class NcmStreamCipher(key: ByteArray) {

    private val s: IntArray = IntArray(256)

    init {
        for (i in 0 until 256) {
            s[i] = i
        }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) % 256
            val tmp = s[i]
            s[i] = s[j]
            s[j] = tmp
        }
    }

    fun decrypt(data: ByteArray, offset: Int = 0): ByteArray {
        val result = data.copyOf()
        for (idx in data.indices) {
            val i = (offset + idx + 1) % 256
            val j = (i + s[i]) % 256
            val k = (s[i] + s[j]) % 256
            result[idx] = (result[idx].toInt() xor s[k]).toByte()
        }
        return result
    }

    fun decryptInPlace(data: ByteArray, offset: Int = 0) {
        for (idx in data.indices) {
            val i = (offset + idx + 1) % 256
            val j = (i + s[i]) % 256
            val k = (s[i] + s[j]) % 256
            data[idx] = (data[idx].toInt() xor s[k]).toByte()
        }
    }
}
