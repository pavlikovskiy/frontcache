### Request log : (frontcache-requests.log)


        log-timestamp request-id   domain   http-method   is-hystrix-error{success|error}   request-type {toplevel|include|include-async}   is-cacheable{cacheable|direct}   is-cached{dynamic|from-cache|dynamic-soft}     runtime-millis    datalength-bytes   url   client-IP    frontcache-ID    client-type{bot|browser}    user-agent


###  EXAMPLE

        2016-06-03T15:06:35,092-0600 1649b11f-8acf-4718-8e0b-abdcf7356212 coinshome.net GET success toplevel cacheable from-cache 0 50874 "http://myfc.coinshome.net:8080/en/coin_definition-1_Thaler-Silver-Kingdom_of_Prussia_(1701_1918)-c_sK.GJAIx4AAAEvnTTi7NnT.htm" 0:0:0:0:0:0:0:1 front-cache-local-1 browser
        2016-06-03T15:06:35,100-0600 1649b11f-8acf-4718-8e0b-abdcf7356212 coinshome.net GET success include cacheable from-cache 0 1581 "http://myfc.coinshome.net:8080/fc/include-footer.htm?locale=en" 127.0.0.1 front-cache-local-1 browser
        2016-06-03T15:06:35,105-0600 1649b11f-8acf-4718-8e0b-abdcf7356212 coinshome.net GET success include-async cacheable from-cache 0 1411 "http://myfc.coinshome.net:8080/fc/external-ads.htm?locale=" 127.0.0.1 front-cache-local-1 browser
        2016-06-03T15:06:35,558-0600 e67a3f57-07f1-4fdb-91e1-e33313ba4185 coinshome.net GET success toplevel direct dynamic 5 -1 "http://myfc.coinshome.net:8080/follower?eid=c_sK.GJAIx4AAAEvnTTi7NnT&activity=COIN_GROUP_UPDATE&cmd=check" 0:0:0:0:0:0:0:1 front-cache-local-1 browser
        2016-06-03T15:06:35,565-0600 66223049-3269-4de2-8d0e-a467b83a8390 coinshome.net GET success toplevel cacheable dynamic 12 1676 "http://myfc.coinshome.net:8080/fc/include-header.htm?view=desktop&locale=en" 0:0:0:0:0:0:0:1 front-cache-local-1 browser
        2016-06-03T15:06:35,578-0600 cac3e17f-2084-4f18-b4f7-cb7ae6ee8a37 coinshome.net GET success toplevel direct dynamic 2 -1 "http://myfc.coinshome.net:8080/uinfo" 0:0:0:0:0:0:0:1 front-cache-local-1 browser
        2016-06-03T15:06:35,741-0600 55ba8287-cedd-43b6-ae47-357292664cb3 coinshome.net GET success toplevel cacheable dynamic 4 9662 "http://myfc.coinshome.net:8080/favicon.ico" 0:0:0:0:0:0:0:1 front-cache-local-1 bot
