### Management Servlet

By default security for Management URI is off - it accepts connections on all connectors (e.g. 80 and 433).

If 'front-cache.management.port' property is set to some value Management URI accepts requests from connector with that port only.

*Example*
Frontcache runs on 2 connectors - 80 and 443 and Management URI accepts requests from both of them.
Goal is to restrict requests to Management URI from trusted sources only (and keep serving other requests from everywhere).

* add new connector (port: 9999)
* update frontcache.properties: front-cache.management.port=9999
* restart tomcat (or other servlet container you use)
* update firewall (allow connections to port 9999 from trusted sources only)

Now Frontcache serves ports 80 and 443 from all sources and port 8888 from trusted sources. And Management URI accept requests on 8888 port only.

### Frontcache Console

Update $TOMCAT_HOME/conf/tomcat-users.xml. Add role 'frontcache-console' and assign it to users required access.
Example:

```
  <role rolename="frontcache-console"/>
  <user username="tomcat" password="tomcat" roles="frontcache-console"/>
```