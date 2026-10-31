package com.dronedirector.veyrocutpro.editor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Audio file writers for the audio-only export, written for AlcorCut Pro (no third-party code).
 * Input is a raw interleaved little-endian PCM file — 16-bit integer or 32-bit float — captured
 * from the finished mix. Formats: WAV (16 / 24-bit integer, 32-bit float), AIFF (16 / 24-bit) and
 * FLAC (16 / 24-bit, lossless). All three formats are open and free to use in commercial apps.
 */
public final class PcmFiles {
    private PcmFiles() {}

    /** Reads interleaved samples from the raw file as integers of [bits] bits (or floats). */
    private static final class Source implements AutoCloseable {
        final InputStream in;
        final boolean floatIn;
        final int channels;
        final byte[] buf;
        long seed = 0x9E3779B97F4A7C15L;

        Source(File raw, boolean floatIn, int channels) throws IOException {
            this.in = new BufferedInputStream(new FileInputStream(raw), 1 << 16);
            this.floatIn = floatIn;
            this.channels = channels;
            this.buf = new byte[4096 * channels * (floatIn ? 4 : 2)];
        }

        private double rnd() {
            seed ^= seed << 13; seed ^= seed >>> 7; seed ^= seed << 17;
            return (seed >>> 11) * (1.0 / (1L << 53));
        }

        /** Fills up to [frames] frames into [dst] (frame-major); returns the frames read. */
        int readInt(int[] dst, int frames, int bits) throws IOException {
            int bpsIn = floatIn ? 4 : 2;
            int want = frames * channels * bpsIn;
            int got = 0;
            while (got < want) {
                int n = in.read(buf, got, want - got);
                if (n < 0) break;
                got += n;
            }
            int f = got / (channels * bpsIn);
            int count = f * channels;
            long max = (1L << (bits - 1)) - 1, min = -(1L << (bits - 1));
            for (int i = 0; i < count; i++) {
                if (floatIn) {
                    int b = i * 4;
                    int v = (buf[b] & 0xFF) | (buf[b + 1] & 0xFF) << 8 | (buf[b + 2] & 0xFF) << 16 | (buf[b + 3] & 0xFF) << 24;
                    double x = Float.intBitsToFloat(v);
                    if (Double.isNaN(x)) x = 0;
                    double s = x * (max + 1);
                    // TPDF dither when making 16-bit from float.
                    if (bits <= 16) s += rnd() - rnd();
                    long q = Math.round(s);
                    dst[i] = (int) Math.max(min, Math.min(max, q));
                } else {
                    int b = i * 2;
                    int v = (short) ((buf[b] & 0xFF) | (buf[b + 1] << 8));
                    dst[i] = bits >= 16 ? v << (bits - 16) : v >> (16 - bits);
                }
            }
            return f;
        }

        /** Same, as floats in -1..1. */
        int readFloat(float[] dst, int frames) throws IOException {
            int bpsIn = floatIn ? 4 : 2;
            int want = frames * channels * bpsIn;
            int got = 0;
            while (got < want) {
                int n = in.read(buf, got, want - got);
                if (n < 0) break;
                got += n;
            }
            int f = got / (channels * bpsIn);
            for (int i = 0; i < f * channels; i++) {
                if (floatIn) {
                    int b = i * 4;
                    dst[i] = Float.intBitsToFloat((buf[b] & 0xFF) | (buf[b + 1] & 0xFF) << 8 | (buf[b + 2] & 0xFF) << 16 | (buf[b + 3] & 0xFF) << 24);
                } else {
                    int b = i * 2;
                    dst[i] = ((short) ((buf[b] & 0xFF) | (buf[b + 1] << 8))) / 32768f;
                }
            }
            return f;
        }

        @Override public void close() throws IOException { in.close(); }
    }

    public static long frames(File raw, boolean floatIn, int channels) {
        return raw.length() / ((long) channels * (floatIn ? 4 : 2));
    }

    private static void le16(OutputStream o, int v) throws IOException { o.write(v); o.write(v >>> 8); }
    private static void le32(OutputStream o, long v) throws IOException { o.write((int) v); o.write((int) (v >>> 8)); o.write((int) (v >>> 16)); o.write((int) (v >>> 24)); }
    private static void be16(OutputStream o, int v) throws IOException { o.write(v >>> 8); o.write(v); }
    private static void be32(OutputStream o, long v) throws IOException { o.write((int) (v >>> 24)); o.write((int) (v >>> 16)); o.write((int) (v >>> 8)); o.write((int) v); }

    // ------------------------------------------------------------------ WAV

