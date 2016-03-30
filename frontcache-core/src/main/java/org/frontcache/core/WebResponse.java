package org.frontcache.core;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.frontcache.cache.CacheProcessor;

/**
 * 
 * Container for web response. Usually it's text response for GET method.  
 * 
 *
 */
public class WebResponse implements Serializable {


	/**
	 * 
	 */
	private static final long serialVersionUID = 4L; // v0.4 -> 4, v1.0 -> 10

	private int statusCode = -1; // for redirects
	
	private String url;
	
	private String content;
	
	/**
	 * Some headers, such as Accept-Language can be sent by clients as several headers each with a different value rather than sending the header as a comma separated list
	 */
	private MultiValuedMap<String, String> headers;
	
	private Set<String> tags;
	
	private String contentType;
	
	// -1 cache forever
	// 0 never cache
	// 123456789 expiration time in ms
	private long expireTimeMillis = CacheProcessor.NO_CACHE;
	
	/**
	 * some responses has no body (e.g. response for redirect)
	 * 
	 * @param url
	 */
	public WebResponse(String url) {
		super();
		this.url = url;
		this.headers = new ArrayListValuedHashMap<String, String>();
	}
	
	public WebResponse(String url, String content, int cacheMaxAgeSec) {
		this(url);
		this.content = content;
		setExpireTime(cacheMaxAgeSec);
	}	
	
	public int getStatusCode() {
		return statusCode;
	}



	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}



	public String getUrl() {
		return url;
	}



	public void setUrl(String url) {
		this.url = url;
	}



	public MultiValuedMap<String, String> getHeaders() {
		return headers;
	}

	/**
	 * 
	 * @param headers
	 */
	public void setHeaders(MultiValuedMap<String, String> headers) {
		this.headers = headers;
		
		return;
	}

	/**
	 * 
	 * @return
	 */
	public Set<String> getTags() {
		return tags;
	}


	/**
	 * 
	 * @param tags
	 */
	public void setTags(Set<String> tags) {
		this.tags = tags;
	}

	/**
	 * 
	 * @return
	 */
	public String getContent() {
		return content;
	}

	/**
	 * 
	 * @param content
	 */
	public void setContent(String content) {
		this.content = content;
	}
	
	/**
	 * 
	 * @return
	 */
	public String getContentType() {
		return contentType;
	}

	/**
	 * 
	 * @param contentType
	 */
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	/**
	 * Check if response is cacheable 
	 * 
	 * @return
	 */
	public boolean isCacheable()
	{
		if (expireTimeMillis == CacheProcessor.NO_CACHE) 
			return false; // do not cache marker
		
		if (null == headers)  
			return false; // no header
		
		if (null == content) 
			return false;  // no data
		
		if (null == contentType || -1 == contentType.indexOf("text")) 
			return false;  // response data is not text
		
		return true; 
	}

	/**
	 * 
	 * @param cacheMaxAgeSec time to live in seconds or CacheProcessor.CACHE_FOREVER
	 */
	public void setExpireTime(long cacheMaxAgeSec) {
		
		if (CacheProcessor.CACHE_FOREVER == cacheMaxAgeSec)
			this.expireTimeMillis = CacheProcessor.CACHE_FOREVER;
		
		else if (CacheProcessor.NO_CACHE == cacheMaxAgeSec)
			this.expireTimeMillis = CacheProcessor.NO_CACHE;
		
		else 
			this.expireTimeMillis = System.currentTimeMillis() + 1000 * cacheMaxAgeSec;
		
		return;
	}
	
	/**
	 * Check with current time if expired 
	 * 
	 * @return
	 */
	public boolean isExpired()
	{
		if (CacheProcessor.CACHE_FOREVER == expireTimeMillis)
			return false;
		
		if (System.currentTimeMillis() > expireTimeMillis)
			return true;
		
		return false;
	}

	/**
	 * content length in bytes
	 * 
	 * @return
	 */
	public long getContentLenth() 
	{
		if (null != getContent())
			return getContent().length();
		
		return -1;
	}

    /**
     * 
     * @return
     */
    public WebResponse copy() {
    	WebResponse copy = new WebResponse(this.url);
    	copy.content = this.content;
    	copy.contentType = this.contentType;
    	copy.expireTimeMillis = this.expireTimeMillis;
    	copy.statusCode = this.statusCode;
    	
    	if (null != this.tags)
    	{
    		copy.tags = new HashSet<String>();
    		copy.tags.addAll(this.tags);
    	}
    	
    	if (null != headers)
    	{
    		copy.headers = new ArrayListValuedHashMap<String, String>();
    		
    		for (String name : this.headers.keySet()) 
    			for (String value : headers.get(name)) 
    				copy.headers.put(name, value);
    	}
    	
        return copy;
    }	
}
