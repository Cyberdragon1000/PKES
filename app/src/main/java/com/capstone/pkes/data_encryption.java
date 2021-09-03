package com.capstone.pkes;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class data_encryption {

    private static final String TAG = "PKES-data_encryption";

    static String key = Constants.SECRET_SHARED_KEY;

    //generates the encryption key for AES using given key of length 256
    static void generate_key() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        SecretKey encryption_key = keyGen.generateKey();
        key = Base64.encodeToString(encryption_key.getEncoded(), Base64.DEFAULT);
        Log.d(TAG, "generate_key: " + key);
    }

    //encrypts the data takes inputs of message text and key
    static String encrypt(String text)
    {

        String encryptedText = "";
        if (text == null || key == null)
            return encryptedText;
        // decode the base64 encoded string
        byte[] decodedKey = Base64.decode(key, Base64.DEFAULT);
        // rebuild key using SecretKeySpec
        SecretKey encryption_Key = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
        try {
            //Get Cipher Instance
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            //Initialize Cipher for ENCRYPT_MODE
            cipher.init(Cipher.ENCRYPT_MODE, encryption_Key, new SecureRandom());
            //Perform Encryption
            byte[] encryptedBytes = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));

            //take IV from this cipher
            byte[] iv = cipher.getIV();

            //append Initiation Vector as a prefix to use it during decryption:
            byte[] combinedPayload = new byte[iv.length + encryptedBytes.length];

            //populate payload with prefix IV and encrypted data
            System.arraycopy(iv, 0, combinedPayload, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combinedPayload, iv.length, encryptedBytes.length);

            encryptedText = Base64.encodeToString(combinedPayload, Base64.DEFAULT);

        }catch (NoSuchAlgorithmException | BadPaddingException | NoSuchPaddingException | IllegalBlockSizeException | InvalidKeyException e){
            e.printStackTrace();
        }
        return encryptedText;
    }


    //decrypts the data takes inputs of encrypted text and key
    static String decrypt(String encryptedString)
    {

        String decryptedText = "";
        if (encryptedString == null || key == null)
            return decryptedText;

        byte[] decodedKey = Base64.decode(key, Base64.DEFAULT);
        // rebuild key using SecretKeySpec
        SecretKey encryption_Key = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");

        //Get Cipher Instance
        Cipher cipher;
        try {
            //separate prefix with IV from the rest of encrypted data
            byte[] encryptedPayload = Base64.decode(encryptedString, Base64.DEFAULT);
            byte[] iv = new byte[16];
            byte[] encryptedBytes = new byte[encryptedPayload.length - iv.length];

            //populate iv with bytes:
            System.arraycopy(encryptedPayload, 0, iv, 0, 16);

            //populate encryptedBytes with bytes:
            System.arraycopy(encryptedPayload, iv.length, encryptedBytes, 0, encryptedBytes.length);

            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

            //Initialize Cipher for DECRYPT_MODE
            cipher.init(Cipher.DECRYPT_MODE, encryption_Key, new IvParameterSpec(iv));
            //Perform Decryption
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            decryptedText = new String(decryptedBytes);

        } catch (NoSuchAlgorithmException | BadPaddingException | NoSuchPaddingException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            e.printStackTrace();
        }
        return  decryptedText;
    }

}
