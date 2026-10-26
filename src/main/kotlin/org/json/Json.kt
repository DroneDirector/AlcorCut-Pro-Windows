package org.json

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/** The phone's built-in JSON classes, for the shared code. Own code (on top of Gson). */
class JSONException(msg: String) : RuntimeException(msg)

private fun wrap(e: JsonElement?): Any? = when {
    e == null || e.isJsonNull -> null
    e.isJsonObject -> JSONObject(e.asJsonObject)
    e.isJsonArray -> JSONArray(e.asJsonArray)
    e.asJsonPrimitive.isBoolean -> e.asBoolean
    e.asJsonPrimitive.isNumber -> e.asNumber
    else -> e.asString
}

private fun unwrap(v: Any?): JsonElement = when (v) {
    null -> JsonNull.INSTANCE
    is JSONObject -> v.o
    is JSONArray -> v.a
    is Boolean -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Char -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject().also { o -> v.forEach { (k, x) -> o.add(k.toString(), unwrap(x)) } }
    is Collection<*> -> JsonArray().also { a -> v.forEach { a.add(unwrap(it)) } }
    else -> JsonPrimitive(v.toString())
}

class JSONObject internal constructor(internal val o: JsonObject) {
    constructor() : this(JsonObject())
    constructor(text: String) : this(try { JsonParser.parseString(text).asJsonObject } catch (t: Throwable) { throw JSONException(t.message ?: "Bad JSON") })
    constructor(map: Map<*, *>) : this(unwrap(map).asJsonObject)

    fun put(k: String, v: Any?): JSONObject { o.add(k, unwrap(v)); return this }
    fun put(k: String, v: Int): JSONObject { o.addProperty(k, v); return this }
    fun put(k: String, v: Long): JSONObject { o.addProperty(k, v); return this }
    fun put(k: String, v: Double): JSONObject { o.addProperty(k, v); return this }
    fun put(k: String, v: Boolean): JSONObject { o.addProperty(k, v); return this }
    fun putOpt(k: String?, v: Any?): JSONObject { if (k != null && v != null) put(k, v); return this }
    fun remove(k: String): Any? = wrap(o.remove(k))

    fun has(k: String) = o.has(k)
    fun isNull(k: String) = !o.has(k) || o.get(k).isJsonNull
    fun length() = o.size()
    fun keys(): Iterator<String> = o.keySet().toList().iterator()
    fun names(): JSONArray? = if (o.size() == 0) null else JSONArray(o.keySet().toList())

    private fun need(k: String): JsonElement = o.get(k)?.takeIf { !it.isJsonNull } ?: throw JSONException("No value for $k")
    fun get(k: String): Any = wrap(need(k))!!
    fun opt(k: String): Any? = wrap(o.get(k))
    fun getString(k: String): String = need(k).let { if (it.isJsonPrimitive) it.asString else it.toString() }
    fun getInt(k: String): Int = try { need(k).asInt } catch (t: NumberFormatException) { throw JSONException("Not a number: $k") }
    fun getLong(k: String): Long = try { need(k).asLong } catch (t: NumberFormatException) { throw JSONException("Not a number: $k") }
    fun getDouble(k: String): Double = try { need(k).asDouble } catch (t: NumberFormatException) { throw JSONException("Not a number: $k") }
    fun getBoolean(k: String): Boolean = need(k).let { if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString.equals("true", true) else it.asBoolean }
    fun getJSONObject(k: String): JSONObject = need(k).let { if (it.isJsonObject) JSONObject(it.asJsonObject) else throw JSONException("Not an object: $k") }
    fun getJSONArray(k: String): JSONArray = need(k).let { if (it.isJsonArray) JSONArray(it.asJsonArray) else throw JSONException("Not an array: $k") }

