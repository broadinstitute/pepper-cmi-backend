package org.broadinstitute.ddp.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import redis.clients.jedis.Jedis;

@Slf4j
public class RedisConnectionValidator {


    public static void doTest() {
        String redisAddress = ConfigManager.getInstance().getConfig().getString(ConfigFile.REDIS_SERVER_ADDRESS);
        String host = StringUtils.substringBetween(redisAddress, "//", ":");
        String portString = StringUtils.substringAfterLast(redisAddress, ":");
        log.debug("Will look for redis at " + host + ":" + portString);
        doTest(host, Integer.parseInt(portString));
    }

    public static void doTest(String host, int port) {
        String testHash = "testhash";
        // todo arz switch to jedispool?
        String valToWrite = Math.random() + "";
        try (var jedis = new Jedis(host, port)) {
            jedis.hset(testHash, "hello", valToWrite);

            String valRead = jedis.hget(testHash, "hello");
            if (valToWrite.equals(valRead)) {
                log.debug("Redis ok");
            }
        } catch (RuntimeException e) {
            log.error("There was a problem reading/writing to Redis", e);
        }
    }

    public static void main(String[] args) {
        doTest("localhost", 6379);
    }
}
