package ru.amazy.crm.nativeclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-GCM encrypted token, key held by Android Keystore and never committed to the repository. */
final class SecureTokenStore {
    private static final String PREF = "amazy_native_credentials";
    private static final String ALIAS = "amazy_chatwoot_native_access_token_v1";
    private final SharedPreferences prefs;

    SecureTokenStore(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    String server() { return prefs.getString("server", "https://desktop-to6ives.taile101ff.ts.net"); }
    int account() { return prefs.getInt("account", 2); }

    String loadToken() {
        String encrypted = prefs.getString("token_data", null);
        String iv = prefs.getString("token_iv", null);
        if (encrypted == null || iv == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            prefs.edit().remove("token_data").remove("token_iv").apply();
            return null;
        }
    }

    void save(String server, int account, String token) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] bytes = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        boolean ok = prefs.edit()
            .putString("server", server)
            .putInt("account", account)
            .putString("token_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .putString("token_data", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .commit();
        if (!ok) throw new IllegalStateException("Не удалось сохранить настройки");
    }

    void clear() { prefs.edit().remove("token_iv").remove("token_data").apply(); }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        KeyStore.Entry entry = ks.getEntry(ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry)
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
        return generator.generateKey();
    }
}