    /** WAV: [bits] 16 or 24 (integer PCM) or 32 (IEEE float). */
    public static void writeWav(File raw, boolean floatIn, int rate, int ch, int bits, OutputStream os) throws IOException {
        boolean fl = bits == 32;
        int bps = bits / 8;
        long frames = frames(raw, floatIn, ch);
        long data = frames * ch * bps;
        boolean ext = ch > 2 || bits == 24;
        os.write(new byte[] {'R', 'I', 'F', 'F'});
        le32(os, 4 + (ext ? 48 : (fl ? 26 : 24)) + (fl ? 12 : 0) + 8 + data + (data & 1));
        os.write(new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        if (ext) {
            le32(os, 40);
            le16(os, 0xFFFE); le16(os, ch); le32(os, rate); le32(os, (long) rate * ch * bps); le16(os, ch * bps); le16(os, bits);
            le16(os, 22); le16(os, bits);
            le32(os, ch == 1 ? 0x4 : ch == 2 ? 0x3 : (1L << ch) - 1);
            // KSDATAFORMAT_SUBTYPE_PCM / IEEE_FLOAT GUID
            le32(os, fl ? 3 : 1); le16(os, 0x0000); le16(os, 0x0010);
            os.write(new byte[] {(byte) 0x80, 0x00, 0x00, (byte) 0xAA, 0x00, 0x38, (byte) 0x9B, 0x71});
        } else {
            le32(os, fl ? 18 : 16);
            le16(os, fl ? 3 : 1); le16(os, ch); le32(os, rate); le32(os, (long) rate * ch * bps); le16(os, ch * bps); le16(os, bits);
            if (fl) le16(os, 0);
        }
        if (fl) { os.write(new byte[] {'f', 'a', 'c', 't'}); le32(os, 4); le32(os, frames); }
        os.write(new byte[] {'d', 'a', 't', 'a'});
        le32(os, data);
        writeSamples(raw, floatIn, ch, bits, false, os);
        if ((data & 1) != 0) os.write(0);
    }

    private static void writeSamples(File raw, boolean floatIn, int ch, int bits, boolean bigEndian, OutputStream os) throws IOException {
        try (Source s = new Source(raw, floatIn, ch)) {
            int n = 4096;
            byte[] out = new byte[n * ch * 4];
            if (bits == 32) {
                float[] f = new float[n * ch];
                int got;
                while ((got = s.readFloat(f, n)) > 0) {
                    int o = 0;
                    for (int i = 0; i < got * ch; i++) {
                        int v = Float.floatToIntBits(f[i]);
                        out[o++] = (byte) v; out[o++] = (byte) (v >>> 8); out[o++] = (byte) (v >>> 16); out[o++] = (byte) (v >>> 24);
                    }
                    os.write(out, 0, o);
                }
            } else {
                int[] v = new int[n * ch];
                int got;
                while ((got = s.readInt(v, n, bits)) > 0) {
                    int o = 0;
                    for (int i = 0; i < got * ch; i++) {
                        int x = v[i];
                        if (bigEndian) {
                            if (bits == 24) out[o++] = (byte) (x >>> 16);
                            out[o++] = (byte) (x >>> 8); out[o++] = (byte) x;
                        } else {
                            out[o++] = (byte) x; out[o++] = (byte) (x >>> 8);
                            if (bits == 24) out[o++] = (byte) (x >>> 16);
                        }
                    }
                    os.write(out, 0, o);
                }
            }
        }
    }

    // ------------------------------------------------------------------ AIFF

    /** AIFF (big-endian integer PCM), [bits] 16 or 24. */
    public static void writeAiff(File raw, boolean floatIn, int rate, int ch, int bits, OutputStream os) throws IOException {
        int bps = bits / 8;
        long frames = frames(raw, floatIn, ch);
        long data = frames * ch * bps;
        os.write(new byte[] {'F', 'O', 'R', 'M'});
        be32(os, 4 + 26 + 16 + data + (data & 1));
        os.write(new byte[] {'A', 'I', 'F', 'F', 'C', 'O', 'M', 'M'});
        be32(os, 18);
        be16(os, ch); be32(os, frames); be16(os, bits);
        os.write(ieee80(rate));
        os.write(new byte[] {'S', 'S', 'N', 'D'});
        be32(os, 8 + data);
        be32(os, 0); be32(os, 0);
        writeSamples(raw, floatIn, ch, bits, true, os);
        if ((data & 1) != 0) os.write(0);
    }

    /** 80-bit IEEE extended for the AIFF sample rate. */
    static byte[] ieee80(int rate) {
        byte[] b = new byte[10];
        if (rate <= 0) return b;
        int exp = 31 - Integer.numberOfLeadingZeros(rate);
        long mant = ((long) rate) << (63 - exp);
        int e = exp + 16383;
        b[0] = (byte) (e >>> 8); b[1] = (byte) e;
        for (int i = 0; i < 8; i++) b[2 + i] = (byte) (mant >>> (56 - 8 * i));
        return b;
    }

    // ------------------------------------------------------------------ FLAC

    private static final int BLOCK = 4096;

    /** Bit writer for one FLAC frame. */
    private static final class Bits {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(BLOCK * 8);
        long acc; int n;
        void put(long v, int bits) {
            for (int i = bits - 1; i >= 0; i--) {
                acc = (acc << 1) | ((v >>> i) & 1);
                if (++n == 8) { out.write((int) acc); acc = 0; n = 0; }
            }
        }
        void unary(long q) {
            // q zeros then a one
            while (q >= 32) { put(0, 32); q -= 32; }
            put(1, (int) q + 1);
        }
        void align() { if (n > 0) put(0, 8 - n); }
        byte[] bytes() { return out.toByteArray(); }
        long bitLength() { return out.size() * 8L + n; }
    }

    private static int crc8(byte[] d, int len) {
        int c = 0;
        for (int i = 0; i < len; i++) {
            c ^= d[i] & 0xFF;
            for (int k = 0; k < 8; k++) c = (c & 0x80) != 0 ? ((c << 1) ^ 0x07) & 0xFF : (c << 1) & 0xFF;
        }
        return c;
    }

    private static int crc16(byte[] d, int len) {
        int c = 0;
        for (int i = 0; i < len; i++) {
            c ^= (d[i] & 0xFF) << 8;
            for (int k = 0; k < 8; k++) c = (c & 0x8000) != 0 ? ((c << 1) ^ 0x8005) & 0xFFFF : (c << 1) & 0xFFFF;
        }
        return c;
    }

    /** Fixed-predictor residual of order [o] for samples x[0..n). */
    private static void residual(long[] x, int n, int o, long[] r) {
        for (int i = o; i < n; i++) {
            long e;
            switch (o) {
                case 0: e = x[i]; break;
                case 1: e = x[i] - x[i - 1]; break;
                case 2: e = x[i] - 2 * x[i - 1] + x[i - 2]; break;
                case 3: e = x[i] - 3 * x[i - 1] + 3 * x[i - 2] - x[i - 3]; break;
                default: e = x[i] - 4 * x[i - 1] + 6 * x[i - 2] - 4 * x[i - 3] + x[i - 4]; break;
            }
            r[i] = e;
        }
    }

    private static long zig(long r) { return r >= 0 ? r << 1 : ((-r) << 1) - 1; }

    /** Best Rice parameter and its cost (bits) for residuals r[from..to). */
    private static long[] rice(long[] r, int from, int to) {
        long sum = 0;
        for (int i = from; i < to; i++) sum += zig(r[i]);
        int cnt = Math.max(1, to - from);
        long mean = sum / cnt;
        int k0 = mean > 0 ? 63 - Long.numberOfLeadingZeros(mean) : 0;
        long bestBits = Long.MAX_VALUE; int bestK = 0;
        int lo = Math.min(14, Math.max(0, k0 - 1)), hi = Math.min(14, Math.max(0, k0 + 1));
        for (int k = lo; k <= hi; k++) {
            long bits = 0;
            for (int i = from; i < to; i++) bits += (zig(r[i]) >>> k) + 1 + k;
            if (bits < bestBits) { bestBits = bits; bestK = k; }
        }
        return new long[] {bestK, bestBits + 4};
    }

    /** Writes one channel's subframe for x[0..n) with [bps] bits per sample. */
    private static void subframe(Bits b, long[] x, int n, int bps, long[] r) {
        // Constant?
        boolean constant = true;
        for (int i = 1; i < n; i++) if (x[i] != x[0]) { constant = false; break; }
        if (constant) { b.put(0, 1); b.put(0, 6); b.put(0, 1); b.put(x[0] & ((1L << bps) - 1), bps); return; }
        long verbatim = (long) n * bps;
        long best = Long.MAX_VALUE; int bestO = -1, bestP = 0;
        for (int o = 0; o <= Math.min(4, n - 1); o++) {
            residual(x, n, o, r);
            for (int p = 0; p <= 6; p++) {
                if ((n & ((1 << p) - 1)) != 0) break;
                int part = n >> p;
                if (part <= o) break;
                long bits = 4 + 2 + (long) o * bps;
                boolean ok = true;
                for (int q = 0; q < (1 << p); q++) {
                    int from = q == 0 ? o : q * part;
                    long[] kr = rice(r, from, (q + 1) * part);
                    if (kr[0] >= 14 && kr[1] > (long) (part) * bps) { ok = false; break; }
                    bits += kr[1];
                }
                if (ok && bits < best) { best = bits; bestO = o; bestP = p; }
            }
        }
        if (bestO < 0 || best >= verbatim) {
            b.put(0, 1); b.put(1, 6); b.put(0, 1);
            for (int i = 0; i < n; i++) b.put(x[i] & ((1L << bps) - 1), bps);
            return;
        }
        int o = bestO, p = bestP;
        residual(x, n, o, r);
        b.put(0, 1); b.put(8 | o, 6); b.put(0, 1);
        for (int i = 0; i < o; i++) b.put(x[i] & ((1L << bps) - 1), bps);
        b.put(0, 2);           // Rice, 4-bit parameters
        b.put(p, 4);
        int part = n >> p;
        for (int q = 0; q < (1 << p); q++) {
            int from = q == 0 ? o : q * part;
            int to = (q + 1) * part;
            int k = (int) rice(r, from, to)[0];
            b.put(k, 4);
            for (int i = from; i < to; i++) {
                long u = zig(r[i]);
                b.unary(u >>> k);
                if (k > 0) b.put(u & ((1L << k) - 1), k);
            }
        }
    }

    private static void utf8(Bits b, long v) {
        if (v < 0x80) { b.put(v, 8); return; }
        int bytes = v < 0x800 ? 2 : v < 0x10000 ? 3 : v < 0x200000 ? 4 : v < 0x4000000 ? 5 : 6;
        int shift = (bytes - 1) * 6;
        b.put(((0xFF00 >> bytes) & 0xFF) | (v >>> shift), 8);
        for (int i = bytes - 2; i >= 0; i--) b.put(0x80 | ((v >>> (i * 6)) & 0x3F), 8);
    }

    /** FLAC, [bits] 16 or 24, up to 8 channels, any sample rate up to 655 kHz. */
    public static void writeFlac(File raw, boolean floatIn, int rate, int ch, int bits, OutputStream os) throws IOException {
        long total = frames(raw, floatIn, ch);
        // "fLaC" + STREAMINFO (last metadata block).
        os.write(new byte[] {'f', 'L', 'a', 'C'});
        Bits h = new Bits();
        h.put(1, 1); h.put(0, 7); h.put(34, 24);
        h.put(BLOCK, 16); h.put(BLOCK, 16);
        h.put(0, 24); h.put(0, 24);
        h.put(rate, 20); h.put(ch - 1, 3); h.put(bits - 1, 5); h.put(total, 36);
        for (int i = 0; i < 16; i++) h.put(0, 8);   // MD5 unknown
        os.write(h.bytes());

        int ssCode = bits == 16 ? 4 : bits == 24 ? 6 : 0;
        int[] inter = new int[BLOCK * ch];
        long[][] chan = new long[ch][BLOCK];
        long[] r = new long[BLOCK];
        try (Source s = new Source(raw, floatIn, ch)) {
            long frameNo = 0;
            int n;
            while ((n = s.readInt(inter, BLOCK, bits)) > 0) {
                for (int i = 0; i < n; i++) for (int c = 0; c < ch; c++) chan[c][i] = inter[i * ch + c];
                Bits b = new Bits();
                b.put(0xFFF8, 16);
                int bsCode = n == BLOCK ? 12 : 7;
                b.put(bsCode, 4); b.put(0, 4);           // sample rate from STREAMINFO
                b.put(ch - 1, 4); b.put(ssCode, 3); b.put(0, 1);
                utf8(b, frameNo++);
                if (bsCode == 7) b.put(n - 1, 16);
                byte[] head = b.bytes();
                b.put(crc8(head, head.length), 8);
                for (int c = 0; c < ch; c++) subframe(b, chan[c], n, bits, r);
                b.align();
                byte[] fr = b.bytes();
                int crc = crc16(fr, fr.length);
                os.write(fr);
                os.write(crc >>> 8); os.write(crc & 0xFF);
            }
        }
    }

    // ------------------------------------------------------------------ helpers for other encoders

    /** Converts the raw capture to 16-bit interleaved little-endian PCM (for the Opus encoder). */
    public static void toPcm16(File raw, boolean floatIn, int ch, OutputStream os) throws IOException {
        writeSamples(raw, floatIn, ch, 16, false, os);
    }
}
