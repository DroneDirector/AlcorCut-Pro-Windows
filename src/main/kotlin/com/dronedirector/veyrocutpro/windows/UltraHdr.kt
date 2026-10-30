package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.ln

/**
 * Ultra HDR JPEG (gain map in the same file, as Android and current browsers read it): the photo as
 * edited, then a small grey picture saying how much brighter each part may be shown on an HDR screen.
 * Own code, following the published Ultra HDR / Adobe gain map layout (XMP + MPF).
 */
object UltraHdr {
    private fun log2(v: Float) = (ln(v.toDouble()) / ln(2.0)).toFloat()

    fun write(out: OutputStream, bmp: Bitmap, quality: Int) {
        val gm = bmp.gainmap ?: throw IllegalStateException("no gain map")
        // The photo itself.
        val cs = bmp.colorSpace
        val icc = if (cs != null && cs != android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)) android.graphics.ColorSpace.icc(cs) else null
        val w = bmp.width; val h = bmp.height
        val primary = ByteArrayOutputStream().also { o -> JpegMax.write(o, w, h, quality, icc) { y, n, dst -> bmp.getPixels(dst, 0, w, 0, y, w, n) } }.toByteArray()

        // The gain map, one grey channel.
        val g = gm.gainmapContents
        val gw = g.width; val gh = g.height
        val gpx = IntArray(gw * gh); g.getPixels(gpx, 0, gw, 0, 0, gw, gh)
        val grey = java.awt.image.BufferedImage(gw, gh, java.awt.image.BufferedImage.TYPE_BYTE_GRAY)
        val raster = grey.raster
        for (y in 0 until gh) for (x in 0 until gw) raster.setSample(x, y, 0, (gpx[y * gw + x] shr 8) and 0xFF)
        val gmJpeg = ByteArrayOutputStream().also { o ->
            val wr = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next()
            val p = wr.defaultWriteParam.apply { compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT; compressionQuality = 0.9f }
            javax.imageio.ImageIO.createImageOutputStream(o).use { ios -> wr.output = ios; wr.write(null, javax.imageio.IIOImage(grey, null, null), p) }
            wr.dispose()
        }.toByteArray()
        val f = java.util.Locale.US
        val mapXmp = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">""" +
            """<rdf:Description xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/" hdrgm:Version="1.0"""" +
            """ hdrgm:GainMapMin="${"%.6f".format(f, log2(gm.ratioMin))}" hdrgm:GainMapMax="${"%.6f".format(f, log2(gm.ratioMax))}"""" +
            """ hdrgm:Gamma="${"%.6f".format(f, gm.gamma)}" hdrgm:OffsetSDR="${"%.6f".format(f, gm.epsilonSdr)}" hdrgm:OffsetHDR="${"%.6f".format(f, gm.epsilonHdr)}"""" +
            """ hdrgm:HDRCapacityMin="${"%.6f".format(f, log2(gm.minDisplayRatioForHdrTransition.coerceAtLeast(1f)))}" hdrgm:HDRCapacityMax="${"%.6f".format(f, log2(gm.displayRatioForFullHdr.coerceAtLeast(1f)))}"""" +
            """ hdrgm:BaseRenditionIsHDR="False"/></rdf:RDF></x:xmpmeta>"""
        val mapFull = ByteArrayOutputStream().apply {
            write(0xFF); write(0xD8); segment(this, 0xE1, xmpBody(mapXmp)); write(gmJpeg, 2, gmJpeg.size - 2)
        }.toByteArray()

        val mainXmp = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">""" +
            """<rdf:Description xmlns:Container="http://ns.google.com/photos/1.0/container/" xmlns:Item="http://ns.google.com/photos/1.0/container/item/" xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/" hdrgm:Version="1.0">""" +
            """<Container:Directory><rdf:Seq><rdf:li rdf:parseType="Resource"><Container:Item Item:Semantic="Primary" Item:Mime="image/jpeg"/></rdf:li>""" +
            """<rdf:li rdf:parseType="Resource"><Container:Item Item:Semantic="GainMap" Item:Mime="image/jpeg" Item:Length="${mapFull.size}"/></rdf:li></rdf:Seq></Container:Directory>""" +
            """</rdf:Description></rdf:RDF></x:xmpmeta>"""

        // Main picture: SOI, (JFIF), XMP, MPF, the rest.
        var i = 2
        if ((primary[2].toInt() and 0xFF) == 0xFF && (primary[3].toInt() and 0xFF) == 0xE0) i += 2 + (((primary[4].toInt() and 0xFF) shl 8) or (primary[5].toInt() and 0xFF))
        val head = ByteArrayOutputStream().apply { write(primary, 0, i); segment(this, 0xE1, xmpBody(mainXmp)) }.toByteArray()
        val mpfLen = 4 + 86
        val tiffStart = head.size + 4 + 4
        val primaryLen = head.size + mpfLen + (primary.size - i)
        val mpf = mpfBody(primaryLen, mapFull.size, primaryLen - tiffStart)
        out.write(head)
        segment(out, 0xE2, mpf)
        out.write(primary, i, primary.size - i)
        out.write(mapFull)
    }

    private fun xmpBody(xmp: String) = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.ISO_8859_1) + xmp.toByteArray(Charsets.UTF_8)

    private fun segment(o: OutputStream, marker: Int, body: ByteArray) {
        o.write(0xFF); o.write(marker); o.write((body.size + 2) shr 8); o.write((body.size + 2) and 0xFF); o.write(body)
    }

    /** Multi-Picture Format index: the main picture and the gain map after it (offsets from the TIFF header). */
    private fun mpfBody(primaryLen: Int, mapLen: Int, mapOffset: Int): ByteArray {
        val b = java.nio.ByteBuffer.allocate(86).order(java.nio.ByteOrder.BIG_ENDIAN)
        b.put("MPF".toByteArray()); b.put(0)
        b.put('M'.code.toByte()); b.put('M'.code.toByte()); b.putShort(0x2A); b.putInt(8)
        b.putShort(3)
        b.putShort(0xB000.toShort()); b.putShort(7); b.putInt(4); b.put("0100".toByteArray())
        b.putShort(0xB001.toShort()); b.putShort(4); b.putInt(1); b.putInt(2)
        b.putShort(0xB002.toShort()); b.putShort(7); b.putInt(32); b.putInt(50)
        b.putInt(0)
        b.putInt(0x030000); b.putInt(primaryLen); b.putInt(0); b.putShort(0); b.putShort(0)
        b.putInt(0); b.putInt(mapLen); b.putInt(mapOffset); b.putShort(0); b.putShort(0)
        return b.array()
    }
}
