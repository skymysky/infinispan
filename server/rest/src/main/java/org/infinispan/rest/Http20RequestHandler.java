package org.infinispan.rest;

import static io.netty.handler.codec.http.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import org.infinispan.rest.configuration.RestServerConfiguration;
import org.infinispan.rest.logging.Log;
import org.infinispan.util.logging.LogFactory;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.unix.Errors;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.HttpConversionUtil;

/**
 * Netty REST handler for HTTP/2.0
 *
 * @author Sebastian Łaskawiec
 */
public class Http20RequestHandler extends BaseHttpRequestHandler {

   protected final static Log logger = LogFactory.getLog(Http20RequestHandler.class, Log.class);
   protected final RestServer restServer;
   protected final RestServerConfiguration configuration;
   private AuthenticationHandler authenticationHandler;

   /**
    * Creates new {@link Http20RequestHandler}.
    *
    * @param restServer Rest Server.
    */
   public Http20RequestHandler(RestServer restServer) {
      this.restServer = restServer;
      this.configuration = restServer.getConfiguration();
   }

   @Override
   public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
      authenticationHandler = ctx.pipeline().get(AuthenticationHandler.class);
      super.channelRegistered(ctx);
   }

   @Override
   public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
      restAccessLoggingHandler.preLog(request);
      NettyRestRequest restRequest = new NettyRestRequest(request);
      if (authenticationHandler != null) {
         restRequest.setSubject(authenticationHandler.getSubject());
      }
      restServer.getRestDispatcher().dispatch(restRequest).whenComplete((restResponse, throwable) -> {
         if (throwable == null) {
            NettyRestResponse nettyRestResponse = (NettyRestResponse) restResponse;
            addCorrelatedHeaders(request, nettyRestResponse.getResponse());
            sendResponse(ctx, request, nettyRestResponse.getResponse());
         } else {
            handleError(ctx, request, throwable);
         }
      });
   }

   private void addCorrelatedHeaders(FullHttpRequest request, FullHttpResponse response) {
      String streamId = request.headers().get(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
      if (streamId != null) {
         response.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), streamId);
      }
      boolean isKeepAlive = HttpUtil.isKeepAlive(request);
      HttpVersion httpVersion = response.protocolVersion();
      if ((httpVersion == HttpVersion.HTTP_1_1 || httpVersion == HttpVersion.HTTP_1_0) && isKeepAlive) {
         response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
      }
   }

   @Override
   protected Log getLogger() {
      return logger;
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
      // handle the case of to big requests.
      if (e.getCause() instanceof TooLongFrameException) {
         DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, REQUEST_ENTITY_TOO_LARGE);
         ctx.write(response).addListener(ChannelFutureListener.CLOSE);
      } else if (e instanceof Errors.NativeIoException) {
         // Native IO exceptions happen on HAProxy disconnect. It sends RST instead of FIN, which cases
         // a Netty IO Exception. The only solution is to ignore it, just like Tomcat does.
         logger.debug("Native IO Exception", e);
         ctx.close();
      } else {
         logger.uncaughtExceptionInThePipeline(e);
         ctx.close();
      }
   }
}