    fun optString(k: String): String = optString(k, "")
    fun optString(k: String, d: String): String = o.get(k)?.takeIf { !it.isJsonNull }?.let { if (it.isJsonPrimitive) it.asString else it.toString() } ?: d
    fun optInt(k: String, d: Int = 0): Int = try { o.get(k)?.takeIf { !it.isJsonNull }?.asInt ?: d } catch (_: Throwable) { d }
    fun optLong(k: String, d: Long = 0L): Long = try { o.get(k)?.takeIf { !it.isJsonNull }?.asLong ?: d } catch (_: Throwable) { d }
    fun optDouble(k: String, d: Double = Double.NaN): Double = try { o.get(k)?.takeIf { !it.isJsonNull }?.asDouble ?: d } catch (_: Throwable) { d }
    fun optBoolean(k: String, d: Boolean = false): Boolean = try { o.get(k)?.takeIf { !it.isJsonNull }?.asBoolean ?: d } catch (_: Throwable) { d }
    fun optJSONObject(k: String): JSONObject? = o.get(k)?.takeIf { it.isJsonObject }?.let { JSONObject(it.asJsonObject) }
    fun optJSONArray(k: String): JSONArray? = o.get(k)?.takeIf { it.isJsonArray }?.let { JSONArray(it.asJsonArray) }

    override fun toString(): String = o.toString()
    fun toString(indent: Int): String = com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(o)

    companion object {
        @JvmField val NULL: Any = Any()
        fun quote(s: String?): String = JsonPrimitive(s ?: "").toString()
    }
}

class JSONArray internal constructor(internal val a: JsonArray) {
    constructor() : this(JsonArray())
    constructor(text: String) : this(try { JsonParser.parseString(text).asJsonArray } catch (t: Throwable) { throw JSONException(t.message ?: "Bad JSON") })
    constructor(c: Collection<*>) : this(unwrap(c).asJsonArray)

    fun length() = a.size()
    fun put(v: Any?): JSONArray { a.add(unwrap(v)); return this }
    fun put(v: Int): JSONArray { a.add(v); return this }
    fun put(v: Long): JSONArray { a.add(v); return this }
    fun put(v: Double): JSONArray { a.add(v); return this }
    fun put(v: Boolean): JSONArray { a.add(v); return this }
    fun put(i: Int, v: Any?): JSONArray { while (a.size() <= i) a.add(JsonNull.INSTANCE); a.set(i, unwrap(v)); return this }
    fun remove(i: Int): Any? = wrap(a.remove(i))
    fun isNull(i: Int) = i !in 0 until a.size() || a[i].isJsonNull

    private fun need(i: Int): JsonElement = if (i in 0 until a.size() && !a[i].isJsonNull) a[i] else throw JSONException("No value at $i")
    fun get(i: Int): Any = wrap(need(i))!!
    fun opt(i: Int): Any? = if (i in 0 until a.size()) wrap(a[i]) else null
    fun getString(i: Int): String = need(i).let { if (it.isJsonPrimitive) it.asString else it.toString() }
    fun getInt(i: Int): Int = need(i).asInt
    fun getLong(i: Int): Long = need(i).asLong
    fun getDouble(i: Int): Double = need(i).asDouble
    fun getBoolean(i: Int): Boolean = need(i).asBoolean
    fun getJSONObject(i: Int): JSONObject = JSONObject(need(i).asJsonObject)
    fun getJSONArray(i: Int): JSONArray = JSONArray(need(i).asJsonArray)
    fun optString(i: Int, d: String = ""): String = if (isNull(i)) d else a[i].let { if (it.isJsonPrimitive) it.asString else it.toString() }
    fun optInt(i: Int, d: Int = 0): Int = try { if (isNull(i)) d else a[i].asInt } catch (_: Throwable) { d }
    fun optLong(i: Int, d: Long = 0L): Long = try { if (isNull(i)) d else a[i].asLong } catch (_: Throwable) { d }
    fun optDouble(i: Int, d: Double = Double.NaN): Double = try { if (isNull(i)) d else a[i].asDouble } catch (_: Throwable) { d }
    fun optBoolean(i: Int, d: Boolean = false): Boolean = try { if (isNull(i)) d else a[i].asBoolean } catch (_: Throwable) { d }
    fun optJSONObject(i: Int): JSONObject? = if (i in 0 until a.size() && a[i].isJsonObject) JSONObject(a[i].asJsonObject) else null
    fun optJSONArray(i: Int): JSONArray? = if (i in 0 until a.size() && a[i].isJsonArray) JSONArray(a[i].asJsonArray) else null

    override fun toString(): String = a.toString()
    fun toString(indent: Int): String = com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(a)
}
