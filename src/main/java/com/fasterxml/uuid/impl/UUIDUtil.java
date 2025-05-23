package com.fasterxml.uuid.impl;

import java.util.UUID;

import com.fasterxml.uuid.UUIDTimer;
import com.fasterxml.uuid.UUIDType;

public class UUIDUtil
{
    public final static int BYTE_OFFSET_CLOCK_LO = 0;
    public final static int BYTE_OFFSET_CLOCK_MID = 4;
    public final static int BYTE_OFFSET_CLOCK_HI = 6;

    // note: clock-hi and type occupy same byte (different bits)
    public final static int BYTE_OFFSET_TYPE = 6;

    // similarly, clock sequence and variant are multiplexed
    public final static int BYTE_OFFSET_CLOCK_SEQUENCE = 8;
    public final static int BYTE_OFFSET_VARIATION = 8;

    /**
     * @since 4.1
     */
    private final static UUID NIL_UUID = new UUID(0L, 0L);

    /**
     * @since 4.1
     */
    private final static UUID MAX_UUID = new UUID(-1L, -1L);

    /*
    /**********************************************************************
    /* Construction (can instantiate, although usually not necessary)
    /**********************************************************************
     */

    // note: left public just for convenience; all functionality available
    // via static methods
    public UUIDUtil() { }

    /*
    /**********************************************************************
    /* Static UUID instances
    /**********************************************************************
     */

    /**
     * Accessor for so-call "Nil UUID" (see
     * <a href="https://datatracker.ietf.org/doc/html/rfc9562#name-nil-uuid">RFC 9562, #5.9</a>;
     * one that is all zeroes.
     *
     * @since 4.1
     *
     * @return "Nil" UUID instance
     */
    public static UUID nilUUID() {
        return NIL_UUID;
    }

    /**
     * Accessor for so-call "Max UUID" (see
     * <a href="https://datatracker.ietf.org/doc/html/rfc9562#name-max-uuid">RFC-9562, #5.10</a>);
     * one that is all one bits
     *
     * @since 4.1
     *
     * @return "Nil" UUID instance
     */
    public static UUID maxUUID() {
        return MAX_UUID;
    }

    /*
    /**********************************************************************
    /* Factory methods
    /**********************************************************************
     */
	
    /**
     * Factory method for creating UUIDs from the canonical string
     * representation.
     *
     * @param id String that contains the canonical representation of
     *   the UUID to build; 36-char string (see UUID specs for details).
     *   Hex-chars may be in upper-case too; UUID class will always output
     *   them in lowercase.
     */
    public static UUID uuid(String id)
    {
        if (id == null) {
            throw new NullPointerException();
        }
        if (id.length() != 36) {
            throw new NumberFormatException("UUID has to be represented by the standard 36-char representation");
        }

        long lo, hi;
        lo = hi = 0;
        
        for (int i = 0, j = 0; i < 36; ++j) {
        	
            // Need to bypass hyphens:
            switch (i) {
            case 8:
            case 13:
            case 18:
            case 23:
                if (id.charAt(i) != '-') {
                    throw new NumberFormatException("UUID has to be represented by the standard 36-char representation");
                }
                ++i;
            }
            int curr;
            char c = id.charAt(i);

            if (c >= '0' && c <= '9') {
                curr = (c - '0');
            } else if (c >= 'a' && c <= 'f') {
                curr = (c - 'a' + 10);
            } else if (c >= 'A' && c <= 'F') {
                curr = (c - 'A' + 10);
            } else {
                throw new NumberFormatException("Non-hex character at #"+i+": '"+c
                        +"' (value 0x"+Integer.toHexString(c)+")");
            }
            curr = (curr << 4);

            c = id.charAt(++i);

            if (c >= '0' && c <= '9') {
                curr |= (c - '0');
            } else if (c >= 'a' && c <= 'f') {
                curr |= (c - 'a' + 10);
            } else if (c >= 'A' && c <= 'F') {
                curr |= (c - 'A' + 10);
            } else {
                throw new NumberFormatException("Non-hex character at #"+i+": '"+c
                        +"' (value 0x"+Integer.toHexString(c)+")");
            }
            if (j < 8) {
            	hi = (hi << 8) | curr;
            } else {
            	lo = (lo << 8) | curr;
            }
            ++i;
        }		
        return new UUID(hi, lo);
    }

    /**
     * Factory method for constructing {@link java.util.UUID} instance from given
     * 16 bytes.
     * NOTE: since absolutely no validation is done for contents, this method should
     * only be used if contents are known to be valid.
     */
    public static UUID uuid(byte[] bytes)
    {
        _checkUUIDByteArray(bytes, 0);
        long l1 = gatherLong(bytes, 0);
        long l2 = gatherLong(bytes, 8);
        return new UUID(l1, l2);
    }

