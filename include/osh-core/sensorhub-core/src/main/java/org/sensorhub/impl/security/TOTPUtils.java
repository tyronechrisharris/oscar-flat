package org.sensorhub.impl.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class TOTPUtils
{
    private static final String ALGORITHM = "HmacSHA1";
    private static final int DIGITS = 6;
    private static final int PERIOD = 30;

    public static String generateSecret()
    {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[20]; // 20 bytes = 160 bits (Recommended by RFC 4226/6238)
        random.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public static boolean validateCode(String secret, String code)
    {
        if (secret == null || code == null || code.length() != DIGITS)
            return false;

        long time = System.currentTimeMillis() / 1000 / PERIOD;

        // Check current interval and adjacent ones for clock drift
        for (int i = -1; i <= 1; i++)
        {
            try
            {
                String generated = generateTOTP(secret, time + i);
                if (generated.equals(code))
                    return true;
            }
            catch (Exception e)
            {
                // ignore
            }
        }
        return false;
    }

    public static String getQRUrl(String user, String secret)
    {
        // otpauth://totp/OpenSensorHub:user@example.com?secret=SECRET&issuer=OpenSensorHub
        try
        {
            return String.format("otpauth://totp/OpenSensorHub:%s?secret=%s&issuer=OpenSensorHub",
                    URLEncoder.encode(user, StandardCharsets.UTF_8.toString()),
                    secret);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    protected static String generateTOTP(String secret, long time) throws NoSuchAlgorithmException, InvalidKeyException
    {
        byte[] key = decodeBase32(secret);
        byte[] data = new byte[8];
        long value = time;
        for (int i = 8; i-- > 0; value >>>= 8)
        {
            data[i] = (byte) value;
        }

        SecretKeySpec signKey = new SecretKeySpec(key, ALGORITHM);
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(signKey);
        byte[] hash = mac.doFinal(data);

        int offset = hash[hash.length - 1] & 0xF;

        long truncatedHash = 0;
        for (int i = 0; i < 4; ++i)
        {
            truncatedHash <<= 8;
            truncatedHash |= (hash[offset + i] & 0xFF);
        }

        truncatedHash &= 0x7FFFFFFF;
        truncatedHash %= 1000000;

        return String.format("%06d", truncatedHash);
    }

    // Simple Base32 implementation
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int[] DECODE_TABLE;

    static
    {
        DECODE_TABLE = new int[128];
        for (int i = 0; i < DECODE_TABLE.length; i++) DECODE_TABLE[i] = -1;
        for (int i = 0; i < ALPHABET.length; i++) DECODE_TABLE[ALPHABET[i]] = i;
        DECODE_TABLE['='] = -1;
    }

    private static String encodeBase32(byte[] data)
    {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int next = 0;
        int bitsLeft = 0;

        while (bitsLeft > 0 || next < data.length)
        {
            if (bitsLeft < 5)
            {
                if (next < data.length)
                {
                    buffer <<= 8;
                    buffer |= (data[next++] & 0xFF);
                    bitsLeft += 8;
                }
                else
                {
                    int pad = 5 - bitsLeft;
                    buffer <<= pad;
                    bitsLeft += pad;
                }
            }

            int index = 0x1F & (buffer >> (bitsLeft - 5));
            bitsLeft -= 5;
            sb.append(ALPHABET[index]);
        }
        return sb.toString();
    }

    private static byte[] decodeBase32(String secret)
    {
        secret = secret.trim().replace(" ", "").toUpperCase();
        // Remove padding if any
        secret = secret.replace("=", "");

        int buffer = 0;
        int bitsLeft = 0;
        int count = 0;
        // Approximation of size
        byte[] temp = new byte[secret.length() * 5 / 8];

        for (char c : secret.toCharArray())
        {
            if (c >= DECODE_TABLE.length || DECODE_TABLE[c] == -1)
                continue; // ignore invalid chars

            buffer <<= 5;
            buffer |= DECODE_TABLE[c] & 0x1F;
            bitsLeft += 5;

            if (bitsLeft >= 8)
            {
                temp[count++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }

        // resize to actual
        byte[] result = new byte[count];
        System.arraycopy(temp, 0, result, 0, count);
        return result;
    }
}
