# Running Frontcache on Docker

1. Install and Run [Docker](https://docs.docker.com/engine/installation/) 
1. Go to [FRONTCACHE_INSTALL_DIR/scripts/docker/frontcache-server](https://github.com/eternita/frontcache/tree/master/scripts/docker/frontcache-server)
1. Build an image with `./build.sh`
1. Start container with `./start_fc.sh`
1. Open http://localhost:9080/

### Update it for your site

1. Edit `front-cache.origin-host`  in `FRONTCACHE_INSTALL_DIR/scripts/docker/frontcache-server/dist/FRONTCACHE_HOME/conf/frontcache.properties`
1. Stop container with `./stop_fc.sh`
1. Build an image with `./build.sh`
1. Start container with `./start_fc.sh`
1. Open http://localhost:9080/

# Getting Frontcache's logs into Kibana
### Starting Kibana + Elasticsearch container
1. Go to https://github.com/eternita/frontcache/tree/master/scripts/docker/fc-elk
1. Run `./build.sh` to build docker image
1. Run `./start_elk.sh` to start elasticsearch and kibana container
### Starting Logstash container
1. Go to https://github.com/eternita/frontcache/tree/master/scripts/docker/fc-logstash
1. Run `./build.sh` to build docker image
1. Run `./start_logstash.sh` to start logstash container
### Getting logs from frontcache instances
1. Add all instances with frontcache to your `/.ssh/config`
ex.
```
Host fc1 fc1.mydomain.net
Hostname fc1.mydomain.net
User ubuntu
IdentityFile ~/Documents/keys/fc1_host.pem
```
1. Add all instances into `get_logs.sh`
like `array=("fc2" "fc3" "fc4")` 
1. Run `./get_logs.sh` to download all frontcache logs and import it into Kibana

### View statistic in Kibana
1. Open `http://localhost:5601/`
1. Import Kibana's dashboard https://github.com/eternita/frontcache/blob/master/scripts/docker/fc-elk/conf/kibana.json
![kibana](https://github.com/eternita/frontcache/blob/master/images/kibana.png)
