package org.frontcache;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections4.MultiValuedMap;
//import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpContext;
import org.frontcache.cache.CacheManager;
import org.frontcache.cache.CacheProcessor;
import org.frontcache.core.FCHeaders;
import org.frontcache.core.Pair;
import org.frontcache.core.RequestContext;
import org.frontcache.include.IncludeProcessor;
import org.frontcache.include.IncludeProcessorManager;
import org.frontcache.reqlog.RequestLogger;

public class FrontCacheEngine {

	private String appOriginBaseURL = null;
	private String fcInstanceId = null;	// used to determine which front cache processed request (forwarded by GEO LB e.g. route53 AWS)
	
	private static int fcConnectionsMaxTotal = 200;
	private static int fcConnectionsMaxPerRoute = 20;

	private IncludeProcessor includeProcessor = null;
	
	private CacheProcessor cacheProcessor = null; 

	protected Logger logger = Logger.getLogger(getClass().getName());
	
	private final Timer connectionManagerTimer = new Timer(
			"FrontCacheEngine.connectionManagerTimer", true);

	private PoolingHttpClientConnectionManager connectionManager;
	private CloseableHttpClient httpClient;
	
	public FrontCacheEngine() {
		initialize();
	}
	
	private void initialize() {

		appOriginBaseURL = FCConfig.getProperty("app_origin_base_url");
		fcInstanceId = FCConfig.getProperty("fc_instance_id");
			
		cacheProcessor = CacheManager.getInstance();

		includeProcessor = IncludeProcessorManager.getInstance();

		includeProcessor.setCacheProcessor(cacheProcessor);

		this.httpClient = newClient();

		connectionManagerTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (connectionManager == null) {
					return;
				}
				connectionManager.closeExpiredConnections();
			}
		}, 30000, 5000);
	}

	public void stop() {
		connectionManagerTimer.cancel();
	}

	
	private CloseableHttpClient newClient() {
		final RequestConfig requestConfig = RequestConfig.custom()
				.setSocketTimeout(10000)
				.setConnectTimeout(2000)
				.setCookieSpec(CookieSpecs.IGNORE_COOKIES)
				.build();

		return HttpClients.custom()
				.setConnectionManager(newConnectionManager())
				.setDefaultRequestConfig(requestConfig)
//				.setSSLHostnameVerifier(new NoopHostnameVerifier()) // for SSL do not verify certificate's host 
				.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false))
				.setRedirectStrategy(new RedirectStrategy() {
					@Override
					public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
						return false;
					}

					@Override
					public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
						return null;
					}
				})
				.build();
	}

	private PoolingHttpClientConnectionManager newConnectionManager() {
		try {
			
			final SSLContext sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, new TrustManager[]{new X509TrustManager() {
				@Override
				public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
				}

				@Override
				public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
				}

				@Override
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}
			}}, new SecureRandom());
			

			final Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
					.register("http", PlainConnectionSocketFactory.INSTANCE)
					.register("https", new SSLConnectionSocketFactory(sslContext))
					.build();

			connectionManager = new PoolingHttpClientConnectionManager(registry);
			
			connectionManager.setMaxTotal(fcConnectionsMaxTotal);
			connectionManager.setDefaultMaxPerRoute(fcConnectionsMaxPerRoute);
			
			return connectionManager;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
    public void init(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {

        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.setRequest(servletRequest);
        ctx.setResponse(servletResponse);
		String uri = FCUtils.buildRequestURI(servletRequest);
		ctx.setRequestURI(uri);
    }	
    
	public void processRequest() throws Exception
	{
		RequestContext context = RequestContext.getCurrentContext();
		HttpServletRequest httpRequest = context.getRequest();
		String originRequestURL = appOriginBaseURL + context.getRequestURI();
//		System.out.println(originRequestURL);

		
		if (context.isCacheableRequest()) // GET method & Accept header contain 'text'
		{
			MultiValuedMap<String, String> requestHeaders = FCUtils.buildRequestHeaders(httpRequest);

			// TODO: throw exception in response is not cacheable !
			WebComponent webComponent = cacheProcessor.processRequest(originRequestURL, requestHeaders, httpClient);


			// include processor
			if (null != webComponent.getContent())
			{
				String content = webComponent.getContent(); 
				content = includeProcessor.processIncludes(content, appOriginBaseURL, requestHeaders, httpClient);
				webComponent.setContent(content);
			}
			
			
			addResponseHeaders(webComponent);
			writeResponse(webComponent);
			
		} else {
			long start = System.currentTimeMillis();
			boolean isRequestDynamic = true;

			
			forwardToOrigin();
			
			long lengthBytes = -1; // TODO: set/get content length from context

			RequestLogger.logRequest(originRequestURL, isRequestDynamic, System.currentTimeMillis() - start, lengthBytes);			
			addResponseHeaders();
			writeResponse();
			
		}
		
		
		return;
	}

	
	private void forwardToOrigin()
	{
		RequestContext context = RequestContext.getCurrentContext();
		
		try {
			context.setRouteHost(new URL(appOriginBaseURL));
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		HttpServletRequest request = context.getRequest();
		MultiValuedMap<String, String> headers = FCUtils.buildRequestHeaders(request);
		MultiValuedMap<String, String> params = FCUtils.builRequestQueryParams(request);
		String verb = FCUtils.getVerb(request);
		InputStream requestEntity = getRequestBody(request);
		String uri = context.getRequestURI();

		try {
			HttpResponse response = forward(httpClient, verb, uri, request, headers, params, requestEntity);
			
			// response 2 context
			setResponse(response);
			
		}
		catch (Exception ex) {
			ex.printStackTrace();
			context.set("error.status_code", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			context.set("error.exception", ex);
		}
		
		return;
	}


	/**
	 * forward all kind of requests (GET, POST, PUT, ...)
	 * 
	 * @param httpclient
	 * @param verb
	 * @param uri
	 * @param request
	 * @param headers
	 * @param params
	 * @param requestEntity
	 * @return
	 * @throws Exception
	 */
	private HttpResponse forward(HttpClient httpclient, String verb, String uri, HttpServletRequest request,
			MultiValuedMap<String, String> headers, MultiValuedMap<String, String> params, InputStream requestEntity)
					throws Exception {
		URL host = RequestContext.getCurrentContext().getRouteHost();
		HttpHost httpHost = FCUtils.getHttpHost(host);
		uri = (host.getPath() + uri).replaceAll("/{2,}", "/");
		
		System.out.println("forward (no-cache) " + httpHost + uri);
		
		HttpRequest httpRequest;
		switch (verb.toUpperCase()) {
		case "POST":
			HttpPost httpPost = new HttpPost(uri + FCUtils.getQueryString());
			httpRequest = httpPost;
			httpPost.setEntity(new InputStreamEntity(requestEntity, request.getContentLength()));
			break;
		case "PUT":
			HttpPut httpPut = new HttpPut(uri + FCUtils.getQueryString());
			httpRequest = httpPut;
			httpPut.setEntity(new InputStreamEntity(requestEntity, request.getContentLength()));
			break;
		case "PATCH":
			HttpPatch httpPatch = new HttpPatch(uri + FCUtils.getQueryString());
			httpRequest = httpPatch;
			httpPatch.setEntity(new InputStreamEntity(requestEntity, request.getContentLength()));
			break;
		default:
			httpRequest = new BasicHttpRequest(verb, uri + FCUtils.getQueryString());
		}
		
		
		try {
			httpRequest.setHeaders(FCUtils.convertHeaders(headers));
			HttpResponse originResponse = httpclient.execute(httpHost, httpRequest);
			return originResponse;
		} finally {
			// When HttpClient instance is no longer needed,
			// shut down the connection manager to ensure
			// immediate deallocation of all system resources
			// httpclient.getConnectionManager().shutdown();
		}
	}	
	
	private void setResponse(HttpResponse response) throws IOException {
		setResponse(response.getStatusLine().getStatusCode(),
				response.getEntity() == null ? null : response.getEntity().getContent(),
				FCUtils.revertHeaders(response.getAllHeaders()));
	}
	
	private void setResponse(int status, InputStream entity, MultiValuedMap<String, String> headers) throws IOException {
		RequestContext context = RequestContext.getCurrentContext();
		
		context.setResponseStatusCode(status);
		
		if (entity != null) {
			context.setResponseDataStream(entity);
		}
		
		for (String key : headers.keySet()) {
			for (String value : headers.get(key)) {
				context.addOriginResponseHeader(key, value);
			}
		}

	}	
	

	private InputStream getRequestBody(HttpServletRequest request) {
		InputStream requestEntity = null;
		try {
			requestEntity = request.getInputStream();
		}
		catch (IOException ex) {
			// no requestBody is ok.
		}
		return requestEntity;
	}	
	
	private void writeResponse() throws Exception {
		RequestContext context = RequestContext.getCurrentContext();
		// there is no body to send
		if (context.getResponseBody() == null && context.getResponseDataStream() == null) {
			return;
		}
		HttpServletResponse servletResponse = context.getResponse();
//		servletResponse.setCharacterEncoding("UTF-8");
		OutputStream outStream = servletResponse.getOutputStream();
		InputStream is = null;
		try {
			if (RequestContext.getCurrentContext().getResponseBody() != null) {
				String body = RequestContext.getCurrentContext().getResponseBody();
				FCUtils.writeResponse(new ByteArrayInputStream(body.getBytes()), outStream);
				return;
			}
			boolean isGzipRequested = false;
			final String requestEncoding = context.getRequest().getHeader(
					FCHeaders.ACCEPT_ENCODING);

			if (requestEncoding != null
					&& FCUtils.isGzipped(requestEncoding)) {
				isGzipRequested = true;
			}
			is = context.getResponseDataStream();
			InputStream inputStream = is;
			if (is != null) {
/*				
				if (context.sendZuulResponse()) {
					// if origin response is gzipped, and client has not requested gzip,
					// decompress stream
					// before sending to client
					// else, stream gzip directly to client
					if (context.getResponseGZipped() && !isGzipRequested) {
						try {
							inputStream = new GZIPInputStream(is);
						}
						catch (java.util.zip.ZipException ex) {
							log.debug("gzip expected but not "
									+ "received assuming unencoded response "
									+ RequestContext.getCurrentContext().getRequest()
											.getRequestURL().toString());
							inputStream = is;
						}
					}
					else if (context.getResponseGZipped() && isGzipRequested) {
						servletResponse.setHeader(FCHeaders.CONTENT_ENCODING, "gzip");
					}
//					writeResponse(inputStream, outStream);
				}
//*/				
				
				FCUtils.writeResponse(inputStream, outStream);
			}

		}
		finally {
			try {
				if (is != null) {
					is.close();
				}
				outStream.flush();
				outStream.close();
			}
			catch (IOException ex) {
			}
		}
	}

	private void writeResponse(WebComponent webComponent) throws Exception {
		RequestContext context = RequestContext.getCurrentContext();

		// there is no body to send
		if (null == webComponent.getContent()) {
			return;
		}
		
		HttpServletResponse servletResponse = context.getResponse();
		servletResponse.setCharacterEncoding("UTF-8");
		OutputStream outStream = servletResponse.getOutputStream();
		try {
			String body = webComponent.getContent();
			FCUtils.writeResponse(new ByteArrayInputStream(body.getBytes()), outStream);
		}
		finally {
			try {
				outStream.flush();
				outStream.close();
			}
			catch (IOException ex) {
			}
		}
		return;
	}
	
	private void addResponseHeaders() {
		RequestContext context = RequestContext.getCurrentContext();
		HttpServletResponse servletResponse = context.getResponse();
		List<Pair<String, String>> originResponseHeaders = context.getOriginResponseHeaders();

		servletResponse.addHeader(FCHeaders.X_FC_INSTANCE, fcInstanceId);
		servletResponse.setStatus(context.getResponseStatusCode());
		
		if (originResponseHeaders != null) {
			for (Pair<String, String> it : originResponseHeaders) {
				servletResponse.addHeader(it.first(), it.second());
			}
		}
		RequestContext ctx = RequestContext.getCurrentContext();
		Long contentLength = ctx.getOriginContentLength();
		// Only inserts Content-Length if origin provides it and origin response is not
		// gzipped
//		if (SET_CONTENT_LENGTH.get()) {
			if (contentLength != null && !ctx.getResponseGZipped()) {
				servletResponse.setContentLength(contentLength.intValue());
			}
//		}
	}	

	private void addResponseHeaders(WebComponent webComponent) {
		RequestContext context = RequestContext.getCurrentContext();
		HttpServletResponse servletResponse = context.getResponse();

		servletResponse.setStatus(webComponent.getStatusCode());
		
		servletResponse.addHeader(FCHeaders.X_FC_INSTANCE, fcInstanceId);
		
		if (webComponent.getHeaders() != null) {
			for (String name : webComponent.getHeaders().keySet()) {
				for (String value : webComponent.getHeaders().get(name)) {
					servletResponse.addHeader(name, value);
				}
			}
		}
		// TO
		
//		RequestContext ctx = RequestContext.getCurrentContext();
//		Long contentLength = ctx.getOriginContentLength();
//		// Only inserts Content-Length if origin provides it and origin response is not
//		// gzipped
////		if (SET_CONTENT_LENGTH.get()) {
//			if (contentLength != null && !ctx.getResponseGZipped()) {
//				servletResponse.setContentLength(contentLength.intValue());
//			}
////		}
	}	
	
		
}
