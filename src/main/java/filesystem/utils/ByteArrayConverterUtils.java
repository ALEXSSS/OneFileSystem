package filesystem.utils;

import java.nio.ByteBuffer;

public class ByteArrayConverterUtils {
    public static byte[] getByteArrayFromInt(int val) {
        byte[] result = new byte[4];

        result[0] = (byte) ((val >>> 24) & 0xFF);
        result[1] = (byte) ((val >>> 16) & 0xFF);
        result[2] = (byte) ((val >>> 8) & 0xFF);
        result[3] = (byte) (val & 0xFF);

        return result;
    }

    public static int intFromByteArray(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
                ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8) |
                ((bytes[3] & 0xFF));
    }

    public static byte[] byteArrayFromByte(int val) {
        return new byte[]{(byte) val};
    }


    public static byte[] mergeByteArrays(byte[]... arrs) {
        int size = 0;
        for (byte[] value : arrs) {
            size += value.length;
        }

        byte[] result = new byte[size];

        ByteBuffer buff = ByteBuffer.wrap(result);

        for (byte[] bytes : arrs) {
            buff.put(bytes);
        }

        return buff.array();
    }

    //    Strings are stored like size and bytes(not \0 byte)
    public static byte[] stringToByteArray(String s) {
        return mergeByteArrays(getByteArrayFromInt(s.length()), s.getBytes());
    }

}