    /**
     * Factory method for constructing {@link java.util.UUID} instance from given
     * 16 bytes.
     * NOTE: since absolutely no validation is done for contents, this method should
     * only be used if contents are known to be valid.
     * 
     * @param bytes Array that contains sequence of 16 bytes that contain a valid UUID
     * @param offset Offset of the first of 16 bytes
     */
    public static UUID uuid(byte[] bytes, int offset)
    {
        _checkUUIDByteArray(bytes, offset);
        return new UUID(gatherLong(bytes, offset), gatherLong(bytes, offset+8));
    }

    /**
     * Helper method for constructing UUID instances with appropriate type
     */
    public static UUID constructUUID(UUIDType type, byte[] uuidBytes)
    {
        // first, ensure type is ok
        int b = uuidBytes[BYTE_OFFSET_TYPE] & 0xF; // clear out high nibble
        b |= type.raw() << 4;
        uuidBytes[BYTE_OFFSET_TYPE] = (byte) b;
        // second, ensure variant is properly set too
        b = uuidBytes[UUIDUtil.BYTE_OFFSET_VARIATION] & 0x3F; // remove 2 MSB
        b |= 0x80; // set as '10'
        uuidBytes[BYTE_OFFSET_VARIATION] = (byte) b;
        return uuid(uuidBytes);
    }
    
    public static UUID constructUUID(UUIDType type, long l1, long l2)
    {
        // first, ensure type is ok
        l1 &= ~0xF000L; // remove high nibble of 6th byte
        l1 |= (long) (type.raw() << 12);
        // second, ensure variant is properly set too (8th byte; most-sig byte of second long)
        l2 = ((l2 << 2) >>> 2); // remove 2 MSB
        l2 |= (2L << 62); // set 2 MSB to '10'
        return new UUID(l1, l2);
    }

    public static long initUUIDFirstLong(long l1, UUIDType type)
    {
        return initUUIDFirstLong(l1, type.raw());
    }

    public static long initUUIDFirstLong(long l1, int rawType)
    {
        l1 &= ~0xF000L; // remove high nibble of 6th byte
        l1 |= (long) (rawType << 12);
        return l1;
    }
    
    public static long initUUIDSecondLong(long l2)
    {
        l2 = ((l2 << 2) >>> 2); // remove 2 MSB
        l2 |= (2L << 62); // set 2 MSB to '10'
        return l2;
    }
    
    /*
    /***********************************************************************
    /* Type introspection
    /***********************************************************************
     */

    /**
     * Method for determining which type of UUID given UUID is.
     * Returns null if type can not be determined.
     * 
     * @param uuid UUID to check
     * 
     * @return Null if UUID is null or type can not be determined (== invalid UUID);
     *   otherwise type
     */
    public static UUIDType typeOf(UUID uuid)
    {
        if (uuid == null) {
            return null;
        }
        // Ok: so 4 MSB of byte at offset 6...
        long l = uuid.getMostSignificantBits();
        int typeNibble = (((int) l) >> 12) & 0xF;
        switch (typeNibble) {
        case 0:
            // possibly null?
            if (l == 0L && uuid.getLeastSignificantBits() == l) {
                return UUIDType.UNKNOWN;
            }
            break;
        case 1:
            return UUIDType.TIME_BASED;
        case 2:
            return UUIDType.DCE;
        case 3:
            return UUIDType.NAME_BASED_MD5;
        case 4:
            return UUIDType.RANDOM_BASED;
        case 5:
            return UUIDType.NAME_BASED_SHA1;
        case 6:
            return UUIDType.TIME_BASED_REORDERED;
        case 7:
            return UUIDType.TIME_BASED_EPOCH;
        case 8:
            return UUIDType.FREE_FORM;
        }
        // not recognized: return null
        return null;
    }
	
    /*
    /***********************************************************************
    /* Conversions to other types
    /***********************************************************************
     */
	
    public static byte[] asByteArray(UUID uuid)
    {
        long hi = uuid.getMostSignificantBits();
        long lo = uuid.getLeastSignificantBits();
        byte[] result = new byte[16];
        _appendInt((int) (hi >> 32), result, 0);
        _appendInt((int) hi, result, 4);
        _appendInt((int) (lo >> 32), result, 8);
        _appendInt((int) lo, result, 12);
        return result;
    }

    public static void toByteArray(UUID uuid, byte[] buffer) {
        toByteArray(uuid, buffer, 0);
    }

    public static void toByteArray(UUID uuid, byte[] buffer, int offset)
    {
        _checkUUIDByteArray(buffer, offset);
        long hi = uuid.getMostSignificantBits();
        long lo = uuid.getLeastSignificantBits();
        _appendInt((int) (hi >> 32), buffer, offset);
        _appendInt((int) hi, buffer, offset+4);
        _appendInt((int) (lo >> 32), buffer, offset+8);
        _appendInt((int) lo, buffer, offset+12);
    }

    /*
    /******************************************************************************** 
    /* Package helper methods
    /******************************************************************************** 
     */
    
