package com.cl.vtolive.modules.core;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Injects Apple QuickTime metadata (com.apple.quicktime.content.identifier)
 * into MOV/MP4 files for Live Photo pairing with iPhone, Photos app, WeChat, AirDrop.
 */
public class MovMetadataInjector {
    private static final String TAG = "MovMetadataInjector";

    /**
     * Injects ContentIdentifier into the MOV/MP4 file.
     *
     * @param movPath   Path to the MOV/MP4 file (will be modified in place)
     * @param contentId UUID string that must match the HEIC's ContentIdentifier
     * @return true if metadata was successfully added
     */
    public static boolean injectContentIdentifier(String movPath, String contentId) {
        if (contentId == null || contentId.isEmpty()) return false;
        try {
            File file = new File(movPath);
            if (!file.exists() || !file.canRead() || !file.canWrite()) return false;

            byte[] idBytes = contentId.getBytes("UTF-8");
            byte[] udtaBox = buildUdtaBox(idBytes);

            try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
                 FileChannel ch = raf.getChannel()) {

                BoxInfo moov = findBox(ch, 0, file.length(), "moov");
                if (moov == null) {
                    Log.w(TAG, "moov box not found");
                    return false;
                }

                long udtaOffset = findUdtaInsertPoint(ch, moov);
                if (udtaOffset < 0) {
                    udtaOffset = moov.offset + 8;  // Insert after moov header
                }

                long bytesToInsert = udtaBox.length;
                long insertPosition = udtaOffset;

                ch.position(insertPosition);
                long afterInsert = ch.size() - insertPosition;
                byte[] tail = new byte[(int) afterInsert];
                ch.read(ByteBuffer.wrap(tail));
                ch.position(insertPosition);
                ch.write(ByteBuffer.wrap(udtaBox));
                ch.write(ByteBuffer.wrap(tail));

                updateMoovSize(ch, moov, (int) bytesToInsert);

                Log.d(TAG, "Injected ContentIdentifier into " + movPath);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to inject metadata", e);
            return false;
        }
    }

