### Frontcache console tabs

**'Realtime monitor' tab** shows frontcache edges statuses (online/offline, amount of cached items) and real time view for input request, cache and origin hits.


**'Fallback configs' tab**
When origin is not available or returns error (5XX HTTP code) fallback can be configured for specific URLs. Fallbacks data are stored on filesystem under FRONTCACHE_HOME/fallbacks directory.

        URL regexp pattern | file with data | fallback source URL (optional)
        http://www.coinshome.net/../welcome.htm | welcome.html | http://origin.coinshome.net/en/welcome.htm
        http://www.coinshome.net/../market.htm | market.html | http://origin.coinshome.net/en/market.htm

During frontcache startup fallback data files are populated from fallback source URLs.
During runtime when input request or include experience error Frontcache attempts to resolve data with fallback or return default error message if no fallbacks configured.


**'Dynamic URLs configs' tab**. Requests which match dynamic URL patterns are forwarded to origin bypass cache and includes checking. 

**'Cache view' tab** to view cached data and http headers.

**'Cache invalidation' tab** to force cache invalidation on specific edge by URL or invalidation tag.

**'Edges' tab** to add/remove Frontcache edge to be monitored by Frontcache console.


![Alt](https://github.com/eternita/frontcache/raw/master/images/fc-console-screen.png "Frontcache console demo")



### Running Frontcache console 

- create config file ( e.g. /opt/frontcache/conf/frontcache-console.properties) with Frontcache edges. For example:

```
http://fc1.coinshome.net:8888/
http://fc2.coinshome.net:8888/
```

- copy frontcache-console.war to web container (e.g. $TOMCAT_HOME/webapps/frontcache-console.war)

- update Tomcat startup scripts with Java property 'org.frontcache.console.config' pointed to console config file. For example


```
JAVA_OPTS="-Dorg.frontcache.console.config=/opt/frontcache/conf/frontcache-console.properties"
```

- setup security - update $TOMCAT_HOME/conf/tomcat-users.xml. Add role 'frontcache-console' and assign it to a user.
Example:

```
  <role rolename="frontcache-console"/>
  <user username="tomcat" password="tomcat" roles="frontcache-console"/>
```
