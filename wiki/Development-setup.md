* Prerequirements: Java 1.8, Git, Gradle 

* checkout
```
git clone https://github.com/eternita/frontcache.git
```

* run with defaults
```
cd frontcache
./gradlew clean frontcache-server:farmRun
```
Point browser to http://localhost:9080/ . You can see some content from www.coinshome.net 
Frontcache Console - http://localhost:9080/frontcache-console/ 
Edit ./conf/frontcache.properties to point different origin.

* build
```
./gradlew clean build
```

* Run tests

**Preconditions:** add following test domains to your hosts file (e.g. /etc/hosts on Linux/Mac)

        127.0.0.1	www.fc1-test.org
        127.0.0.1	origin.fc1-test.org
        127.0.0.1	www.fc2-test.org
        127.0.0.1	origin.fc2-test.org

```
./tests.sh
```

* Start test environment
```
./gradlew clean :frontcache-tests:farmRun
```

### Debuging requests 
Set http request header 
```
X-frontcache.trace = true
```
HTTP Response will have following headers with performance stat for request/includes and debug info:

```
X-frontcache.trace.request.0 - for top level request
X-frontcache.trace.request.1.1... - for includes (numbers at the end point to include tree hierarchy)
```

**Example**

        X-frontcache.trace.request.0: success toplevel from-cache 9 64505 "http://localhost:9080/en/coin_definition-1_Escudo-Gold-Centralist_Republic_of_Mexico_(1835_1846)-E9AKbzbiOBIAAAFG0vnZjkvL.htm" frontcache-localhost-1 browser
        X-frontcache.trace.request.1.1: success sync from-cache 0 768 "http://localhost:9080/fc/recent-updates-add.htm?locale=en" frontcache-localhost-1 browser
        X-frontcache.trace.request.1.2: success sync from-cache 1 1600 "http://localhost:9080/fc/include-footer.htm?locale=en" frontcache-localhost-1 browser
        X-frontcache.trace.request.2.0: success sync from-cache 0 1139 "http://localhost:9080/fc/recent-updates-add.htm?locale=en&id=k4YKX9ISFwkAAAFZ.IMBsCnt" frontcache-localhost-1 browser
        X-frontcache.trace.request.2.1: success sync from-cache 1 1139 "http://localhost:9080/fc/recent-updates-add.htm?locale=en&id=8JEKX9ISJ5AAAAFZflEBsB_O" frontcache-localhost-1 browser
