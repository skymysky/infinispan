package org.infinispan.factories;

import org.infinispan.commons.configuration.ClassWhiteList;
import org.infinispan.commons.dataconversion.BinaryEncoder;
import org.infinispan.commons.dataconversion.ByteArrayWrapper;
import org.infinispan.commons.dataconversion.DefaultTranscoder;
import org.infinispan.commons.dataconversion.GenericJbossMarshallerEncoder;
import org.infinispan.commons.dataconversion.GlobalMarshallerEncoder;
import org.infinispan.commons.dataconversion.IdentityEncoder;
import org.infinispan.commons.dataconversion.IdentityWrapper;
import org.infinispan.commons.dataconversion.JavaSerializationEncoder;
import org.infinispan.commons.dataconversion.UTF8Encoder;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;
import org.infinispan.commons.marshall.StreamingMarshaller;
import org.infinispan.commons.marshall.jboss.GenericJBossMarshaller;
import org.infinispan.factories.annotations.ComponentName;
import org.infinispan.factories.annotations.DefaultFactoryFor;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.impl.ComponentRef;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.marshall.core.EncoderRegistry;
import org.infinispan.marshall.core.EncoderRegistryImpl;
import org.infinispan.marshall.persistence.PersistenceMarshaller;

/**
 * Factory for {@link EncoderRegistryImpl} objects.
 *
 * @since 9.1
 */
@DefaultFactoryFor(classes = {EncoderRegistry.class})
public class EncoderRegistryFactory extends AbstractComponentFactory implements AutoInstantiableFactory {
   // Must not start the global marshaller or it will be too late for modules to register their externalizers
   @Inject @ComponentName(KnownComponentNames.INTERNAL_MARSHALLER)
   ComponentRef<StreamingMarshaller> globalMarshaller;
   @Inject @ComponentName(KnownComponentNames.PERSISTENCE_MARSHALLER)
   ComponentRef<PersistenceMarshaller> persistenceMarshaller;
   @Inject EmbeddedCacheManager embeddedCacheManager;

   @Override
   public Object construct(String componentName) {
      EncoderRegistryImpl encoderRegistry = new EncoderRegistryImpl();
      ClassWhiteList classWhiteList = embeddedCacheManager.getClassWhiteList();

      ClassLoader classLoader = globalConfiguration.classLoader();
      GenericJBossMarshaller jBossMarshaller = new GenericJBossMarshaller(classLoader, classWhiteList);
      JavaSerializationMarshaller javaSerializationMarshaller = new JavaSerializationMarshaller(classWhiteList);

      encoderRegistry.registerEncoder(IdentityEncoder.INSTANCE);
      encoderRegistry.registerEncoder(UTF8Encoder.INSTANCE);
      encoderRegistry.registerEncoder(new JavaSerializationEncoder(classWhiteList));
      encoderRegistry.registerEncoder(new BinaryEncoder(globalMarshaller.wired()));
      encoderRegistry.registerEncoder(new GenericJbossMarshallerEncoder(jBossMarshaller));
      encoderRegistry.registerEncoder(new GlobalMarshallerEncoder(globalMarshaller.wired()));
      encoderRegistry.registerTranscoder(new DefaultTranscoder(jBossMarshaller, javaSerializationMarshaller));

      encoderRegistry.registerWrapper(ByteArrayWrapper.INSTANCE);
      encoderRegistry.registerWrapper(IdentityWrapper.INSTANCE);
      return encoderRegistry;
   }
}
