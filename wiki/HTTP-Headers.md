HTTP Header | Set by | Where | Description
--- | --- | --- | ---
**X-frontcache.trace** | Client | request | when true - response headers have performance statistics for request and includes
**X-frontcache.site-key** | Client | request | authentication key for invalidation requests
**X-frontcache.component.tags** | Origin | response | set invalidation tags for cache, optional
**X-frontcache.component.maxage** | Origin | response | set time to live in cache
**X-frontcache.component.cache-level** | Origin  | response  | set cache level - L1 / L2, optional, default - L2 
**X-frontcache.component.refresh** | Origin | response | set refresh type for cached entry - regular / soft, optional, default - regular
**X-frontcache.id** | Frontcache | response | frontcache server id (useful in case of geo balancing e.g. R53)
**X-frontcache.request-id** | Frontcache | response | request UUID - the same value for top level request and all includes
**X-frontcache.client-ip** | Frontcache | request | set in frontcache server for origin application
**X-frontcache.trace.request** | Frontcache | response | performance statistics for request and includes
**X-frontcache.fallback-is-used** | Frontcache | response | is true when response has fallbacks included
**X-frontcache.dynamic-request** | Frontcache | request | internal use
**X-frontcache.soft-refresh** | Frontcache | request | internal use
**X-frontcache.async-include** | Frontcache | request | internal use
**X-frontcache.include-level** | Frontcache | request | internal use




