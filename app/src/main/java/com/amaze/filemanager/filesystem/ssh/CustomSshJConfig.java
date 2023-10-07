package com.amaze.filemanager.filesystem.ssh;

import net.schmizz.sshj.DefaultConfig;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.Security;

/**
 * SSHJ {@link net.schmizz.sshj.Config} for our own use.
 *
 * Borrowed from the original AndroidConfig but also uses vanilla BouncyCastle from the start.
 *
 * @see net.schmizz.sshj.Config
 * @see net.schmizz.sshj.AndroidConfig
 */
public class CustomSshJConfig extends DefaultConfig {

    /**
     * This is where we differ from the original AndroidConfig. Found that it only works if we remove
     * BouncyCastle bundled with Android before registering our BouncyCastle provider.
     */
    public static void init() {
        Security.removeProvider("BC");
        Security.insertProviderAt(new BouncyCastleProvider(), 0);
    }
}

