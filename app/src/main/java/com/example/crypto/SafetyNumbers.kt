package com.example.crypto

import java.security.MessageDigest

object SafetyNumbers {

    fun computeSafetyNumber(localIdentityKey: ByteArray, remoteIdentityKey: ByteArray): String {
        // Sort keys lexically to ensure both parties compute the exact same fingerprint
        val sortedKeys = if (compareByteArrays(localIdentityKey, remoteIdentityKey) <= 0) {
            localIdentityKey + remoteIdentityKey
        } else {
            remoteIdentityKey + localIdentityKey
        }

        // SHA-512 hash
        val digest = MessageDigest.getInstance("SHA-512")
        val hash = digest.digest(sortedKeys)

        // Convert the first 48 bytes of hash into 12 groups of 5 digits (mod 100000)
        val sb = StringBuilder()
        for (i in 0 until 12) {
            val offset = i * 4
            val value = (hash[offset].toInt() and 0xFF shl 24) or
                        (hash[offset + 1].toInt() and 0xFF shl 16) or
                        (hash[offset + 2].toInt() and 0xFF shl 8) or
                        (hash[offset + 3].toInt() and 0xFF)
            val positiveValue = value and 0x7FFFFFFF
            val group = positiveValue % 100000
            sb.append(String.format("%05d", group))
        }
        return sb.toString()
    }

    fun formatForDisplay(safetyNumber: String): String {
        if (safetyNumber.length != 60) return safetyNumber
        return safetyNumber.chunked(5).joinToString(" ")
    }

    fun generateQrCodeData(safetyNumber: String): String {
        return "phantom_sn:$safetyNumber"
    }

    fun verifySafetyNumber(scanned: String, expected: String): Boolean {
        val cleanScanned = scanned.replace("phantom_sn:", "").replace(" ", "").trim()
        val cleanExpected = expected.replace("phantom_sn:", "").replace(" ", "").trim()
        if (cleanScanned.length != 60 || cleanExpected.length != 60) return false
        return MessageDigest.isEqual(cleanScanned.toByteArray(Charsets.UTF_8), cleanExpected.toByteArray(Charsets.UTF_8))
    }

    private fun compareByteArrays(a: ByteArray, b: ByteArray): Int {
        val minLen = minOf(a.size, b.size)
        for (i in 0 until minLen) {
            val byteA = a[i].toInt() and 0xFF
            val byteB = b[i].toInt() and 0xFF
            if (byteA != byteB) {
                return byteA - byteB
            }
        }
        return a.size - b.size
    }

    private val SAS_DICTIONARY = listOf(
        "abacus", "accord", "acoustic", "action", "active", "actor", "adapter", "address",
        "admit", "advice", "aerial", "airport", "album", "alibi", "alley", "alloy",
        "alpine", "alter", "amber", "amigo", "anchor", "animal", "antique", "anvil",
        "apollo", "appeal", "archive", "arctic", "arena", "armor", "arrow", "artist",
        "aspect", "assist", "assume", "astron", "atlas", "atomic", "attitude", "audio",
        "audit", "aurora", "avatar", "average", "aviator", "avoid", "award", "axis",
        "baboon", "backup", "badge", "baffle", "baggage", "bagpipe", "balance", "ballot",
        "balloon", "bamboo", "banana", "bandit", "banjo", "banker", "banner", "barber",
        "barrel", "basement", "basket", "battery", "bazaar", "beacon", "beaver", "beckon",
        "beehive", "beggar", "beginner", "behave", "behind", "belfry", "belief", "bender",
        "benefit", "bengal", "beret", "berry", "bestseller", "betray", "beyond", "bicycle",
        "bifocal", "billiard", "billion", "binary", "binder", "biology", "biplane", "biscuit",
        "bishop", "bison", "bitter", "blacksmith", "bladder", "blanket", "blazer", "blender",
        "blizzard", "blockade", "blossom", "bluebird", "blueprint", "blunder", "blush", "boardwalk",
        "boathouse", "bobcat", "bobsled", "bodyguard", "bohemia", "boiler", "boldness", "bolero",
        "bonanza", "bondage", "bonfire", "bonnet", "bonsai", "bookcase", "booklet", "boomer",
        "bootleg", "border", "botany", "bottle", "bottom", "boulder", "boundary", "bounty",
        "bouquet", "boutique", "bowler", "boxcar", "boyhood", "bracelet", "bracket", "brainpower",
        "bramble", "brandy", "brass", "bravery", "breakout", "breezy", "brewer", "bridal",
        "brigade", "brilliant", "brimstone", "brine", "brisket", "bristle", "broadway", "brocade",
        "broccoli", "brochure", "broken", "broker", "bronze", "brownie", "browse", "brushwood",
        "bubble", "bucket", "buckle", "buffalo", "buffer", "buffet", "bugle", "builder",
        "bulbous", "bulwark", "bumble", "bumper", "bunker", "burden", "bureau", "burglar",
        "burnout", "burrito", "bursar", "bushel", "bustle", "butter", "button", "buyer",
        "bypass", "cabaret", "cabin", "cable", "caboose", "cactus", "cadet", "cadillac",
        "cafe", "cagey", "cahoot", "cajole", "calamity", "calcium", "calculator", "calendar",
        "caliber", "calico", "callbox", "callous", "calmness", "calorie", "camel", "camera",
        "campbell", "camper", "campfire", "campus", "canal", "canary", "candid", "candle"
    )

    fun computeSasWords(safetyNumber: String): List<String> {
        val clean = safetyNumber.replace(" ", "").trim()
        if (clean.length < 12) return listOf("abacus", "accord", "acoustic", "action")
        val words = mutableListOf<String>()
        for (i in 0 until 4) {
            val segment = clean.substring(i * 3, i * 3 + 3)
            val index = (segment.toIntOrNull() ?: 0) % SAS_DICTIONARY.size
            words.add(SAS_DICTIONARY[index])
        }
        return words
    }
}
