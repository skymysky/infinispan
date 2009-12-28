/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat, Inc. and/or its affiliates, and
 * individual contributors as indicated by the @author tags. See the
 * copyright.txt file in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.server.memcached;

import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;
import org.jboss.netty.channel.Channel;

/**
 * ReplaceCommand.
 * 
 * @author Galder Zamarreño
 * @since 4.0
 */
public class ReplaceCommand extends SetCommand {

   ReplaceCommand(Cache cache, CommandType type, StorageParameters params, byte[] data) {
      super(cache, type, params, data);
   }

   @Override
   public Object acceptVisitor(Channel ch, CommandInterceptor next) throws Exception {
      return next.visitReplace(ch, this);
   }

   @Override
   protected Reply put(String key, int flags, byte[] data, long expiry) {
      Value value = new Value(flags, data);
      Object prev = cache.replace(params.key, value, expiry, TimeUnit.MILLISECONDS);
      return reply(prev);
   }

   private Reply reply(Object prev) {
      if (prev == null)
         return Reply.NOT_STORED;
      else
         return Reply.STORED;
   }

}
