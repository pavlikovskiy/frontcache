## Running standalone Frontcache

* download binaries:  http://static.frontcache.io/download/frontcache
* extract archive
* start ./bin/frontcache
* Point browser to http://localhost:9080/   - You can see content from www.coinshome.net 
* Frontcache Console - http://localhost:9080/frontcache-console/    


**Setup Frontcache for your site**

* edit $FRONTCACHE_HOME/conf/frontcache.properties.  Set origin to Wikipedia (or your site)

        front-cache.origin-host=en.wikipedia.org
* start ./bin/frontcache
* Point browser to http://localhost:9080/ - You can see content from Wikipedia (or your site)

## Running Frontcache on Docker

There are couple Docker configurations are available to run Frontcache and visualize logs with Kibana (ELK).

Check article about [**Frontcache and Docker**](https://github.com/eternita/frontcache/wiki/Frontache-&-Docker)

## Frontcache as Servlet Filter

[Here you can find couple examples](https://github.com/eternita/frontcache/wiki/Examples) how to run Frontcache with plain JSP and Spring based apps

## Frontcache with PHP

Following [example](https://github.com/eternita/frontcache/tree/master/examples/frontcache-php) shows how easily PHP application can be integrated with Frontcache Server.