    //private final static long MASK_LOW_INT = 0x0FFFFFFFF;

    protected final static long gatherLong(byte[] buffer, int offset)
    {
        long hi = ((long) _gatherInt(buffer, offset)) << 32;
        //long lo = ((long) _gatherInt(buffer, offset+4)) & MASK_LOW_INT;
        long lo = (((long) _gatherInt(buffer, offset+4)) << 32) >>> 32;
        return hi | lo;
    }
    
    /*
    /******************************************************************************** 
    /* Internal helper methods
    /******************************************************************************** 
     */

    private final static void _appendInt(int value, byte[] buffer, int offset)
    {
        buffer[offset++] = (byte) (value >> 24);
        buffer[offset++] = (byte) (value >> 16);
        buffer[offset++] = (byte) (value >> 8);
        buffer[offset] = (byte) value;
    }
	
    private final static int _gatherInt(byte[] buffer, int offset)
    {
        return (buffer[offset] << 24) | ((buffer[offset+1] & 0xFF) << 16)
            | ((buffer[offset+2] & 0xFF) << 8) | (buffer[offset+3] & 0xFF);
    }

    private final static void _checkUUIDByteArray(byte[] bytes, int offset)
    {
        if (bytes == null) {
            throw new IllegalArgumentException("Invalid byte[] passed: can not be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Invalid offset ("+offset+") passed: can not be negative");
        }
        if ((offset + 16) > bytes.length) {
            throw new IllegalArgumentException("Invalid offset ("+offset+") passed: not enough room in byte array (need 16 bytes)");
        }
    }

    /**
     * Extract 64-bit timestamp from time-based UUIDs (if time-based type);
     * returns 0 for other types.
     *
     * @param uuid uuid timestamp to extract from
     *
     * @return Unix timestamp in milliseconds (since Epoch), or 0 if type does not support timestamps
     *
     * @since 5.0
     */
    public static long extractTimestamp(UUID uuid)
    {
        UUIDType type = typeOf(uuid);
        if (type == null) {
            // Likely null UUID:
            return 0L;
        }
        switch (type) {
            case NAME_BASED_SHA1:
            case UNKNOWN:
            case DCE:
            case RANDOM_BASED:
            case FREE_FORM:
            case NAME_BASED_MD5:
                return 0L;
            case TIME_BASED:
                return UUIDTimer.timestampToEpoch(_getRawTimestampFromUuidV1(uuid));
            case TIME_BASED_REORDERED:
                return UUIDTimer.timestampToEpoch(_getRawTimestampFromUuidV6(uuid));
            case TIME_BASED_EPOCH:
                return _getRawTimestampFromUuidV7(uuid);
            default:
                throw new IllegalArgumentException("Invalid `UUID`: unexpected type " + type);
        }
    }

    /**
     * Get raw timestamp, used to create the UUID v1
     *<p>
     * NOTE: no verification is done to ensure UUID given is of version 1.
     *
     * @param uuid uuid, to extract timestamp from
     * @return timestamp, used to create uuid v1
     */
    static long _getRawTimestampFromUuidV1(UUID uuid) {
        long mostSignificantBits = uuid.getMostSignificantBits();
        mostSignificantBits = mostSignificantBits & 0b1111_1111_1111_1111_1111_1111_1111_1111_1111_1111_1111_1111_1110_1111_1111_1111L;
        long low = mostSignificantBits >>> 32;
        long lowOfHigher = mostSignificantBits & 0xFFFF0000L;
        lowOfHigher = lowOfHigher >>> 16;
        long highOfHigher = mostSignificantBits & 0xFFFFL;
        return highOfHigher << 48 | lowOfHigher << 32 | low;
    }

    /**
     * Get raw timestamp, used to create the UUID v6.
     *<p>
     * NOTE: no verification is done to ensure UUID given is of version 6.
     *
     * @param uuid uuid, to extract timestamp from
     * @return timestamp, used to create uuid v6
     */
    static long _getRawTimestampFromUuidV6(UUID uuid) {
        long mostSignificantBits = uuid.getMostSignificantBits();
        mostSignificantBits = mostSignificantBits & 0b1111_1111_1111_1111_1111_1111_1111_1111_1111_1111_1111_1111_1001_1111_1111_1111L;
        long lowL = mostSignificantBits & 0xFFFL;
        long lowH = mostSignificantBits & 0xFFFF0000L;
        lowH = lowH >>> 16;
        long high = mostSignificantBits & 0xFFFFFFFF00000000L;
        return high >>> 4 | lowH << 12 | lowL;
    }

    static long _getRawTimestampFromUuidV7(UUID uuid) {
        long mostSignificantBits = uuid.getMostSignificantBits();
        mostSignificantBits = mostSignificantBits & 0b1111_1111_1111_1111_1111_1111_1111_1111_1111_1111_1111_1111_1001_1111_1111_1111L;
        return mostSignificantBits >>> 16;
    }
}
