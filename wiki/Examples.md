### 'frontcache-jsp' - Frontcache as Servlet Filter (with plain JSP app) ###

Example project path: ./examples/frontcache-jsp

**Steps to run**

        git clone https://github.com/eternita/frontcache.git
        cd frontcache/examples/frontcache-jsp/
        gradle clean jettyRun

point browser to http://localhost:8080/example/index.jsp

[More about frontcache-jsp example](https://github.com/eternita/frontcache/tree/master/examples/frontcache-jsp)

### 'frontcache-spring' - Frontcache as Servlet Filter (with Spring app) ###

Example project path: ./examples/frontcache-spring

**Steps to run**

        git clone https://github.com/eternita/frontcache.git
        cd frontcache/examples/frontcache-spring/
        mvn clean install spring-boot:run

point browser to http://localhost:8080/

[More about frontcache-spring example](https://github.com/eternita/frontcache/tree/master/examples/frontcache-spring)

### 'frontcache-php' - Integrating Frontcache with PHP application ###

Example project path: ./examples/frontcache-php

**Steps to run**

        setup apache + php
        git clone https://github.com/eternita/frontcache.git
        copy `/examples/frontcache-php/*` in to `DocumentRoot`
        update /frontcache-server/FRONTCACHE_HOME/conf/frontcache.properties
        cd frontcache
        `./gradlew build`
        run `/frontcache-server/bin/frontcache`
        open http://localhost:9080/ in your browser

[More about frontcache-php example](https://github.com/eternita/frontcache/tree/master/examples/frontcache-php)

