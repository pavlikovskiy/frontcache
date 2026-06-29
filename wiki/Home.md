### What it does? ###
* speed up dynamic pages 
* reduce load on backend systems dozen times 
* increase application's resilience

### [Features](https://github.com/eternita/frontcache/wiki/Features) ###
* Advanced HTTP response caching for dynamic pages.
* Client type specific caching. Different cache behaviour for users and bots.
* Background cache refresh.
* Custom fallbacks when origin is unavailable or returns 5XX error.
* Server-side includes. Frontcache resolves server-side includes what allows to cache parts of HTML page.
* Asynchronous origin calls / Asynchronous includes. 

### [How it works](https://github.com/eternita/frontcache/wiki/How-It-Works) ###

Frontcache is written in Java and works 
 - as Servlet filter for Java based websites
 - as standalone application what makes it friendly to websites written in any language

### [Frontcache Console](https://github.com/eternita/frontcache/wiki/Console-UI) ###

Frontcache has web based user friendly console to show real-time statistics and config parameters.

### Sub-projects ###

**frontcache-core** - core implementation, distribution for 'Servlet filter' mode.

**frontcache-server** - web wrapper for standalone web application (edge) mode.

**frontcache-agent** - library for cache invalidation from Web App side.

**frontcache-console** - UI for server/cluster configuration and realtime statistics.

**frontcache-tests** - integration tests.
 
 
