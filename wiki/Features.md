* Advanced HTTP response caching for dynamic pages.
* Client type specific caching. Different cache behaviour for users and bots.
* Background cache refresh.
* Custom fallbacks when origin is unavailable or returns 5XX error.
* Server-side includes. Frontcache resolves server-side includes what allows to cache parts of HTML page.
* Asynchronous origin calls / Asynchronous includes. 


**Client type specific caching**  Different cache behaviour for users and bots.

- cache data for bots and get dynamic data for people for the same URL
- and opposite - dynamic data for bots and cached for people 
- different cache timeouts for bots vs users

Example: 

`<fc:component maxage="1h" />` - cache responses for all types of client (1 hour) 

`<fc:component maxage="bot:30d" />` - cache responses for bots (30 days) only

**Background cache refresh**

There is an option to reduce latency for expired date in cache. 

`Set refresh="soft"`
`<fc:component maxage="1h" refresh="soft" />`

**Custom fallbacks when origin is unavailable or returns 5XX error**

When origin is not available or returns error (5XX HTTP code) fallback can be configured for specific URLs. Fallbacks data are stored on filesystem under FRONTCACHE_HOME/fallbacks directory.

        URL regexp pattern | file with data | fallback source URL (optional)
        http://www.coinshome.net/../welcome.htm | welcome.html | http://origin.coinshome.net/en/welcome.htm
        http://www.coinshome.net/../market.htm | market.html | http://origin.coinshome.net/en/market.htm

During frontcache startup fallback data files are populated from fallback source URLs.
During runtime when input request or include experience error Frontcache attempts to resolve data with fallback or return default error message if no fallbacks configured.


**Server-side includes - Page fragment caching**

- cached page with dynamic includes

Example: cached page with dynamic 'recent updates' include

        product-details.jsp
        <fc:component maxage="30d" />
        {content}
        <fc:include url="recent-updates.jsp" />
        {content}

        recent-updates.jsp
        <fc:component maxage="0" />
        {dynamic content}


- dynamic page with cached includes

Example: dynamic page (basket.jsp) with cached include (footer.jsp)

        basket.jsp
        <fc:component maxage="0" />
        {dynamic content}
        <fc:include url="footer.jsp" />
        
        footer.jsp
        <fc:component maxage="1d" />
        {content}


**Asynchronous origin calls. Asynchronous includes**

Asynchronous origin calls / Asynchronous includes don't return data to output page but hit origin.  It allows to have zero latency pages with origin hits.

E.g. I would like to count visits for completely cached page. 
There is an option to add regular/sync dynamic include and count visits there. It works but create a latency for origin call. Another option to run include in async mode and have the page ready a little bit faster.


**Multilevel cache**


`<fc:component maxage="1h" level="L1 | L2" />`

**Cache invalidation by tags**


`<fc:component maxage="1h" tags="invalidation-tag-1 | invalidation-tag-2" />`
