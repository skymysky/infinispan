package org.infinispan.commands;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.infinispan.Cache;
import org.infinispan.commands.remote.BaseRpcCommand;
import org.infinispan.commons.time.TimeService;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.statetransfer.StateTransferLock;
import org.infinispan.topology.CacheTopology;
import org.infinispan.util.ByteString;
import org.infinispan.util.concurrent.CompletableFutures;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * Command to create/start a cache on a subset of Infinispan cluster nodes
 * @author Vladimir Blagojevic
 * @since 5.2
 */
public class CreateCacheCommand extends BaseRpcCommand implements InitializableCommand {

   private static final Log log = LogFactory.getLog(CreateCacheCommand.class);
   public static final byte COMMAND_ID = 29;

   private EmbeddedCacheManager cacheManager;
   private String cacheNameToCreate;
   private String cacheConfigurationName;
   private int expectedMembers;

   private CreateCacheCommand() {
      super(null);
   }

   public CreateCacheCommand(ByteString ownerCacheName) {
      super(ownerCacheName);
   }

   public CreateCacheCommand(ByteString ownerCacheName, String cacheNameToCreate, String cacheConfigurationName) {
      this(ownerCacheName, cacheNameToCreate, cacheConfigurationName, 0);
   }

   public CreateCacheCommand(ByteString cacheName, String cacheNameToCreate, String cacheConfigurationName,
                             int expectedMembers) {
      super(cacheName);
      this.cacheNameToCreate = cacheNameToCreate;
      this.cacheConfigurationName = cacheConfigurationName;
      this.expectedMembers = expectedMembers;
   }

   @Override
   public void init(ComponentRegistry componentRegistry, boolean isRemote) {
      this.cacheManager = componentRegistry.getGlobalComponentRegistry().getCacheManager();
   }

   @Override
   public CompletableFuture<Object> invokeAsync() throws Throwable {
      if (cacheConfigurationName == null) {
         throw new NullPointerException("Cache configuration name is required");
      }

      Configuration cacheConfig = cacheManager.getCacheConfiguration(cacheConfigurationName);
      if (cacheConfig == null) {
         throw new IllegalStateException(
               "Cache configuration " + cacheConfigurationName + " is not defined on node " +
               this.cacheManager.getAddress());
      }

      cacheManager.defineConfiguration(cacheNameToCreate, cacheConfig);
      Cache<Object, Object> cache = cacheManager.getCache(cacheNameToCreate);
      waitForCacheToStabilize(cache, cacheConfig);
      log.debugf("Defined and started cache %s", cacheNameToCreate);
      return CompletableFutures.completedNull();
   }

   protected void waitForCacheToStabilize(Cache<Object, Object> cache, Configuration cacheConfig)
         throws InterruptedException {
      ComponentRegistry componentRegistry = cache.getAdvancedCache().getComponentRegistry();
      DistributionManager distributionManager = componentRegistry.getDistributionManager();
      StateTransferLock stateTransferLock = componentRegistry.getStateTransferLock();
      TimeService timeService = componentRegistry.getTimeService();

      long endTime = timeService.expectedEndTime(cacheConfig.clustering().stateTransfer().timeout(),
            TimeUnit.MILLISECONDS);
      CacheTopology cacheTopology = distributionManager.getCacheTopology();
      while (cacheTopology.getMembers().size() < expectedMembers || cacheTopology.getPendingCH() != null) {
         long remainingTime = timeService.remainingTime(endTime, TimeUnit.NANOSECONDS);
         try {
            stateTransferLock.waitForTopology(cacheTopology.getTopologyId() + 1, remainingTime,
                  TimeUnit.NANOSECONDS);
         } catch (TimeoutException ignored) {
            throw log.creatingTmpCacheTimedOut(cacheNameToCreate, cacheManager.getAddress());
         }
         cacheTopology = distributionManager.getCacheTopology();
      }
   }

   @Override
   public byte getCommandId() {
      return COMMAND_ID;
   }

   @Override
   public void writeTo(ObjectOutput output) throws IOException {
      output.writeUTF(cacheNameToCreate);
      output.writeUTF(cacheConfigurationName);
      output.writeInt(expectedMembers);
   }

   @Override
   public void readFrom(ObjectInput input) throws IOException, ClassNotFoundException {
      cacheNameToCreate = input.readUTF();
      cacheConfigurationName = input.readUTF();
      expectedMembers = input.readInt();
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result
               + ((cacheConfigurationName == null) ? 0 : cacheConfigurationName.hashCode());
      result = prime * result + ((cacheNameToCreate == null) ? 0 : cacheNameToCreate.hashCode());
      return result;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (obj == null) {
         return false;
      }
      if (!(obj instanceof CreateCacheCommand)) {
         return false;
      }
      CreateCacheCommand other = (CreateCacheCommand) obj;
      if (cacheConfigurationName == null) {
         if (other.cacheConfigurationName != null) {
            return false;
         }
      } else if (!cacheConfigurationName.equals(other.cacheConfigurationName)) {
         return false;
      }
      if (cacheNameToCreate == null) {
         if (other.cacheNameToCreate != null) {
            return false;
         }
      } else if (!cacheNameToCreate.equals(other.cacheNameToCreate)) {
         return false;
      }
      return this.expectedMembers == other.expectedMembers;
   }

   @Override
   public String toString() {
      return "CreateCacheCommand{" +
            "cacheManager=" + cacheManager +
            ", cacheNameToCreate='" + cacheNameToCreate + '\'' +
            ", cacheConfigurationName='" + cacheConfigurationName + '\'' +
            ", expectedMembers=" + expectedMembers +
            '}';
   }

   @Override
   public boolean isReturnValueExpected() {
      return true;
   }

   @Override
   public boolean canBlock() {
      return true;
   }
}
