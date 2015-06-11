package org.ovirt.engine.extension.aaa.jdbc.core;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.apache.commons.codec.binary.Base64;

public class PasswordStore {
    private final String algorithm;
    private final int length;
    private final int iterations;
    private final String randomProvider;

    public PasswordStore(String algorithm, int length, int iterations, String randomProvider) {
        this.algorithm = algorithm;
        this.length = length;
        this.iterations = iterations;
        this.randomProvider = randomProvider;
    }

    public static boolean check(String current, String password) throws GeneralSecurityException {
        String[] comps = current.split("\\|");
        if (comps.length != 5 || !"1".equals(comps[0])) {
            throw new IllegalArgumentException("Invalid current password, length");
        }
        byte[] salt = new Base64(0).decode(comps[2]);
        return Arrays.equals(
            new Base64(0).decode(comps[4]),
            SecretKeyFactory.getInstance(comps[1]).generateSecret(
                new PBEKeySpec(
                    password.toCharArray(),
                    salt,
                    Integer.parseInt(comps[3]),
                    salt.length*8
                )
            ).getEncoded()
        );
    }

    public String encode(String password) throws GeneralSecurityException {
        byte[] salt = new byte[length/8];
        SecureRandom.getInstance(randomProvider == null ? "SHA1PRNG" : randomProvider).nextBytes(salt);
        return String.format(
            "1|%s|%s|%s|%s",
            algorithm,
            new Base64(0).encodeToString(salt),
            iterations,
            new Base64(0).encodeToString(
                SecretKeyFactory.getInstance(algorithm).generateSecret(
                    new PBEKeySpec(
                        password.toCharArray(),
                        salt,
                        iterations,
                        salt.length*8
                    )
                ).getEncoded()
            )
        );
    }

    public static void main(String... args) throws Exception {
        for (String algo : new String[] { "PBEWithMD5AndDES", "PBEWithMD5AndTripleDES", "PBKDF2WithHmacSHA1" }) {
            PasswordStore ph = new PasswordStore(algo, 256, 2000, null);
            String p = ph.encode(args[0]);
            System.out.println(p);
            System.out.println(check(p, args[0]));
        }
    }
}
