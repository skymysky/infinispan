package org.infinispan.server.security;

import static org.junit.Assert.assertEquals;

import javax.net.ssl.SSLHandshakeException;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.exceptions.TransportException;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.server.test.InfinispanServerRule;
import org.infinispan.server.test.InfinispanServerTestConfiguration;
import org.infinispan.server.test.InfinispanServerTestMethodRule;
import org.infinispan.test.Exceptions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Tristan Tarrant &lt;tristan@infinispan.org&gt;
 * @since 10.0
 **/

public class AuthenticationCertTest {

   @ClassRule
   public static InfinispanServerRule SERVERS = new InfinispanServerRule(new InfinispanServerTestConfiguration("configuration/AuthenticationServerTrustTest.xml"));

   @Rule
   public InfinispanServerTestMethodRule SERVER_TEST = new InfinispanServerTestMethodRule(SERVERS);

   @Test
   public void testTrustedCertificate() {
      ConfigurationBuilder builder = new ConfigurationBuilder();
      builder.security()
            .ssl()
               .trustStoreFileName(SERVERS.getServerDriver().getCertificateFile("ca").getAbsolutePath())
               .trustStorePassword("secret".toCharArray())
               .keyStoreFileName(SERVERS.getServerDriver().getCertificateFile("admin").getAbsolutePath())
               .keyStorePassword("secret".toCharArray())
            .authentication()
               .saslMechanism("EXTERNAL")
               .serverName("infinispan")
               .realm("default");

      RemoteCache<String, String> cache = SERVER_TEST.getHotRodCache(builder, CacheMode.DIST_SYNC);
      cache.put("k1", "v1");
      assertEquals(1, cache.size());
      assertEquals("v1", cache.get("k1"));
   }

   @Test
   public void testUntrustedCertificate() {
      ConfigurationBuilder builder = new ConfigurationBuilder();
      builder.security()
            .ssl()
               .trustStoreFileName(SERVERS.getServerDriver().getCertificateFile("ca").getAbsolutePath())
               .trustStorePassword("secret".toCharArray())
               .keyStoreFileName(SERVERS.getServerDriver().getCertificateFile("untrusted").getAbsolutePath())
               .keyStorePassword("secret".toCharArray())
            .authentication()
               .saslMechanism("EXTERNAL")
               .serverName("infinispan")
               .realm("default");

      Exceptions.expectException(TransportException.class, SSLHandshakeException.class, () -> SERVER_TEST.getHotRodCache(builder, CacheMode.DIST_SYNC));
   }
}
