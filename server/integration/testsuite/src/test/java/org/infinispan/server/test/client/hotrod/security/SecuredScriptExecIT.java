package org.infinispan.server.test.client.hotrod.security;

import static org.infinispan.server.test.client.hotrod.security.HotRodSaslAuthTestBase.ADMIN_LOGIN;
import static org.infinispan.server.test.client.hotrod.security.HotRodSaslAuthTestBase.ADMIN_PASSWD;
import static org.infinispan.server.test.client.hotrod.security.HotRodSaslAuthTestBase.EXECUTOR_LOGIN;
import static org.infinispan.server.test.client.hotrod.security.HotRodSaslAuthTestBase.EXECUTOR_PASSWORD;
import static org.infinispan.server.test.client.hotrod.security.HotRodSaslAuthTestBase.WRITER_LOGIN;
import static org.infinispan.server.test.client.hotrod.security.HotRodSaslAuthTestBase.WRITER_PASSWD;
import static org.infinispan.server.test.task.servertask.LocalAuthTestServerTask.CACHE_NAME;
import static org.infinispan.test.TestingUtil.loadFileAsString;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.infinispan.arquillian.core.InfinispanResource;
import org.infinispan.arquillian.core.RemoteInfinispanServer;
import org.infinispan.arquillian.core.RunningServer;
import org.infinispan.arquillian.core.WithRunningServer;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.exceptions.HotRodClientException;
import org.infinispan.scripting.ScriptingManager;
import org.infinispan.server.test.category.Security;
import org.infinispan.server.test.util.ClassRemoteCacheManager;
import org.infinispan.server.test.util.security.SecurityConfigurationHelper;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * Tests script execution over HotRod client on secured cache.
 *
 * @author vjuranek
 * @since 9.0
 */
@RunWith(Arquillian.class)
@Category(Security.class)
@WithRunningServer({@RunningServer(name = "hotrodAuthClustered"), @RunningServer(name = "hotrodAuthClustered-2")})
public class SecuredScriptExecIT {

   @InfinispanResource("hotrodAuthClustered")
   RemoteInfinispanServer server1;

   @InfinispanResource("hotrodAuthClustered-2")
   RemoteInfinispanServer server2;

   private RemoteCacheManager adminRCM = null;

   @ClassRule
   public static ClassRemoteCacheManager classRCM = new ClassRemoteCacheManager();

   @Before
   public void prepareAdminRCM() throws Exception {
      SecurityConfigurationHelper config = new SecurityConfigurationHelper("DIGEST-MD5");
      config.forIspnServer(server1).withServerName("node0");
      config.forCredentials(ADMIN_LOGIN, ADMIN_PASSWD);
      adminRCM = classRCM.cacheRemoteCacheManager(config);
   }

   private void uploadScript(String... scripts) throws IOException {
      RemoteCache scriptCache = adminRCM.getCache(ScriptingManager.SCRIPT_CACHE);
      for (String script : scripts) {
         try (InputStream in = this.getClass().getClassLoader().getResourceAsStream(script)) {
            scriptCache.put(script, loadFileAsString(in));
         }
      }
   }

   @Test
   public void testExecuteScript() throws Exception {
      uploadScript("test.js");

      SecurityConfigurationHelper config = new SecurityConfigurationHelper("DIGEST-MD5");
      config.forIspnServer(server1).withServerName("node0");
      config.forCredentials(EXECUTOR_LOGIN, EXECUTOR_PASSWORD);
      RemoteCacheManager execRCM = new RemoteCacheManager(config.build(), true);
      RemoteCache cache = execRCM.getCache(CACHE_NAME);

      assertEquals(cache.size(), 0);
      Map<String, String> params = new HashMap<>();
      params.put("key", "test_key");
      params.put("value", "test_value");
      int result = (Integer) cache.execute("test.js", params);
      assertEquals(result, 1);
      assertEquals(cache.size(), 1);
      assertEquals(cache.get("test_key"), "test_value");

      execRCM.stop();
   }

   @Test(expected = HotRodClientException.class)
   public void testExecuteScriptWithoutExecPerm() throws IOException {
      uploadScript("test.js");

      SecurityConfigurationHelper config = new SecurityConfigurationHelper("DIGEST-MD5");
      config.forIspnServer(server1).withServerName("node0");
      config.forCredentials(WRITER_LOGIN, WRITER_PASSWD);
      RemoteCacheManager writerRCM = new RemoteCacheManager(config.build(), true);
      RemoteCache cache = writerRCM.getCache(CACHE_NAME);

      Map<String, String> params = new HashMap<>();
      params.put("key", "test_key");
      params.put("value", "test_value");
      try {
         cache.execute("test.js", params);
      } finally {
         writerRCM.stop();
      }
   }

   @Test(expected = HotRodClientException.class)
   public void testUploadScriptWithoutAdminPerm() {
      SecurityConfigurationHelper config = new SecurityConfigurationHelper("DIGEST-MD5");
      config.forIspnServer(server1).withServerName("node0");
      config.forCredentials(EXECUTOR_LOGIN, EXECUTOR_PASSWORD);
      RemoteCacheManager execRCM = new RemoteCacheManager(config.build(), true);
      RemoteCache scriptCache = execRCM.getCache(ScriptingManager.SCRIPT_CACHE);

      try {
         scriptCache.put("shouldNotPass", "1+1");
      } finally {
         execRCM.stop();
      }
   }

}
