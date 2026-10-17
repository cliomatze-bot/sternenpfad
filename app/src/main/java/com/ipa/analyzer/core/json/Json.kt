package com.ipa.analyzer.core.json

/**
 * Kleiner, abhängigkeitsfreier JSON-Parser und -Writer.
 * Bewusst ohne Android-APIs, damit die gesamte Datenlogik auf der JVM testbar ist.
 */
sealed class JsonValue {
    data class Obj(val map: Map<String, JsonValue>) : JsonValue() {
        operator fun get(key: String): JsonValue? = map[key]
    }
    data class Arr(val list: List<JsonValue>) : JsonValue()
    data class Str(val value: String) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data object Null : JsonValue()

    val obj: Obj? get() = this as? Obj
    val arr: List<JsonValue>? get() = (this as? Arr)?.list
    val str: String? get() = (this as? Str)?.value
    val long: Long? get() = when (this) {
        is Num -> value.toLong()
        is Str -> value.toLongOrNull()
        else -> null
    }
    val bool: Boolean? get() = (this as? Bool)?.value
}

class JsonParseException(message: String) : Exception(message)

object Json {

    fun parse(text: String): JsonValue {
        val p = Parser(text)
        p.skipWs()
        val v = p.readValue()
        p.skipWs()
        if (!p.atEnd()) throw JsonParseException("Unerwartete Zeichen nach JSON-Ende bei Position ${p.pos}")
        return v
    }

    fun parseOrNull(text: String): JsonValue? = try { parse(text) } catch (e: JsonParseException) { null }

    fun stringify(value: JsonValue): String = StringBuilder().also { write(value, it) }.toString()

    private fun write(v: JsonValue, sb: StringBuilder) {
        when (v) {
            is JsonValue.Null -> sb.append("null")
            is JsonValue.Bool -> sb.append(if (v.value) "true" else "false")
            is JsonValue.Num -> {
                val d = v.value
                if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 9.0E15) sb.append(d.toLong())
                else sb.append(d)
            }
            is JsonValue.Str -> writeString(v.value, sb)
            is JsonValue.Arr -> {
                sb.append('[')
                v.list.forEachIndexed { i, item -> if (i > 0) sb.append(','); write(item, sb) }
                sb.append(']')
            }
            is JsonValue.Obj -> {
                sb.append('{')
                var first = true
                for ((k, item) in v.map) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(k, sb); sb.append(':'); write(item, sb)
                }
                sb.append('}')
            }
        }
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipWs() {
            while (pos < s.length && (s[pos] == ' ' || s[pos] == '\n' || s[pos] == '\r' || s[pos] == '\t' || s[pos] == '﻿')) pos++
        }

        fun readValue(): JsonValue {
            if (atEnd()) throw JsonParseException("Unerwartetes Ende der Eingabe")
            return when (val c = s[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> JsonValue.Str(readString())
                't' -> literal("true", JsonValue.Bool(true))
                'f' -> literal("false", JsonValue.Bool(false))
                'n' -> literal("null", JsonValue.Null)
                else -> if (c == '-' || c.isDigit()) readNumber()
                else throw JsonParseException("Unerwartetes Zeichen '$c' bei Position $pos")
            }
        }

        private fun literal(word: String, v: JsonValue): JsonValue {
            if (!s.startsWith(word, pos)) throw JsonParseException("Ungültiges Literal bei Position $pos")
            pos += word.length
            return v
        }

        private fun readObject(): JsonValue {
            pos++ // {
            val map = LinkedHashMap<String, JsonValue>()
            skipWs()
            if (pos < s.length && s[pos] == '}') { pos++; return JsonValue.Obj(map) }
            while (true) {
                skipWs()
                if (atEnd() || s[pos] != '"') throw JsonParseException("Schlüssel erwartet bei Position $pos")
                val key = readString()
                skipWs()
                if (atEnd() || s[pos] != ':') throw JsonParseException("':' erwartet bei Position $pos")
                pos++
                skipWs()
                map[key] = readValue()
                skipWs()
                if (atEnd()) throw JsonParseException("Unerwartetes Ende im Objekt")
                when (s[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return JsonValue.Obj(map) }
                    else -> throw JsonParseException("',' oder '}' erwartet bei Position $pos")
                }
            }
        }

        private fun readArray(): JsonValue {
            pos++ // [
            val list = ArrayList<JsonValue>()
            skipWs()
            if (pos < s.length && s[pos] == ']') { pos++; return JsonValue.Arr(list) }
            while (true) {
                skipWs()
                list.add(readValue())
                skipWs()
                if (atEnd()) throw JsonParseException("Unerwartetes Ende im Array")
                when (s[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return JsonValue.Arr(list) }
                    else -> throw JsonParseException("',' oder ']' erwartet bei Position $pos")
                }
            }
        }

        private fun readString(): String {
            pos++ // "
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonParseException("Nicht abgeschlossener String")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonParseException("Ungültige Escape-Sequenz")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw JsonParseException("Ungültige Unicode-Escape-Sequenz")
                                val hex = s.substring(pos, pos + 4)
                                sb.append(hex.toIntOrNull(16)?.toChar()
                                    ?: throw JsonParseException("Ungültige Unicode-Escape-Sequenz '$hex'"))
                                pos += 4
                            }
                            else -> throw JsonParseException("Unbekannte Escape-Sequenz '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): JsonValue {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.' || s[pos] == 'e' || s[pos] == 'E' || s[pos] == '+' || s[pos] == '-')) pos++
            val t = s.substring(start, pos)
            return JsonValue.Num(t.toDoubleOrNull() ?: throw JsonParseException("Ungültige Zahl '$t'"))
        }
    }
}

/** Hilfsfunktionen zum Aufbau von JSON-Werten. */
fun jsonObj(vararg pairs: Pair<String, JsonValue?>): JsonValue.Obj =
    JsonValue.Obj(LinkedHashMap<String, JsonValue>().apply { pairs.forEach { (k, v) -> if (v != null) put(k, v) } })

fun jsonArr(items: List<JsonValue>): JsonValue.Arr = JsonValue.Arr(items)
fun String?.toJson(): JsonValue = if (this == null) JsonValue.Null else JsonValue.Str(this)
fun Long?.toJson(): JsonValue = if (this == null) JsonValue.Null else JsonValue.Num(this.toDouble())
fun Int?.toJson(): JsonValue = if (this == null) JsonValue.Null else JsonValue.Num(this.toDouble())
