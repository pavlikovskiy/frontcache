package org.frontcache.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicHeader;
import org.frontcache.FrontCacheEngine;
import org.frontcache.cache.CacheProcessor;
import org.frontcache.wrapper.FrontCacheHttpResponseWrapper;
import org.frontcache.wrapper.HttpResponseWrapperImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FCUtils {

	private FCUtils() {
	}
	
	private static Logger logger = LoggerFactory.getLogger(FCUtils.class);
		
	/**
	 * e.g. localhost:8080
	 * 
	 * @param httpRequest
	 * @return
	 */
    public static String getHost(HttpServletRequest httpRequest)
    {
    	StringBuffer sb = new StringBuffer();
    	sb.append(httpRequest.getServerName());
    	return sb.toString();
    }

    public static String getProtocol(HttpServletRequest httpRequest)
    {
    	if (httpRequest.isSecure())
    		return "https";
    	else
    		return "http";
    }
	
	/**
	 * GET method only for text requests
	 * 
	 * for cache processor - it can use both (httpClient or filter)
	 * 
	 * @param urlStr
	 * @param httpRequest
	 * @param httpResponse
	 * @return
	 */
	public static WebResponse dynamicCall(String urlStr, MultiValuedMap<String, String> requestHeaders, HttpClient client) throws FrontCacheException
    {
		RequestContext context = RequestContext.getCurrentContext();
		if (context.isFilterMode())
			return dynamicCallFilter();
		else
			return dynamicCallHttpClient(urlStr, requestHeaders, client);

				
    }

	/**
	 * for includes - they allways use httpClient
	 * 
	 * @param urlStr
	 * @param requestHeaders
	 * @param client
	 * @return
	 * @throws FrontCacheException
	 */
	public static WebResponse dynamicCallHttpClient(String urlStr, MultiValuedMap<String, String> requestHeaders, HttpClient client) throws FrontCacheException
    {
		HttpResponse response = null;

		try {
			HttpHost httpHost = FCUtils.getHttpHost(new URL(urlStr));
			HttpRequest httpRequest = new HttpGet(FCUtils.buildRequestURI(urlStr));//(verb, uri + context.getRequestQueryString());

			// translate headers
			Header[] httpHeaders = convertHeaders(requestHeaders);
			for (Header header : httpHeaders)
				httpRequest.addHeader(header);
			
			response = client.execute(httpHost, httpRequest);
			WebResponse webResp = httpResponse2WebComponent(urlStr, response);
			return webResp;

		} catch (IOException ioe) {
			throw new FrontCacheException("Can't read from " + urlStr, ioe);
		} finally {
			if (null != response)
				try {
					((CloseableHttpResponse) response).close();
				} catch (IOException e) {
					e.printStackTrace();
				} 
		}
		
    }
	
	private static WebResponse dynamicCallFilter() throws FrontCacheException
	{
		RequestContext context = RequestContext.getCurrentContext();
		HttpServletRequest httpRequest = context.getRequest();
		HttpServletResponse httpResponse = context.getResponse();
		FilterChain chain = context.getFilterChain();

		FrontCacheHttpResponseWrapper wrappedResponse = new HttpResponseWrapperImpl(httpResponse);
		
		try {
			chain.doFilter(httpRequest, wrappedResponse); // run request to origin
			
			String url = getRequestURL(httpRequest);
			WebResponse webResponse = httpResponse2WebComponent(url, wrappedResponse);
			return webResponse;
			
		} catch (IOException | ServletException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new FrontCacheException("FilterChain exception", e);
		} 
		
	}

	/**
	 * is used in ServletFilter mode
	 * 
	 * @param url
	 * @param originWrappedResponse
	 * @return
	 * @throws FrontCacheException
	 * @throws IOException
	 */
	private static WebResponse httpResponse2WebComponent(String url, FrontCacheHttpResponseWrapper originWrappedResponse) throws FrontCacheException, IOException
	{
		WebResponse webResponse = null;
		
		String contentType = originWrappedResponse.getContentType();
		String dataStr = originWrappedResponse.getContentString();
		if (null != dataStr && dataStr.length() > 0)
		{
			webResponse = parseWebComponent(url, dataStr);
				
			if (null == contentType || -1 == contentType.indexOf("text"))
			{
				contentType = "text"; // 
				webResponse.setContentType(contentType);
			}
		} else {
			webResponse = new WebResponse(url);
			logger.info(url + " has response with no data");
			
		}

		if (!"".equals(contentType))
			webResponse.setContentType(contentType);
				

		webResponse.setStatusCode(originWrappedResponse.getStatus());

		// get headers
		MultiValuedMap<String, String> headers = new ArrayListValuedHashMap<String, String>();
		for (String headerName : originWrappedResponse.getHeaderNames())
			if (isIncludedHeaderToResponse(headerName))
				headers.put(headerName, originWrappedResponse.getHeader(headerName));
		
		// filter may not set up content type yet -> check and setup 
		if (null != dataStr && 0 == headers.get("Content-Type").size())
		{
			headers.put("Content-Type", contentType); 
		}

		webResponse.setHeaders(headers);
		
		return webResponse;
	}
	
	private static WebResponse httpResponse2WebComponent(String url, HttpResponse response) throws FrontCacheException, IOException
	{
		
		
		int httpResponseCode = response.getStatusLine().getStatusCode();
			
		if (httpResponseCode < 200 || httpResponseCode > 299)
		{
			// error
//			throw new RuntimeException("Wrong response code " + httpResponseCode);
		}

		// get headers
		MultiValuedMap<String, String> headers = revertHeaders(response.getAllHeaders());
		
		// process redirects
		Header locationHeader = response.getFirstHeader("Location");
		if (null != locationHeader)
		{
			String originLocation = locationHeader.getValue();
			
			String fcLocation = transformRedirectURL(originLocation);
				
			headers.remove("Location");
			headers.put("Location", fcLocation);
		}
		
		String contentType = "";
		Header contentTypeHeader = response.getFirstHeader("Content-Type");
		if (null != contentTypeHeader)
			contentType = contentTypeHeader.getValue();
		
		WebResponse webResponse = null;
		if (-1 == contentType.indexOf("text"))
		{
			webResponse = new WebResponse(url);

		} else {
			BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			StringBuffer result = new StringBuffer();
			String line = null;
			boolean firstLine = true;
			while ((line = rd.readLine()) != null) {
				if (firstLine) 
					firstLine = false;
				else
					result.append("\n"); // append '\n' because it's lost during rd.readLine() (in between lines)
				
				result.append(line);
			}
			
			String dataStr = result.toString();
			webResponse = parseWebComponent(url, dataStr);
		}

		webResponse.setStatusCode(httpResponseCode);
		webResponse.setHeaders(headers);
		
		if (!"".equals(contentType))
			webResponse.setContentType(contentType);
				
		return webResponse;
	}
	
	public static String transformRedirectURL(String originLocation)
	{
		String fcLocation = null;
		RequestContext context = RequestContext.getCurrentContext();
		String protocol = getRequestProtocol(originLocation);
		if ("https".equalsIgnoreCase(protocol))
			fcLocation = "https://" + context.getFrontCacheHost() + ":" + context.getFrontCacheHttpsPort() + buildRequestURI(originLocation);
		else // http
			fcLocation = "http://" + context.getFrontCacheHost() + ":" + context.getFrontCacheHttpPort() + buildRequestURI(originLocation);
		
		return fcLocation;
	}
	
	
    /**
     * returns query params as a Map with String keys and Lists of Strings as values
     * @return
     */
    public static Map<String, List<String>> getQueryParams() {

        Map<String, List<String>> qp = RequestContext.getCurrentContext().getRequestQueryParams();
        if (qp != null) return qp;

        HttpServletRequest request = RequestContext.getCurrentContext().getRequest();

        qp = new HashMap<String, List<String>>();

        if (request.getQueryString() == null) return null;
        StringTokenizer st = new StringTokenizer(request.getQueryString(), "&");
        int i;

        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            i = s.indexOf("=");
            if (i > 0 && s.length() >= i + 1) {
                String name = s.substring(0, i);
                String value = s.substring(i + 1);

                try {
                    name = URLDecoder.decode(name, "UTF-8");
                } catch (Exception e) {
                }
                try {
                    value = URLDecoder.decode(value, "UTF-8");
                } catch (Exception e) {
                }

                List<String> valueList = qp.get(name);
                if (valueList == null) {
                    valueList = new LinkedList<String>();
                    qp.put(name, valueList);
                }

                valueList.add(value);
            }
            else if (i == -1)
            {
                String name=s;
                String value="";
                try {
                    name = URLDecoder.decode(name, "UTF-8");
                } catch (Exception e) {
                }
               
                List<String> valueList = qp.get(name);
                if (valueList == null) {
                    valueList = new LinkedList<String>();
                    qp.put(name, valueList);
                }

                valueList.add(value);
                
            }
        }

        RequestContext.getCurrentContext().setRequestQueryParams(qp);
        return qp;
    }
	
    /**
     * revert header from HttpClient format (call to origin) to Map 
     * 
     * @param headers
     * @return
     */
    public static MultiValuedMap<String, String> revertHeaders(Header[] headers) {
		MultiValuedMap<String, String> map = new ArrayListValuedHashMap<String, String>();
		for (Header header : headers) {
			String name = header.getName();
			
			if (isIncludedHeaderToResponse(name))
				map.put(name, header.getValue());
		}
		return map;
	}
    
	private static boolean isIncludedHeaderToResponse(String headerName) {
		String name = headerName.toLowerCase();

		switch (name) {
			case "content-length": // do not use 'content-length' because 'transfer-encoding' is used 
			case "transfer-encoding": // put it here to avoid duplicates 
				return false;
			default:
				return true;
		}
	}

	public static Header[] convertHeaders(MultiValuedMap<String, String> headers) {
		List<Header> list = new ArrayList<>();
		for (String name : headers.keySet()) {
			for (String value : headers.get(name)) {
				list.add(new BasicHeader(name, value));
			}
		}
		return list.toArray(new BasicHeader[0]);
	}
	
	/**
	 * 
	 * @param request
	 * @return
	 */
	public static String getRequestURL(HttpServletRequest request)
	{
        String requestURL = request.getRequestURL().toString();
        
        if ("GET".equals(request.getMethod()))
        {
        	// add parameters for storing 
        	// POST method parameters are not stored because they can be huge (e.g. file upload)
        	StringBuffer sb = new StringBuffer(requestURL);
        	Enumeration paramNames = request.getParameterNames();
        	if (paramNames.hasMoreElements())
        		sb.append("?");

        	while (paramNames.hasMoreElements()){
        		String name = (String) paramNames.nextElement();
        		sb.append(name).append("=").append(request.getParameter(name));
        		
        		if (paramNames.hasMoreElements())
        			sb.append("&");
        	}
        	requestURL = sb.toString();
        }	
        return requestURL;
	}

	public static String getQueryString(HttpServletRequest request) {
		MultiValuedMap<String, String> params = FCUtils.builRequestQueryParams(request);
		StringBuilder query=new StringBuilder();
		
		try {
			for (String paramKey : params.keySet())
			{
				String key = URLEncoder.encode(paramKey, "UTF-8");
				for (String value : params.get(paramKey))
				{
					query.append("&");
					query.append(key);
					query.append("=");
					query.append(URLEncoder.encode(value, "UTF-8"));
				}
				
			}		
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		return (query.length()>0) ? "?" + query.substring(1) : "";
	}

	
	/**
	 * http://www.coinshome.net/en/welcome.htm -> /en/welcome.htm
	 * 
	 * @param request
	 * @return
	 */
	public static String getRequestURLWithoutHostPort(HttpServletRequest request)
	{
        String requestURL = getRequestURL(request);
        int sIdx = 10; // requestURL.indexOf("://"); // http://www.coinshome.net/en/welcome.htm
        int idx = requestURL.indexOf("/", sIdx);

        return requestURL.substring(idx);
	}

    /**
     * return true if the client requested gzip content
     *
     * @param contentEncoding 
     * @return true if the content-encoding containg gzip
     */
    public static boolean isGzipped(String contentEncoding) {
        return contentEncoding.contains("gzip");
    }
    
	/**
	 * wrap String to WebResponse.
	 * Check for header - extract caching options.
	 * 
	 * @param content
	 * @return
	 */
	private static final WebResponse parseWebComponent (String urlStr, String content)
	{
		int cacheMaxAgeSec = CacheProcessor.NO_CACHE;
		
		String outStr = null;
		final String START_MARKER = "<fc:component";
		final String END_MARKER = "/>";
		
		int startIdx = content.indexOf(START_MARKER);
		if (-1 < startIdx)
		{
			int endIdx = content.indexOf(END_MARKER, startIdx);
			if (-1 < endIdx)
			{
				String includeTagStr = content.substring(startIdx, endIdx + END_MARKER.length());
				cacheMaxAgeSec = getCacheMaxAge(includeTagStr);
				
				// exclude tag from content
				StringBuffer outSb = new StringBuffer();
				outSb.append(content.substring(0, startIdx));
				if (FrontCacheEngine.debugComments)
					outSb.append("<!-- fc:component ttl=").append(cacheMaxAgeSec).append("sec -->"); // comment out tag (leave it for debugging purpose)
				outSb.append(content.substring(endIdx + END_MARKER.length(), content.length()));
				outStr = outSb.toString();
			} else {
				// can't find closing 
				outStr = content;
			}
			
		} else {
			outStr = content;
		}

		WebResponse component = new WebResponse(urlStr, outStr, cacheMaxAgeSec);
		
		return component;
	}
	
	/**
	 * 
	 * @param content
	 * @return time to live in cache in seconds
	 */
	private static int getCacheMaxAge(String content)
	{
		final String START_MARKER = "maxage=\"";
		int startIdx = content.indexOf(START_MARKER);
		if (-1 < startIdx)
		{
			int endIdx = content.indexOf("\"", startIdx + START_MARKER.length());
			if (-1 < endIdx)
			{
				String maxAgeStr = content.substring(startIdx + START_MARKER.length(), endIdx);
				try
				{
					int multiplyPrefix = 1;
					if (maxAgeStr.endsWith("d")) // days
					{
						maxAgeStr = maxAgeStr.substring(0, maxAgeStr.length() - 1);
						multiplyPrefix = 86400; // 24 * 60 * 60
					} else if (maxAgeStr.endsWith("h")) { // hours
						maxAgeStr = maxAgeStr.substring(0, maxAgeStr.length() - 1);
						multiplyPrefix = 3600; // 60 * 60
					} else if (maxAgeStr.endsWith("m")) { // minutes
						maxAgeStr = maxAgeStr.substring(0, maxAgeStr.length() - 1);
						multiplyPrefix = 60;
					} else if (maxAgeStr.endsWith("s")) { // seconds
						maxAgeStr = maxAgeStr.substring(0, maxAgeStr.length() - 1);
						multiplyPrefix = 1;
					} else {
						// seconds
					}
					
					return multiplyPrefix * Integer.parseInt(maxAgeStr); // time to live in cache in seconds
				} catch (Exception e) {
					logger.info("can't parse component maxage - " + maxAgeStr + " defalut is used (NO_CACHE)");
					return CacheProcessor.NO_CACHE;
				}
				
			} else {
				logger.info("no closing tag for - " + content);
				// can't find closing 
				return CacheProcessor.NO_CACHE;
			}
			
			
		} else {
			// no maxage attribute
			logger.info("no maxage attribute for - " + content);
			return CacheProcessor.NO_CACHE;
		}

	}	

	public static String buildRequestURI(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return uri;
	}		

	/**
	 * http://localhost:8080/coin_instance_details.htm? -> /coin_instance_details.htm?
	 * 
	 * @param urlStr
	 * @return
	 */
	public static String buildRequestURI(String urlStr) {
		
		int idx = urlStr.indexOf("//");
		if (-1 < idx)
		{
			idx = urlStr.indexOf("/", idx + "//".length());
			if (-1 < idx)
				return urlStr.substring(idx);
		} 
		
		return urlStr;
	}
	
	/**
	 * http://localhost:8080/coin_instance_details.htm? -> http
	 * @param urlStr
	 * @return
	 */
	public static String getRequestProtocol(String urlStr) {
		
		int idx = urlStr.indexOf(":");
		if (-1 < idx)
			return urlStr.substring(0, idx);
		
		return ""; // default
	}		
	
	public static HttpHost getHttpHost(URL host) {
		HttpHost httpHost = new HttpHost(host.getHost(), host.getPort(), host.getProtocol());
		return httpHost;
	}
	
	public static MultiValuedMap<String, String> builRequestQueryParams(HttpServletRequest request) {
		Map<String, List<String>> map = FCUtils.getQueryParams();
		MultiValuedMap<String, String> params = new ArrayListValuedHashMap<>();
		if (map == null) {
			return params;
		}
		for (String key : map.keySet()) {
			for (String value : map.get(key)) {
				params.put(key, value);
			}
		}
		return params;
	}	

	public static MultiValuedMap<String, String> buildRequestHeaders(HttpServletRequest request) {

		MultiValuedMap<String, String> headers = new ArrayListValuedHashMap<>();
		Enumeration<String> headerNames = request.getHeaderNames();
		if (headerNames != null) {
			while (headerNames.hasMoreElements()) {
				String name = headerNames.nextElement();
				if (isIncludedHeader(name)) {
					Enumeration<String> values = request.getHeaders(name);
					while (values.hasMoreElements()) {
						String value = values.nextElement();
						headers.put(name, value);
					}
				}
			}
		}

		return headers;
	}
	
	private static boolean isIncludedHeader(String headerName) {
		String name = headerName.toLowerCase();

		switch (name) {
			case "host":
			case "connection":
			case "content-length":
			case "content-encoding":
			case "server":
			case "transfer-encoding":
				return false;
			default:
				return true;
		}
	}
	
	
	public static String getVerb(HttpServletRequest request) {
		String sMethod = request.getMethod();
		return sMethod.toUpperCase();
	}	

	public static void writeResponse(InputStream zin, OutputStream out) throws Exception {
		byte[] bytes = new byte[1024];
		int bytesRead = -1;
		while ((bytesRead = zin.read(bytes)) != -1) {
			try {
				out.write(bytes, 0, bytesRead);
				out.flush();
			}
			catch (IOException ex) {
				ex.printStackTrace();
			}
			// doubles buffer size if previous read filled it
			if (bytesRead == bytes.length) {
				bytes = new byte[bytes.length * 2];
			}
		}
	}
	
}
