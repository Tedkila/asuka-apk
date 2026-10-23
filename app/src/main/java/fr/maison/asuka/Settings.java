package fr.maison.asuka;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

final class Settings {
    final SharedPreferences p;
    Settings(Context context) { p = context.getSharedPreferences("asuka", Context.MODE_PRIVATE); }
    String server() { return p.getString("server", "http://192.168.1.26:8765/"); }
    String room() { return p.getString("room", "salle de bain"); }
    boolean hasPin() { return p.contains("pin"); }
    void setPin(String pin) throws Exception {
        byte[] salt = new byte[24]; new SecureRandom().nextBytes(salt);
        p.edit().putString("salt", enc(salt)).putString("pin", enc(derive(pin, salt))).apply();
    }
    boolean checkPin(String pin) {
        try { return MessageDigest.isEqual(derive(pin, dec(p.getString("salt", ""))), dec(p.getString("pin", ""))); }
        catch (Exception e) { return false; }
    }
    private byte[] derive(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 120000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias("AsukaPrivate")) return (SecretKey) store.getKey("AsukaPrivate", null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder("AsukaPrivate", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    void putSecret(String name, String value) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key());
        p.edit().putString(name, enc(c.getIV()) + ":" + enc(c.doFinal(value.getBytes(StandardCharsets.UTF_8)))).apply();
    }
    String secret(String name) throws Exception {
        String stored = p.getString(name, ""); if (stored.isEmpty()) return "";
        String[] parts = stored.split(":"); Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, dec(parts[0])));
        return new String(c.doFinal(dec(parts[1])), StandardCharsets.UTF_8);
    }
    static String enc(byte[] b) { return Base64.encodeToString(b, Base64.NO_WRAP); }
    static byte[] dec(String s) { return Base64.decode(s, Base64.NO_WRAP); }
}
