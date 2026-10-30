package org.xmlpull.v1

import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/** The phone's streaming XML reader, over Java's own (StAX). Own code. */
class XmlPullParser {
    private var r: XMLStreamReader? = null
    private var ev = START_DOCUMENT

    fun setInput(ins: java.io.InputStream, encoding: String?) {
        val f = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        }
        r = if (encoding != null) f.createXMLStreamReader(ins, encoding) else f.createXMLStreamReader(ins)
        ev = START_DOCUMENT
    }

    fun next(): Int {
        val x = r ?: return END_DOCUMENT
        while (x.hasNext()) {
            ev = when (x.next()) {
                XMLStreamConstants.START_ELEMENT -> START_TAG
                XMLStreamConstants.END_ELEMENT -> END_TAG
                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> TEXT
                XMLStreamConstants.END_DOCUMENT -> END_DOCUMENT
                else -> continue
            }
            return ev
        }
        ev = END_DOCUMENT
        return ev
    }

    val eventType: Int get() = ev
    val name: String get() = r?.takeIf { ev == START_TAG || ev == END_TAG }?.localName ?: ""
    val text: String get() = r?.takeIf { ev == TEXT }?.text ?: ""
    fun getAttributeValue(ns: String?, attr: String): String? = r?.takeIf { ev == START_TAG }?.let { x ->
        (0 until x.attributeCount).firstOrNull { x.getAttributeLocalName(it) == attr }?.let { x.getAttributeValue(it) }
    }
    val attributeCount: Int get() = r?.takeIf { ev == START_TAG }?.attributeCount ?: 0
    fun getAttributeName(i: Int): String = r?.getAttributeLocalName(i) ?: ""
    fun getAttributeValue(i: Int): String = r?.getAttributeValue(i) ?: ""

    companion object {
        const val START_DOCUMENT = 0
        const val END_DOCUMENT = 1
        const val START_TAG = 2
        const val END_TAG = 3
        const val TEXT = 4
    }
}