    private static byte[] buildUdtaBox(byte[] contentId) {
        byte[] mean = "com.apple.quicktime".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] name = "content.identifier".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] meanBox = buildFullBox("mean", 0, mean);
        byte[] nameBox = buildFullBox("name", 0, name);
        byte[] dataBox = buildDataBox(contentId);

        byte[] dashBox = concat(meanBox, nameBox, dataBox);
        byte[] dashAtom = buildBox("----", dashBox);

        byte[] ilstContents = buildBox("ilst", dashAtom);
        byte[] hdlr = buildHdlrBox();
        byte[] metaContents = concat(hdlr, ilstContents);
        byte[] metaBox = buildFullBox("meta", 0, metaContents);
        return buildBox("udta", metaBox);
    }

    private static byte[] buildBox(String type, byte[] content) {
        int total = 8 + content.length;
        ByteBuffer bb = ByteBuffer.allocate(total);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.putInt(total);
        bb.put(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        bb.put(content);
        return bb.array();
    }

    private static byte[] buildFullBox(String type, int flags, byte[] content) {
        int total = 12 + content.length;
        ByteBuffer bb = ByteBuffer.allocate(total);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.putInt(total);
        bb.put(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        bb.putInt(flags);
        bb.put(content);
        return bb.array();
    }

    private static byte[] buildDataBox(byte[] value) {
        int total = 8 + 4 + value.length;
        ByteBuffer bb = ByteBuffer.allocate(total);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.putInt(total);
        bb.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        bb.putInt(0x01000000);   // type 1 (UTF-8) + locale 0
        bb.put(value);
        return bb.array();
    }

    private static byte[] buildHdlrBox() {
        int total = 8 + 4 + 4 + 4 + 12 + 1;
        ByteBuffer bb = ByteBuffer.allocate(total);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.putInt(total);
        bb.put("hdlr".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        bb.putInt(0);  // version + flags
        bb.putInt(0);  // pre_defined
        bb.put("mdir".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        bb.put(new byte[12]);  // reserved
        bb.put((byte) 0);     // name (empty, null-terminated)
        return bb.array();
    }

    private static byte[] concat(byte[]... arr) {
        int len = 0;
        for (byte[] a : arr) len += a.length;
        ByteBuffer bb = ByteBuffer.allocate(len);
        for (byte[] a : arr) bb.put(a);
        return bb.array();
    }

    private static class BoxInfo {
        long offset;
        int size;
        String type;
    }

    private static BoxInfo findBox(FileChannel ch, long start, long end, String type) throws IOException {
        ch.position(start);
        while (ch.position() < end - 8) {
            long pos = ch.position();
            ByteBuffer header = ByteBuffer.allocate(8);
            header.order(ByteOrder.BIG_ENDIAN);
            if (ch.read(header) < 8) break;
            header.flip();
            int size = header.getInt();
            byte[] t = new byte[4];
            header.get(t);
            String typeStr = new String(t, "US_ASCII");
            if (size == 1) {
                ByteBuffer ext = ByteBuffer.allocate(8);
                ch.read(ext);
                ext.flip();
                size = (int) ext.getLong();
            }
            if (size <= 0) break;
            if (typeStr.equals(type)) {
                BoxInfo info = new BoxInfo();
                info.offset = pos;
                info.size = size;
                info.type = typeStr;
                return info;
            }
            ch.position(pos + size);
        }
        return null;
    }

    private static long findUdtaInsertPoint(FileChannel ch, BoxInfo moov) throws IOException {
        long p = moov.offset + 8;
        long end = moov.offset + moov.size;
        while (p < end - 8) {
            ch.position(p);
            ByteBuffer h = ByteBuffer.allocate(8);
            h.order(ByteOrder.BIG_ENDIAN);
            if (ch.read(h) < 8) break;
            h.flip();
            int sz = h.getInt();
            byte[] t = new byte[4];
            h.get(t);
            String type = new String(t, "US_ASCII");
            if (sz == 1) {
                ByteBuffer ext = ByteBuffer.allocate(8);
                ch.read(ext);
                ext.flip();
                sz = (int) ext.getLong();
            }
            if (sz <= 0) break;
            if (type.equals("trak")) {
                return p;
            }
            p += sz;
        }
        return -1;
    }

    private static void updateMoovSize(FileChannel ch, BoxInfo moov, int delta) throws IOException {
        ch.position(moov.offset);
        ByteBuffer sizeBuf = ByteBuffer.allocate(4);
        sizeBuf.order(ByteOrder.BIG_ENDIAN);
        sizeBuf.putInt(moov.size + delta);
        sizeBuf.flip();
        ch.write(sizeBuf);
    }

    private static void updateChunkOffsets(FileChannel ch, BoxInfo moov, int delta) throws IOException {
        List<Long> stcoOffsets = new ArrayList<>();
        long p = moov.offset + 8;
        long end = moov.offset + moov.size;
        while (p < end - 8) {
            ch.position(p);
            ByteBuffer h = ByteBuffer.allocate(8);
            h.order(ByteOrder.BIG_ENDIAN);
            if (ch.read(h) < 8) break;
            h.flip();
            int sz = h.getInt();
            byte[] t = new byte[4];
            h.get(t);
            String type = new String(t, "US_ASCII");
            if (sz == 1) {
                ByteBuffer ext = ByteBuffer.allocate(8);
                ch.read(ext);
                ext.flip();
                sz = (int) ext.getLong();
            }
            if (sz <= 0) break;
            if (type.equals("trak")) {
                findStcoInTrak(ch, p, sz, stcoOffsets);
            }
            p += sz;
        }
        for (Long stcoPos : stcoOffsets) {
            ch.position(stcoPos + 4);
            ByteBuffer countBuf = ByteBuffer.allocate(4);
            countBuf.order(ByteOrder.BIG_ENDIAN);
            ch.read(countBuf);
            countBuf.flip();
            int count = countBuf.getInt();
            for (int i = 0; i < count; i++) {
                long off = stcoPos + 8 + i * 4;
                ch.position(off);
                ByteBuffer ob = ByteBuffer.allocate(4);
                ob.order(ByteOrder.BIG_ENDIAN);
                ch.read(ob);
                ob.flip();
                int oldVal = ob.getInt();
                ch.position(off);
                ByteBuffer nb = ByteBuffer.allocate(4);
                nb.order(ByteOrder.BIG_ENDIAN);
                nb.putInt(oldVal + delta);
                nb.flip();
                ch.write(nb);
            }
        }
    }

    private static void findStcoInTrak(FileChannel ch, long trakStart, int trakSize, List<Long> out) throws IOException {
        long p = trakStart + 8;
        long end = trakStart + trakSize;
        while (p < end - 8) {
            ch.position(p);
            ByteBuffer h = ByteBuffer.allocate(8);
            h.order(ByteOrder.BIG_ENDIAN);
            if (ch.read(h) < 8) break;
            h.flip();
            int sz = h.getInt();
            byte[] t = new byte[4];
            h.get(t);
            String type = new String(t, "US_ASCII");
            if (sz == 1) {
                ByteBuffer ext = ByteBuffer.allocate(8);
                ch.read(ext);
                ext.flip();
                sz = (int) ext.getLong();
            }
            if (sz <= 0) break;
            if (type.equals("mdia")) {
                findStcoInMdia(ch, p, sz, out);
            }
            p += sz;
        }
    }

    private static void findStcoInMdia(FileChannel ch, long mdiaStart, int mdiaSize, List<Long> out) throws IOException {
        long p = mdiaStart + 8;
        long end = mdiaStart + mdiaSize;
        while (p < end - 8) {
            ch.position(p);
            ByteBuffer h = ByteBuffer.allocate(8);
            h.order(ByteOrder.BIG_ENDIAN);
            if (ch.read(h) < 8) break;
            h.flip();
            int sz = h.getInt();
            byte[] t = new byte[4];
            h.get(t);
            String type = new String(t, "US_ASCII");
            if (sz == 1) {
                ByteBuffer ext = ByteBuffer.allocate(8);
                ch.read(ext);
                ext.flip();
                sz = (int) ext.getLong();
            }
            if (sz <= 0) break;
            if (type.equals("minf")) {
                findStcoInMinf(ch, p, sz, out);
            }
            p += sz;
        }
    }

    private static void findStcoInMinf(FileChannel ch, long minfStart, int minfSize, List<Long> out) throws IOException {
        long p = minfStart + 8;
        long end = minfStart + minfSize;
        while (p < end - 8) {
            ch.position(p);
            ByteBuffer h = ByteBuffer.allocate(8);
            h.order(ByteOrder.BIG_ENDIAN);
            if (ch.read(h) < 8) break;
            h.flip();
            int sz = h.getInt();
            byte[] t = new byte[4];
            h.get(t);
            String type = new String(t, "US_ASCII");
            if (sz == 1) {
                ByteBuffer ext = ByteBuffer.allocate(8);
                ch.read(ext);
                ext.flip();
                sz = (int) ext.getLong();
            }
            if (sz <= 0) break;
            if (type.equals("stbl")) {
                findStcoInStbl(ch, p, sz, out);
            }
            p += sz;
        }
    }

    private static void findStcoInStbl(FileChannel ch, long stblStart, int stblSize, List<Long> out) throws IOException {
        long p = stblStart + 8;
        long end = stblStart + stblSize;
        while (p < end - 8) {
            ch.position(p);
            ByteBuffer h = ByteBuffer.allocate(8);
            h.order(ByteOrder.BIG_ENDIAN);
            if (ch.read(h) < 8) break;
            h.flip();
            int sz = h.getInt();
            byte[] t = new byte[4];
            h.get(t);
            String type = new String(t, "US_ASCII");
            if (sz == 1) {
                ByteBuffer ext = ByteBuffer.allocate(8);
                ch.read(ext);
                ext.flip();
                sz = (int) ext.getLong();
            }
            if (sz <= 0) break;
            if (type.equals("stco")) {
                out.add(p + 8);  // offset of count field
            }
            p += sz;
        }
    }
}
