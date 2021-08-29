package com.capstone.pkes;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class dataencryption {

    static SecureRandom random = new SecureRandom();//RNG for iv and salt

    //generates hash key of required length using given salt and text key
    static byte[] create_hash_of_textkey(String keytext, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeySpec spec = new PBEKeySpec(keytext.toCharArray(), salt, 65536, 128);
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        return f.generateSecret(spec).getEncoded();
    }


    //generates the encryption key for AES using given key of length 128
    static byte[] generate_encryptionkey(byte[] keyBytes) {
        SecretKey key = new SecretKeySpec(keyBytes, "AES");
        return key.getEncoded();
    }

    //encrypts the data takes inputs of message text, key and iv
    static byte[] encrypt(String text, byte[] key, byte[] IV)
    {
        byte[] plaintext = text.getBytes(StandardCharsets.UTF_8);
        try {
            //Get Cipher Instance
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            //Create SecretKeySpec
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            //Create IvParameterSpec
            IvParameterSpec ivSpec = new IvParameterSpec(IV);
            //Initialize Cipher for ENCRYPT_MODE
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            //Perform Encryption
            return cipher.doFinal(plaintext);
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }


    //decrypts the data takes inputs of encrypted text, key and iv
    static String decrypt(byte[] cipherText, byte[] key, byte[] IV)
    {
        //Get Cipher Instance
        Cipher cipher;
        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            //Create SecretKeySpec
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            //Create IvParameterSpec
            IvParameterSpec ivSpec = new IvParameterSpec(IV);
            //Initialize Cipher for DECRYPT_MODE
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            //Perform Decryption
            byte[] decryptedText = cipher.doFinal(cipherText);
            return new String(decryptedText,StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
